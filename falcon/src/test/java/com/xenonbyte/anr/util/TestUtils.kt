package com.xenonbyte.anr.util

import android.os.Looper
import com.xenonbyte.anr.FalconEventListener
import com.xenonbyte.anr.data.MessageSamplingData
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import java.util.Deque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 测试工具类
 *
 * 提供测试中常用的辅助方法和 Mock 工具
 */
object TestUtils {

    /**
     * 等待条件满足或超时
     *
     * @param timeoutMs 超时时间（毫秒）
     * @param condition 等待条件
     * @return 条件是否满足
     */
    fun waitFor(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) return true
            Thread.sleep(10)
        }
        return false
    }

    /**
     * 在新线程中执行代码并等待完成
     *
     * @param action 要执行的代码
     */
    fun runInNewThread(action: () -> Unit) {
        val latch = CountDownLatch(1)
        Thread {
            try {
                action()
            } finally {
                latch.countDown()
            }
        }.start()
        latch.await(5, TimeUnit.SECONDS)
    }

    /**
     * 在新线程中执行代码并获取结果
     *
     * @param action 要执行的代码
     * @return 执行结果
     */
    fun <T> runInNewThreadAndGet(action: () -> T): T {
        val result = AtomicReference<T>()
        val latch = CountDownLatch(1)
        Thread {
            try {
                result.set(action())
            } finally {
                latch.countDown()
            }
        }.start()
        latch.await(5, TimeUnit.SECONDS)
        return result.get()
    }
}

/**
 * Mock Looper 工具类
 *
 * 用于在测试中 Mock Android Looper
 */
class MockLooperHelper {

    private var mockedMainLooper: Looper? = null

    /**
     * Mock 主线程 Looper
     */
    fun mockMainThreadLooper() {
        mockkStatic(Looper::class)

        val mockLooper = mockk<Looper>(relaxed = true)
        val mockThread = Thread.currentThread()

        every { Looper.getMainLooper() } returns mockLooper
        every { mockLooper.thread } returns mockThread

        mockedMainLooper = mockLooper
    }

    /**
     * 清理 Mock
     */
    fun cleanup() {
        mockedMainLooper = null
    }
}

/**
 * 测试用的简单监听器实现
 */
class TestFalconEventListener : FalconEventListener {
    var anrCalled = false
    var slowRunnableCalled = false
    var lastAnrTimestamp = 0L
    var lastSlowRunnableTimestamp = 0L
    var lastAnrStackTrace = ""
    var lastSlowRunnableStackTrace = ""

    override fun onAnr(
        currentTimestamp: Long,
        mainStackTrace: String,
        messageSamplingData: MessageSamplingData?,
        messageSamplingHistory: Deque<MessageSamplingData>,
        hprofData: String
    ) {
        anrCalled = true
        lastAnrTimestamp = currentTimestamp
        lastAnrStackTrace = mainStackTrace
    }

    override fun onSlowRunnable(
        currentTimestamp: Long,
        mainStackTrace: String,
        messageSamplingData: MessageSamplingData,
        hprofData: String
    ) {
        slowRunnableCalled = true
        lastSlowRunnableTimestamp = currentTimestamp
        lastSlowRunnableStackTrace = mainStackTrace
    }

    /**
     * 重置所有状态
     */
    fun reset() {
        anrCalled = false
        slowRunnableCalled = false
        lastAnrTimestamp = 0L
        lastSlowRunnableTimestamp = 0L
        lastAnrStackTrace = ""
        lastSlowRunnableStackTrace = ""
    }
}
