package com.xenonbyte.anr

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Falcon健康监控器单元测试
 *
 * 测试目标：
 * 1. 错误计数和阈值检查
 * 2. 错误重置机制
 * 3. 健康状态转换
 * 4. 错误详情记录
 */
class FalconHealthMonitorTest {

    private lateinit var healthMonitor: FalconHealthMonitor

    @Before
    fun setup() {
        // 创建一个测试用的健康监控器，阈值为10
        healthMonitor = FalconHealthMonitor(
            maxErrorCount = 10,
            errorResetIntervalMs = 1000L // 1秒用于测试
        )
    }

    @Test
    fun `初始状态应该是HEALTHY`() {
        assertEquals(FalconHealthMonitor.HealthStatus.HEALTHY, healthMonitor.getHealthStatus())
        assertTrue(healthMonitor.isHealthy())
    }

    @Test
    fun `记录错误应该增加错误计数`() {
        val initialStats = healthMonitor.getErrorStats()
        assertEquals(0L, initialStats.totalErrors)

        healthMonitor.recordError("Test error 1")
        healthMonitor.recordError("Test error 2")

        val stats = healthMonitor.getErrorStats()
        assertEquals(2L, stats.totalErrors)
    }

    @Test
    fun `错误数达到阈值时应该进入DEGRADED状态`() {
        // 记录10个错误
        repeat(10) {
            val degraded = healthMonitor.recordError("Error $it")
            if (it == 9) {
                assertTrue("第10个错误应该触发DEGRADED状态", degraded)
            }
        }

        assertEquals(FalconHealthMonitor.HealthStatus.DEGRADED, healthMonitor.getHealthStatus())
        assertFalse(healthMonitor.isHealthy())
    }

    @Test
    fun `记录错误详情应该保留最近10个错误`() {
        // 记录15个错误
        repeat(15) {
            healthMonitor.recordError("Error $it")
        }

        val stats = healthMonitor.getErrorStats()
        assertEquals(15L, stats.totalErrors)
        assertEquals(10, stats.recentErrors.size)
        assertEquals("Error 14", stats.recentErrors.last().error)
        assertEquals("Error 5", stats.recentErrors.first().error)
    }

    @Test
    fun `超过重置间隔后应该重置错误计数`() {
        // 记录5个错误
        repeat(5) {
            healthMonitor.recordError("Error $it")
        }

        assertEquals(5L, healthMonitor.getErrorStats().totalErrors)

        // 等待超过重置间隔（1秒）
        Thread.sleep(1100)

        // 记录新错误，应该重置计数
        healthMonitor.recordError("New error")

        val stats = healthMonitor.getErrorStats()
        assertEquals(1L, stats.totalErrors)
        assertEquals(FalconHealthMonitor.HealthStatus.HEALTHY, stats.status)
    }

    @Test
    fun `reset方法应该重置所有状态`() {
        // 记录一些错误
        repeat(5) {
            healthMonitor.recordError("Error $it")
        }

        // 重置
        healthMonitor.reset()

        val stats = healthMonitor.getErrorStats()
        assertEquals(0L, stats.totalErrors)
        assertEquals(0L, stats.lastErrorTime)
        assertEquals(FalconHealthMonitor.HealthStatus.HEALTHY, stats.status)
        assertTrue(stats.recentErrors.isEmpty())
    }

    @Test
    fun `错误记录应该包含正确的时间戳`() {
        val beforeTime = System.currentTimeMillis()
        healthMonitor.recordError("Test error")
        val afterTime = System.currentTimeMillis()

        val stats = healthMonitor.getErrorStats()
        val errorRecord = stats.recentErrors.first()

        assertTrue(
            "错误时间戳应该在记录时间前后范围内",
            errorRecord.timestamp in beforeTime..afterTime
        )
    }

    @Test
    fun `多次reset后状态应该保持HEALTHY`() {
        repeat(15) {
            healthMonitor.recordError("Error $it")
        }

        assertEquals(FalconHealthMonitor.HealthStatus.DEGRADED, healthMonitor.getHealthStatus())

        healthMonitor.reset()
        assertEquals(FalconHealthMonitor.HealthStatus.HEALTHY, healthMonitor.getHealthStatus())

        healthMonitor.reset()
        assertEquals(FalconHealthMonitor.HealthStatus.HEALTHY, healthMonitor.getHealthStatus())
    }

    @Test
    fun `错误计数应该是线程安全的`() {
        val threads = mutableListOf<Thread>()
        val errorsPerThread = 20

        // 创建多个线程同时记录错误
        repeat(10) {
            val thread = Thread {
                repeat(errorsPerThread) { index ->
                    healthMonitor.recordError("Thread $it error $index")
                }
            }
            threads.add(thread)
            thread.start()
        }

        // 等待所有线程完成
        threads.forEach { it.join() }

        val stats = healthMonitor.getErrorStats()
        assertEquals(200L, stats.totalErrors)
        assertEquals(FalconHealthMonitor.HealthStatus.DEGRADED, stats.status)
    }
}
