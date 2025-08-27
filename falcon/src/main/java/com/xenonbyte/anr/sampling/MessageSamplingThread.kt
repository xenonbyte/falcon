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
import java.util.concurrent.locks.LockSupport


/**
 * 消息采样线程
 *
 * @param messageSamplingModel 消息采样数据模型
 * @author xubo
 */
internal class MessageSamplingThread(private val messageSamplingModel: MessageSamplingModel) : Thread(THREAD_NAME),
    ObjectPoolStoreOwner {
    override val store = ObjectPoolStore()

    companion object {
        private const val THREAD_NAME = "com.xenonbyte:MessageSamplingThread"
        private const val MESSAGE_DATA_POOL_MAX_SIZE = 5
        private const val CPU_IDE_TIMEOUT_MILLISECONDS = 10L
    }

    //采样数据模型

    //采样线程是否启动
    private val threadStarted = AtomicBoolean(false)

    //等待处理的消息数据队列
    private val messageDataQueue = ConcurrentLinkedQueue<MessageData>()

    //采样监听
    private var messageSamplingListener: MessageSamplingListener? = null

    //CPU空转超时
    private val cpuIdeTimeout = TimeUnit.MILLISECONDS.toMillis(CPU_IDE_TIMEOUT_MILLISECONDS);

    //消息数据复用池
    private val messageDataPool =
        ObjectPoolProvider.create(this).get(MessageData::class.java, object : ObjectFactory<MessageData> {
            override fun create(vararg args: Any?): MessageData {
                val message = args[0] as String
                val timestamp = args[1] as Long
                return MessageData(message, timestamp);
            }

            override fun reuse(instance: MessageData, vararg args: Any?) {
                val message = args[0] as String
                val timestamp = args[1] as Long
                instance.rest(message, timestamp)
            }

        }, MESSAGE_DATA_POOL_MAX_SIZE)

    override fun run() {
        super.run()
        var lastHandleMessageTime = SystemClock.elapsedRealtime()
        while (true) {
            //处理消息数据
            if (!messageDataQueue.isEmpty()) {
                while (true) {
                    val messageData = messageDataQueue.poll() ?: break
                    //处理采样消息
                    val samplingData = messageSamplingModel.handleMessageData(messageData) {
                        it == this
                    }
                    //采样数据
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
                    //消息数据放入复用池
                    messageDataPool.recycle(messageData)
                }
                lastHandleMessageTime = SystemClock.elapsedRealtime()
                continue
            }

            //挂起策略：
            //当消息队列中无消息时CPU空转一定时长，等待消息（消息间隔短）
            //当CPU空转超时消息队列依旧无消息时挂起线程
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
     * 分发消息给采样线程
     *
     * @param 消息
     */
    fun dispatchMessage(message: String?) {
        if (message == null) {
            return
        }
        //采样线程未启动
        if (!isStarted()) {
            return
        }
        //仅采样主线消息
        if (currentThread() !== Looper.getMainLooper().thread) {
            return
        }
        //添加采样消息
        addMessageData(message, FalconTimestamp.currentTimeMillis())
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
        //输出debug日志
        FalconUtils.log(LogLevel.DEBUG) {
            "MessageSamplingThread dispatchMessage:\ntime=${
                FalconUtils.formatTimestamp(
                    timestamp, "yyyy-MM-dd HH:mm:ss.SSS"
                )
            } message=$message"
        }
        val messageData: MessageData = messageDataPool.obtain(message, timestamp)
        messageDataQueue.offer(messageData)
        LockSupport.unpark(this)
    }

}