package com.xenonbyte.anr

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Looper
import com.github.xenonbyte.ObjectPoolStore
import com.github.xenonbyte.ObjectPoolStoreOwner
import com.xenonbyte.anr.bomb.AnrBattlefield
import com.xenonbyte.anr.bomb.AnrBombExplosionListener
import com.xenonbyte.anr.bomb.AnrBombThread
import com.xenonbyte.anr.data.MessageSamplingData
import com.xenonbyte.anr.data.SamplingStatus
import com.xenonbyte.anr.dump.Dumper
import com.xenonbyte.anr.dump.collectDataToJson
import com.xenonbyte.anr.sampling.MessageSamplingListener
import com.xenonbyte.anr.sampling.MessageSamplingModel
import com.xenonbyte.anr.sampling.MessageSamplingThread
import com.xenonbyte.anr.sampling.MessageStackTraceCapturer
import java.util.Deque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.json.JSONArray

/**
 * Falcon 控制器
 *
 * 负责协调各模块工作：
 * - 消息采样线程
 * - ANR 检测战场
 * - 堆栈采集器
 * - 健康监控器
 *
 * @author xubo
 */
internal class FalconController(
    private val app: Application,
    private val foregroundAnrThreshold: Long,
    private val backgroundAnrThreshold: Long,
    private val slowMessageThreshold: Long,
    private val messageSamplingMaxCacheSize: Int,
    private val samplingRate: Float,
    private val hprofDumpEnabled: Boolean,
    private val dumperMap: Map<FalconEvent, LinkedHashSet<Dumper<*>>>
) : MessageSamplingListener, AnrBombExplosionListener, ObjectPoolStoreOwner {

    companion object {
        private const val STOP_JOIN_TIMEOUT_MS = 1_000L
    }

    override val store = ObjectPoolStore()

    // 事件监听
    private var listener: FalconEventListener? = null

    // 事件处理线程池
    private var threadPool: ThreadPoolExecutor? = null

    // 采样数据模型
    private val messageSamplingModel = MessageSamplingModel(
        this,
        isLowMemoryDevice(app),
        messageSamplingMaxCacheSize
    )

    // 消息采样线程
    private var messageSamplingThread: MessageSamplingThread? = null

    // 消息堆栈采集器
    private var messageStackTraceCapturer: MessageStackTraceCapturer? = null

    // Anr炸弹线程
    private var anrBombThread: AnrBombThread? = null

    // Anr模拟战场
    private var anrBattlefield: AnrBattlefield? = null

    // 健康监控器
    private val healthMonitor = FalconHealthMonitor()

    /**
     * 初始化
     */
    fun initialize() {
        // 预留初始化入口，实时线程只在 start() 时创建
    }

    /**
     * 开启监测
     */
    fun start() {
        ensureRealtimeComponents()
        // 串行处理事件，保证回调顺序和资源占用可控
        threadPool = ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.SECONDS,
            LinkedBlockingQueue(),
            FalconEventThreadFactory()
        )
        // 主线程消息分发给采样线程
        Looper.getMainLooper().setMessageLogging { message ->
            messageSamplingThread?.dispatchMessage(message)
        }
    }

    /**
     * 停止监测
     */
    fun stop() {
        // 主线程消息分发断开
        Looper.getMainLooper().setMessageLogging(null)
        // 取消雷区和堆栈采集任务
        anrBattlefield?.resetBattle()
        messageStackTraceCapturer?.cancelAllCaptures()

        // 停止采样线程
        val samplingThread = messageSamplingThread
        samplingThread?.stopSampling()
        joinThreadSafely(samplingThread)
        messageSamplingThread = null

        // 停止线程池
        threadPool?.shutdownNow()
        threadPool = null
        // 清空采样数据模型数据
        messageSamplingModel.clear()
        // 清空缓存池
        store.clear()
        // 停止可重建的 HandlerThread
        val stackTraceCapturer = messageStackTraceCapturer
        if (stackTraceCapturer?.isAlive == true) {
            stackTraceCapturer.quitSafely()
        }
        joinThreadSafely(stackTraceCapturer)
        val bombThread = anrBombThread
        if (bombThread?.isAlive == true) {
            bombThread.quitSafely()
        }
        joinThreadSafely(bombThread)
        messageStackTraceCapturer = null
        anrBattlefield = null
        anrBombThread = null
    }

    override fun onSampling(data: MessageSamplingData) {
        // 检查健康状态，如果降级则跳过部分处理
        if (!healthMonitor.isHealthy()) {
            // 仅记录日志，不执行ANR检测，减少对应用的影响
            FalconUtils.log(LogLevel.WARN) {
                "Falcon is in DEGRADED mode, skipping ANR detection for message: ${data.getMessage()}"
            }
            return
        }

        try {
            if (data.getStatus() == SamplingStatus.START) { // 采样起始消息
                // 调度安排Anr游戏
                anrBattlefield?.deployAnrBattle(data.getMessage()) {
                    it == messageSamplingThread
                }
                // 调度安排堆栈采集任务
                messageStackTraceCapturer?.scheduleCapture {
                    it == messageSamplingThread
                }
            } else if (data.getStatus() == SamplingStatus.END) { // 采样结束消息
                // 取消堆栈采集任务
                messageStackTraceCapturer?.cancelCapture {
                    it == messageSamplingThread
                }
                if (data.getDuration() >= slowMessageThreshold) {
                    val slowRunnableData = data.snapshot()
                    // 慢任务回调处理
                    threadPool?.execute {
                        handleSlowRunnable(slowRunnableData)
                    }
                }
            }
        } catch (e: IllegalStateException) {
            handleIllegalState("onSampling", e)
        } catch (e: SecurityException) {
            handleSecurityException("onSampling", e)
        } catch (e: Exception) {
            handleUnexpectedException("onSampling", e)
        }
    }

    /**
     * 处理慢任务回调
     */
    private fun handleSlowRunnable(data: MessageSamplingData) {
        try {
            FalconUtils.log(LogLevel.WARN) {
                val startTime = FalconUtils.formatTimestamp(data.getStartTimestamp(), "yyyy-MM-dd HH:mm:ss.SSS")
                val endTime = FalconUtils.formatTimestamp(data.getEndTimestamp(), "yyyy-MM-dd HH:mm:ss.SSS")
                val duration = data.getDuration()
                "-----Slow Message-----\nstartTime=$startTime endTime=$endTime duration=${duration}ms message=${data.getMessage()}\n${data.getStackTrace()}"
            }
            val hprofData = dumpHprofData(app, dumperMap[FalconEvent.SLOW_RUNNABLE_EVENT])
            listener?.onSlowRunnable(FalconTimestamp.currentTimeMillis(), data.getStackTrace(), data, hprofData)
        } catch (e: IllegalStateException) {
            handleIllegalState("handleSlowRunnable", e)
        } catch (e: Exception) {
            handleUnexpectedException("handleSlowRunnable", e)
        }
    }

    override fun onAnrBombExplosion() {
        try {
            val stackTrace = FalconUtils.captureStackTrace(Looper.getMainLooper().thread)
            val currentSamplingData = messageSamplingModel.getCurrentSamplingData()
            currentSamplingData?.takeIf { it.getStackTrace().isEmpty() }?.setMainStackTrace(stackTrace)
            val currentSamplingDataSnapshot = currentSamplingData?.snapshot()
            val messageSamplingDataDeque = messageSamplingModel.getSamplingDataDequeSnapshot()

            // Anr回调处理
            threadPool?.execute {
                handleAnrCallback(stackTrace, currentSamplingDataSnapshot, messageSamplingDataDeque)
            }
        } catch (e: IllegalStateException) {
            handleIllegalState("onAnrBombExplosion", e)
        } catch (e: SecurityException) {
            handleSecurityException("onAnrBombExplosion", e)
        } catch (e: Exception) {
            handleUnexpectedException("onAnrBombExplosion", e)
        }
    }

    /**
     * 处理 ANR 回调
     */
    private fun handleAnrCallback(
        stackTrace: String,
        currentSamplingData: MessageSamplingData?,
        messageSamplingDataDeque: Deque<MessageSamplingData>
    ) {
        try {
            FalconUtils.log(LogLevel.ERROR) {
                val startTime = FalconUtils.formatTimestamp(
                    currentSamplingData?.getStartTimestamp() ?: 0,
                    "yyyy-MM-dd HH:mm:ss.SSS"
                )
                "-----Anr Event-----\nstartTime=$startTime message=${currentSamplingData?.getMessage()}\n$stackTrace"
            }
            val hprofData = dumpHprofData(app, dumperMap[FalconEvent.ANR_EVENT])
            listener?.onAnr(
                FalconTimestamp.currentTimeMillis(),
                stackTrace,
                currentSamplingData,
                messageSamplingDataDeque,
                hprofData
            )
        } catch (e: IllegalStateException) {
            handleIllegalState("handleAnrCallback", e)
        } catch (e: Exception) {
            handleUnexpectedException("handleAnrCallback", e)
        }
    }

    /**
     * 处理非法状态异常
     */
    private fun handleIllegalState(context: String, e: IllegalStateException) {
        FalconUtils.log(LogLevel.ERROR) {
            "[$context] Illegal state: ${e.message}\n${e.stackTraceToString()}"
        }
        healthMonitor.recordError("$context: ${e.message}")
    }

    /**
     * 处理安全异常
     */
    private fun handleSecurityException(context: String, e: SecurityException) {
        FalconUtils.log(LogLevel.WARN) {
            "[$context] Security exception: ${e.message}"
        }
        // 安全异常通常可以忽略，不影响监控功能
    }

    /**
     * 处理未预期的异常
     */
    private fun handleUnexpectedException(context: String, e: Exception) {
        FalconUtils.log(LogLevel.ERROR) {
            "[$context] Unexpected error: ${e.message}\n${e.stackTraceToString()}"
        }
        healthMonitor.recordError("$context: unexpected - ${e.message}")
    }

    /**
     * 设置事件监听
     *
     * @param listener 事件监听
     */
    fun setFalconListener(listener: FalconEventListener?) {
        this.listener = listener
    }

    /**
     * 获取健康监控器状态（用于调试和监控）
     *
     * @return 健康状态信息
     */
    fun getHealthStatus(): String {
        val stats = healthMonitor.getErrorStats()
        return """
            Falcon Health Status: ${stats.status}
            Total Errors: ${stats.totalErrors}
            Last Error Time: ${stats.lastErrorTime}
            Recent Errors: ${stats.recentErrors.joinToString("\n") { "[${it.timestamp}] ${it.error}" }}
        """.trimIndent()
    }

    /**
     * 获取对外暴露的健康状态枚举。
     */
    fun getHealthState(): FalconHealthState {
        return when (healthMonitor.getHealthStatus()) {
            FalconHealthMonitor.HealthStatus.HEALTHY -> FalconHealthState.HEALTHY
            FalconHealthMonitor.HealthStatus.DEGRADED -> FalconHealthState.DEGRADED
        }
    }

    /**
     * 重置健康监控器（用于从错误中恢复）
     */
    fun resetHealthMonitor() {
        healthMonitor.reset()
    }

    /**
     * 转储分析数据
     *
     * @param app 应用Application
     * @param dumpers 数据转储器集合
     */
    private fun dumpHprofData(app: Application, dumpers: LinkedHashSet<Dumper<*>>?): String {
        if (!hprofDumpEnabled) {
            return ""
        }
        val jsonArray = JSONArray()
        dumpers?.let {
            it.forEach { dumper ->
                val dumpDataJson = dumper.collectDataToJson(app)
                jsonArray.put(dumpDataJson)
            }
        }
        return jsonArray.toString()
    }

    /**
     * 是否低内存设备
     *
     * @param context 上下文
     */
    private fun isLowMemoryDevice(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.isLowRamDevice
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 确保实时检测组件处于可用状态
     */
    private fun ensureRealtimeComponents() {
        if (messageSamplingThread?.isAlive != true) {
            messageSamplingThread = MessageSamplingThread(
                messageSamplingModel,
                samplingRate
            ).also {
                it.setSamplingListener(this)
                it.startSampling()
            }
        }
        if (messageStackTraceCapturer?.isAlive != true) {
            messageStackTraceCapturer = MessageStackTraceCapturer(
                messageSamplingModel,
                slowMessageThreshold
            ).also { it.startCapturing() }
        }
        if (anrBombThread?.isAlive != true) {
            val bombThread = AnrBombThread().also { it.startBombSpace() }
            anrBombThread = bombThread
            anrBattlefield = AnrBattlefield(
                foregroundAnrThreshold,
                backgroundAnrThreshold,
                bombThread
            ).also { it.setBombExplosionListener(this) }
        } else if (anrBattlefield == null) {
            anrBattlefield = anrBombThread?.let { bombThread ->
                AnrBattlefield(
                    foregroundAnrThreshold,
                    backgroundAnrThreshold,
                    bombThread
                ).also { it.setBombExplosionListener(this) }
            }
        }
    }

    /**
     * 等待线程退出，避免 stop 返回后旧线程继续处理消息。
     */
    private fun joinThreadSafely(thread: Thread?) {
        if (thread == null || !thread.isAlive) {
            return
        }
        try {
            thread.join(STOP_JOIN_TIMEOUT_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            handleUnexpectedException("joinThreadSafely", e)
        }
    }

    /**
     * 线程创建工厂
     *
     * @author xubo
     */
    class FalconEventThreadFactory : ThreadFactory {
        private val threadName = "com.xenonbyte:FalconEventThread"

        override fun newThread(r: Runnable?): Thread {
            return Thread(r, threadName)
        }
    }
}
