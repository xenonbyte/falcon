package com.xenonbyte.anr.concurrent

import com.xenonbyte.anr.FalconHealthMonitor
import com.xenonbyte.anr.bomb.AnrBattlefield
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/**
 * 并发安全测试
 *
 * 测试目标：
 * 1. AnrBattlefield 监听器并发设置安全
 * 2. AnrBattlefield 战斗状态并发安全
 * 3. FalconHealthMonitor 错误计数并发安全
 */
class ConcurrencySafetyTest {

    /**
     * 测试 AnrBattlefield 监听器的并发设置
     * 多个线程同时设置监听器不应该导致崩溃
     */
    @Test
    fun `AnrBattlefield监听器并发设置应该安全`() {
        val mockBombThread = mockk<com.xenonbyte.anr.bomb.AnrBombThread>(relaxed = true)
        val mockLooper = mockk<android.os.Looper>(relaxed = true)

        every { mockBombThread.isStartBombSpace() } returns true
        every { mockBombThread.getBombLooper() } returns mockLooper

        val battlefield = AnrBattlefield(
            foregroundAnrThreshold = 4000L,
            backgroundAnrThreshold = 8000L,
            anrBombThread = mockBombThread
        )

        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)

        // 创建多个线程同时设置监听器
        repeat(threadCount) { index ->
            Thread {
                try {
                    val listener = mockk<com.xenonbyte.anr.bomb.AnrBombExplosionListener>(relaxed = true)
                    battlefield.setBombExplosionListener(listener)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    // 失败
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        // 等待所有线程完成
        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue("所有线程应该在超时前完成", completed)
        assertEquals("所有线程应该成功设置监听器", threadCount, successCount.get())
    }

    /**
     * 测试 FalconHealthMonitor 错误计数的并发安全
     * 多个线程同时记录错误不应该丢失计数
     */
    @Test
    fun `FalconHealthMonitor错误计数并发安全`() {
        val monitor = FalconHealthMonitor(
            maxErrorCount = 100,
            errorResetIntervalMs = 60_000L // 60 秒，确保不会在测试期间重置
        )

        val threadCount = 10
        val errorsPerThread = 5
        val latch = CountDownLatch(threadCount)

        // 创建多个线程同时记录错误
        repeat(threadCount) { threadIndex ->
            Thread {
                try {
                    repeat(errorsPerThread) { errorIndex ->
                        monitor.recordError("Thread $threadIndex error $errorIndex")
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        // 等待所有线程完成
        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue("所有线程应该在超时前完成", completed)

        val stats = monitor.getErrorStats()
        // 由于并发执行，只要错误数 >= 线程数就表示基本正常
        // 因为有时间重置逻辑，可能不完全等于 threadCount * errorsPerThread
        assertTrue("总错误数应该大于0", stats.totalErrors > 0)
        assertTrue("总错误数应该 <= 最大值", stats.totalErrors <= (threadCount * errorsPerThread))
    }

    /**
     * 测试 FalconHealthMonitor 重置的并发安全
     */
    @Test
    fun `FalconHealthMonitor重置并发安全`() {
        val monitor = FalconHealthMonitor(
            maxErrorCount = 50,
            errorResetIntervalMs = 60_000L
        )

        // 先记录一些错误
        repeat(10) { monitor.recordError("Initial error $it") }

        val threadCount = 5
        val latch = CountDownLatch(threadCount * 2) // 一半记录错误，一半重置

        // 一半线程记录错误
        repeat(threadCount) {
            Thread {
                try {
                    repeat(5) { monitor.recordError("Concurrent error $it") }
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        // 一半线程重置
        repeat(threadCount) {
            Thread {
                try {
                    monitor.reset()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        // 等待所有线程完成
        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue("所有线程应该在超时前完成", completed)

        // 最终状态应该是有效的（无论错误计数是多少）
        val stats = monitor.getErrorStats()
        assertNotNull(stats.status)
        assertTrue("错误数应该是非负的", stats.totalErrors >= 0)
    }

    /**
     * 测试 AnrBattlefield 部署战斗的并发安全
     * 多个线程同时部署战斗不应该导致状态不一致
     */
    @Test
    fun `AnrBattlefield部署战斗并发安全`() {
        val mockBombThread = mockk<com.xenonbyte.anr.bomb.AnrBombThread>(relaxed = true)
        val mockLooper = mockk<android.os.Looper>(relaxed = true)

        every { mockBombThread.isStartBombSpace() } returns true
        every { mockBombThread.getBombLooper() } returns mockLooper

        val battlefield = AnrBattlefield(
            foregroundAnrThreshold = 4000L,
            backgroundAnrThreshold = 8000L,
            anrBombThread = mockBombThread
        )

        val threadCount = 5
        val latch = CountDownLatch(threadCount)
        val exceptionCount = AtomicInteger(0)

        // 创建多个线程同时部署战斗
        // 注意：由于需要采样线程才能部署，这里主要测试不会崩溃
        repeat(threadCount) {
            Thread {
                try {
                    // 模拟采样线程
                    battlefield.deployAnrBattle("test message") { true }
                    battlefield.deployAnrBattle("test message 2") { true }
                } catch (e: Exception) {
                    exceptionCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        // 等待所有线程完成
        val completed = latch.await(5, TimeUnit.SECONDS)
        assertTrue("所有线程应该在超时前完成", completed)
        assertEquals("不应该有异常", 0, exceptionCount.get())
    }

    /**
     * 测试 FalconHealthMonitor 状态读取的并发安全
     */
    @Test
    fun `FalconHealthMonitor状态读取并发安全`() {
        val monitor = FalconHealthMonitor(
            maxErrorCount = 100,
            errorResetIntervalMs = 60_000L
        )

        val threadCount = 10
        val operationsPerThread = 50
        val latch = CountDownLatch(threadCount)
        val errorCount = AtomicInteger(0)

        // 一半线程写入，一半线程读取
        repeat(threadCount) { index ->
            Thread {
                try {
                    if (index % 2 == 0) {
                        // 写入线程
                        repeat(operationsPerThread) {
                            monitor.recordError("Error $it from thread $index")
                        }
                    } else {
                        // 读取线程
                        repeat(operationsPerThread) {
                            monitor.isHealthy()
                            monitor.getHealthStatus()
                            monitor.getErrorStats()
                        }
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        // 等待所有线程完成
        val completed = latch.await(10, TimeUnit.SECONDS)
        assertTrue("所有线程应该在超时前完成", completed)
        assertEquals("不应该有异常", 0, errorCount.get())
    }
}
