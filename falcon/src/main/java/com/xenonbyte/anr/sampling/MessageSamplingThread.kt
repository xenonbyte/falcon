package com.xenonbyte.anr.sampling

import android.os.Looper
import android.os.SystemClock
import com.github.xenonbyte.ObjectFactory
import com.github.xenonbyte.ObjectPoolProvider
import com.github.xenonbyte.ObjectPoolStore
import com.github.xenonbyte.ObjectPoolStoreOwner
import com.xenonbyte.anr.FalconTimestamp
import com.xenonbyte.anr.FalconUtils
import com.xenonbyte.anr.LogLevel
import com.xenonbyte.anr.data.MessageData
import com.xenonbyte.anr.data.SamplingStatus
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import kotlin.random.Random

/**
 * 消息采样线程
 *
 * 负责接收主线程消息并传递给 [MessageSamplingModel] 处理。
 *
 * ## 线程挂起策略
 *
 * 为了减少 CPU 开销，采样线程采用智能挂起策略：
 *
 * 1. **CPU 空转期** (前 10ms): 使用 `Thread.yield()` 让出 CPU 但保持活跃
 *    - 消息间隔短时能快速响应
 *    - 避免频繁 park/unpark 开销
 *
 * 2. **深度休眠期** (10ms 后): 使用 `LockSupport.park()` 挂起线程
 *    - 长时间无消息时真正休眠
 *    - 等待新消息唤醒
 *
 * ## 采样率
 *
 * 通过 [samplingRate] 参数控制消息采样比例：
 * - 1.0f (100%): 采样所有消息
 * - 0.5f (50%): 采样一半消息
 * - 0.1f (10%): 采样 10% 消息
 *
 * ## 对象池
 *
 * 使用对象池复用 [MessageData] 实例，减少 GC 压力：
 * - 最大池大小: 5
 * - 超出池大小的对象会被 GC 回收
 *
 * @param messageSamplingModel 消息采样数据模型
 * @param samplingRate 消息采样率（0.0f ~ 1.0f）
 * @author xubo
 */
internal class MessageSamplingThread(
    private val messageSamplingModel: MessageSamplingModel,
    private val samplingRate: Float = 1.0f,
    private val nextRandomFloat: () -> Float = { Random.nextFloat() }
) : Thread(THREAD_NAME), ObjectPoolStoreOwner {

    override val store = ObjectPoolStore()

    companion object {
        private const val THREAD_NAME = "com.xenonbyte:MessageSamplingThread"
        private const val MESSAGE_DATA_POOL_MAX_SIZE = 5
        private const val CPU_IDE_TIMEOUT_MILLISECONDS = 10L

        // 防止死循环的最大连续工作次数
        private const val MAX_CONTINUOUS_WORK_COUNT = 1000
        private const val MESSAGE_START_HEAD = ">>>>> Dispatching to "
        private const val MESSAGE_END_HEAD = "<<<<< Finished to "
    }

    // 采样线程是否启动
    private val threadStarted = AtomicBoolean(false)

    // 采样线程运行状态
    private val running = AtomicBoolean(true)

    // 等待处理的消息数据队列
    private val messageDataQueue = ConcurrentLinkedQueue<MessageData>()

    // 采样监听
    private var messageSamplingListener: MessageSamplingListener? = null

    // CPU空转超时
    private val cpuIdeTimeout = TimeUnit.MILLISECONDS.toMillis(CPU_IDE_TIMEOUT_MILLISECONDS)

    // 跳过的消息计数（用于日志）
    private val skippedCount = AtomicLong(0)

    // 当前主线程消息的采样决策，保证 start / end 成对处理
    private var hasPendingDispatch = false
    private var pendingDispatchSampled = false

    // 消息数据复用池
    private val messageDataPool =
        ObjectPoolProvider.create(this).get(
            MessageData::class.java,
            object : ObjectFactory<MessageData> {
                override fun create(vararg args: Any?): MessageData {
                    val message = args[0] as String
                    val timestamp = args[1] as Long
                    return MessageData(message, timestamp)
                }

                override fun reuse(instance: MessageData, vararg args: Any?) {
                    val message = args[0] as String
                    val timestamp = args[1] as Long
                    instance.rest(message, timestamp)
                }
            },
            MESSAGE_DATA_POOL_MAX_SIZE
        )

    override fun run() {
        super.run()
        var lastHandleMessageTime = SystemClock.elapsedRealtime()
        var continuousWorkCount = 0 // 连续工作计数器
        while (running.get()) {
            // 处理消息数据
            if (!messageDataQueue.isEmpty()) {
                var messageData = messageDataQueue.poll()
                while (running.get() && messageData != null) {
                    // 处理采样消息
                    val samplingData = messageSamplingModel.handleMessageData(messageData) {
                        it == this
                    }
                    // 采样数据
                    samplingData?.let {
                        FalconUtils.log(LogLevel.DEBUG) {
                            if (it.getStatus() == SamplingStatus.START) {
                                "MessageSamplingThread sampling start message:\nindex=${it.getIndex()} message=${it.getMessage()}"
                            } else {
                                "MessageSamplingThread sampling end message:\nindex=${it.getIndex()} duration: ${it.getDuration()} message=${it.getMessage()}"
                            }
                        }
                        messageSamplingListener?.onSampling(it)
                    }
                    // 消息数据放入复用池
                    messageDataPool.recycle(messageData)

                    // 防止长时间连续工作导致CPU空转
                    if (++continuousWorkCount >= MAX_CONTINUOUS_WORK_COUNT) {
                        yield()
                        continuousWorkCount = 0
                    }
                    messageData = messageDataQueue.poll()
                }
                lastHandleMessageTime = SystemClock.elapsedRealtime()
                continuousWorkCount = 0 // 重置计数器
                continue
            }

            // 挂起策略：
            // 当消息队列中无消息时CPU空转一定时长，等待消息（消息间隔短）
            // 当CPU空转超时消息队列依旧无消息时挂起线程
            val cpuIdeDuration = SystemClock.elapsedRealtime() - lastHandleMessageTime
            if (cpuIdeDuration >= cpuIdeTimeout) {
                LockSupport.park()
            } else {
                yield()
            }
        }
    }

    /**
     * 开启消息采样
     */
    fun startSampling() {
        if (threadStarted.compareAndSet(false, true)) {
            start()
        }
    }

    /**
     * 停止消息采样线程
     */
    fun stopSampling() {
        running.set(false)
        messageDataQueue.clear()
        hasPendingDispatch = false
        pendingDispatchSampled = false
        LockSupport.unpark(this)
    }

    /**
     * 分发消息给采样线程
     *
     * 根据采样率决定是否处理该消息：
     * - 如果采样率为 1.0f (100%)，则处理所有消息
     * - 如果采样率 < 1.0f，则根据概率决定是否跳过
     *
     * @param message 消息
     */
    fun dispatchMessage(message: String?) {
        try {
            if (message == null) {
                return
            }
            // 采样线程未启动
            if (!isStarted()) {
                return
            }
            // 仅采样主线消息
            if (currentThread() !== Looper.getMainLooper().thread) {
                return
            }

            if (!shouldSampleMessage(message)) {
                return
            }

            // 添加采样消息
            addMessageData(message, FalconTimestamp.currentTimeMillis())
        } catch (e: SecurityException) {
            FalconUtils.log(LogLevel.WARN) {
                "Security exception in dispatchMessage: ${e.message}"
            }
        } catch (e: Exception) {
            FalconUtils.log(LogLevel.ERROR) {
                "Error dispatching message to sampling thread: ${e.message}\n${e.stackTraceToString()}"
            }
        }
    }

    /**
     * 设置采样监听
     *
     * @param listener 采样监听
     */
    fun setSamplingListener(listener: MessageSamplingListener) {
        messageSamplingListener = listener
    }

    /**
     * 采样线程是否启动
     */
    private fun isStarted(): Boolean {
        return threadStarted.get() && isAlive
    }

    /**
     * 添加采样消息
     *
     * @param message 消息
     * @param timestamp 时间戳
     */
    private fun addMessageData(message: String, timestamp: Long) {
        // 输出debug日志
        FalconUtils.log(LogLevel.DEBUG) {
            "MessageSamplingThread dispatchMessage:\ntime=${
                FalconUtils.formatTimestamp(
                    timestamp,
                    "yyyy-MM-dd HH:mm:ss.SSS"
                )
            } message=$message"
        }
        val messageData: MessageData = messageDataPool.obtain(message, timestamp)
        messageDataQueue.offer(messageData)
        LockSupport.unpark(this)
    }

    /**
     * 对单次主线程 dispatch 做成对采样，避免 start / end 被随机拆散
     */
    private fun shouldSampleMessage(message: String): Boolean {
        return when {
            message.startsWith(MESSAGE_START_HEAD) -> {
                val sampled = samplingRate >= 1.0f || nextRandomFloat() < samplingRate
                hasPendingDispatch = true
                pendingDispatchSampled = sampled
                if (!sampled) {
                    logSkippedDispatch()
                }
                sampled
            }

            message.startsWith(MESSAGE_END_HEAD) -> {
                val shouldSample = hasPendingDispatch && pendingDispatchSampled
                hasPendingDispatch = false
                pendingDispatchSampled = false
                shouldSample
            }

            else -> false
        }
    }

    private fun logSkippedDispatch() {
        val skipped = skippedCount.incrementAndGet()
        if (skipped % 100 == 0L) {
            FalconUtils.log(LogLevel.DEBUG) {
                "MessageSamplingThread: skipped $skipped messages (sampling rate: ${(samplingRate * 100).toInt()}%)"
            }
        }
    }
}
