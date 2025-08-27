package com.xenonbyte.anr.sampling

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.xenonbyte.anr.FalconUtils
import com.xenonbyte.anr.LogLevel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 消息堆栈采集器（消息处理超时捕获主线程堆栈）
 *
 * 触发条件：消息执行时长 > (慢任务阈值 * 捕获因子)
 *
 * @param messageSamplingModel 消息采样数据模型
 * @param slowRunnableThreshold 慢任务触发阈值
 * @author xubo
 */
internal class MessageStackTraceCapturer(
    private val messageSamplingModel: MessageSamplingModel,
    private val slowRunnableThreshold: Long
) :
    HandlerThread(THREAD_NAME) {

    companion object {
        //线程名
        private const val THREAD_NAME = "com.xenonbyte:MessageStackTraceCapturer"

        //采集因子
        private const val CAPTURE_FACTOR = 0.8
    }

    //堆栈采集线程是否启动
    private val threadStarted = AtomicBoolean(false)

    //堆栈采集阈值
    private val captureThreshold = (slowRunnableThreshold * CAPTURE_FACTOR).toLong()

    //堆栈采集任务
    private val captureTask = Runnable {
        val stackTrace = FalconUtils.stackTraceToString(Looper.getMainLooper().thread.stackTrace)
        val messageSamplingData = messageSamplingModel.getCurrentSamplingData()
        messageSamplingData?.apply {
            setMainStackTrace(stackTrace)
            FalconUtils.log(LogLevel.DEBUG) {
                "MessageStackTraceDumpThread dump mainThreadStacktrace:\nindex=${getIndex()} message=${getMessage()}"
            }
        }
    }

    //堆栈采集Handler
    private var captureHandler: Handler? = null

    /**
     * 开启堆栈采集线程
     */
    fun startCapturing() {
        if (threadStarted.compareAndSet(false, true)) {
            start()
        }
    }

    /**
     * 调度堆栈采集任务
     *
     * 此方法应由消息采样线程 [MessageSamplingThread] 调用。
     * 仅当消息执行时间超过 ([slowRunnableThreshold] * [CAPTURE_FACTOR]) 时，才会采集堆栈。
     *
     * @param isSamplingThread 是否是消息采样线程
     */
    fun scheduleCapture(isSamplingThread: (currentThread: Thread) -> Boolean) {
        //非消息采样线程不处理
        if (!isSamplingThread.invoke(currentThread())) {
            return
        }
        //堆栈抓取线程未启动
        if (!isStarted()) {
            return
        }
        captureHandler ?: run {
            //堆栈采集线程未创建Looper
            if (looper == null) return
            captureHandler = Handler(looper)
        }
        //尝试堆栈采集
        //如果该消息是慢任务，延迟抓取并不会丢失慢任务堆栈，它发生在慢任务结束之前
        captureHandler?.removeCallbacks(captureTask)
        captureHandler?.postDelayed(captureTask, captureThreshold)
    }

    /**
     * 取消堆栈采集任务
     *
     * 此方法应由消息采样线程 [MessageSamplingThread] 调用。
     *
     * @param isSamplingThread 是否是消息采样线程
     */
    fun cancelCapture(isSamplingThread: (currentThread: Thread) -> Boolean) {
        //非消息采样线程不处理
        if (!isSamplingThread.invoke(currentThread())) {
            return
        }
        //堆栈采集线程未启动
        if (!isStarted() || !isAlive) {
            return
        }
        //取消堆栈采集任务
        captureHandler?.removeCallbacks(captureTask)
    }

    /**
     * 堆栈抓取线程是否启动
     */
    private fun isStarted(): Boolean {
        return threadStarted.get() && isAlive
    }

}