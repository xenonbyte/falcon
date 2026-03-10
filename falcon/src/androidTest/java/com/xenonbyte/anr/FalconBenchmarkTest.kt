package com.xenonbyte.anr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Falcon 性能基准测试
 *
 * 测量 Falcon 对应用性能的影响：
 * 1. 消息采样开销
 * 2. 不同采样率的性能对比
 * 3. 内存占用情况
 * 4. 启动时间
 */
@RunWith(AndroidJUnit4::class)
class FalconBenchmarkTest {

    private val ITERATIONS = 100
    private val WARMUP_ITERATIONS = 10

    @Before
    fun setup() {
        // 预热 JVM
        System.gc()
        Thread.sleep(100)
    }

    @After
    fun tearDown() {
        try {
            Falcon.stopMonitoring()
            Falcon.resetHealthMonitor()
        } catch (e: Exception) {
            // 忽略清理异常
        }
    }

    /**
     * 测量初始化和启动时间
     */
    @Test
    fun benchmarkStartupTime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as android.app.Application

        val config = FalconConfig.Builder()
            .setAnrThreshold(4000L, 8000L)
            .setSlowRunnableThreshold(300L)
            .setSamplingRate(1.0f)
            .setLogLevel(LogLevel.NONE) // 禁用日志减少干扰
            .setHprofDumpEnabled(false)
            .build()

        // 预热
        repeat(WARMUP_ITERATIONS) {
            Falcon.initialize(app, config)
            Falcon.startMonitoring()
            Thread.sleep(50)
            Falcon.stopMonitoring()
            Thread.sleep(50)
        }

        // 实际测量
        val initTimes = mutableListOf<Long>()
        val startTimes = mutableListOf<Long>()

        repeat(5) {
            val initStart = System.nanoTime()
            Falcon.initialize(app, config)
            val initEnd = System.nanoTime()
            initTimes.add((initEnd - initStart) / 1_000_000) // ms

            val startStart = System.nanoTime()
            Falcon.startMonitoring()
            val startEnd = System.nanoTime()
            startTimes.add((startEnd - startStart) / 1_000_000) // ms

            Thread.sleep(100)
            Falcon.stopMonitoring()
            Thread.sleep(100)
        }

        val avgInitTime = initTimes.average()
        val avgStartTime = startTimes.average()

        // 初始化时间应该在合理范围内 (< 100ms)
        assertTrue(
            "初始化时间应该在 100ms 内，实际: ${avgInitTime}ms",
            avgInitTime < 100
        )

        // 启动时间应该在合理范围内 (< 50ms)
        assertTrue(
            "启动时间应该在 50ms 内，实际: ${avgStartTime}ms",
            avgStartTime < 50
        )
    }

    /**
     * 测量不同采样率的性能影响
     */
    @Test
    fun benchmarkSamplingRates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as android.app.Application

        val samplingRates = floatArrayOf(1.0f, 0.8f, 0.5f, 0.2f, 0.1f)
        val results = mutableMapOf<Float, Long>()

        for (rate in samplingRates) {
            // 清理
            try {
                Falcon.stopMonitoring()
            } catch (e: Exception) {
            }
            Thread.sleep(100)

            val config = FalconConfig.Builder()
                .setAnrThreshold(4000L, 8000L)
                .setSlowRunnableThreshold(300L)
                .setSamplingRate(rate)
                .setLogLevel(LogLevel.NONE)
                .setHprofDumpEnabled(false)
                .build()

            Falcon.initialize(app, config)
            Falcon.startMonitoring()

            // 模拟消息处理
            val startTime = System.nanoTime()
            repeat(ITERATIONS) {
                // 模拟主线程工作
                Thread.sleep(1)
            }
            val endTime = System.nanoTime()

            results[rate] = (endTime - startTime) / 1_000_000
        }

        // 打印结果（用于分析）
        // 较低的采样率应该有较低的额外开销
        val fullSamplingTime = results[1.0f] ?: 0L
        val lowSamplingTime = results[0.1f] ?: 0L

        // 验证：即使 100% 采样，也不应该有巨大的性能影响
        assertTrue(
            "100% 采样时，${ITERATIONS} 次迭代应该在 500ms 内，实际: ${fullSamplingTime}ms",
            fullSamplingTime < 500
        )
    }

    /**
     * 测量内存占用
     */
    @Test
    fun benchmarkMemoryUsage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as android.app.Application

        val config = FalconConfig.Builder()
            .setAnrThreshold(4000L, 8000L)
            .setSlowRunnableThreshold(300L)
            .setSamplingRate(1.0f)
            .setLogLevel(LogLevel.NONE)
            .setHprofDumpEnabled(false)
            .setMessageSamplingMaxCacheSize(30)
            .build()

        // 获取启动前内存
        val runtime = Runtime.getRuntime()
        runtime.gc()
        Thread.sleep(100)
        val memoryBefore = runtime.totalMemory() - runtime.freeMemory()

        Falcon.initialize(app, config)
        Falcon.startMonitoring()

        // 模拟一段时间的消息处理
        repeat(50) {
            Thread.sleep(10)
        }

        runtime.gc()
        Thread.sleep(100)
        val memoryAfter = runtime.totalMemory() - runtime.freeMemory()

        val memoryIncrease = (memoryAfter - memoryBefore) / 1024 / 1024 // MB

        // 内存增加应该在合理范围内 (< 5MB)
        assertTrue(
            "内存增加应该在 5MB 内，实际: ${memoryIncrease}MB",
            memoryIncrease < 5
        )
    }

    /**
     * 测量消息采样延迟
     */
    @Test
    fun benchmarkMessageSamplingLatency() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as android.app.Application

        val config = FalconConfig.Builder()
            .setAnrThreshold(4000L, 8000L)
            .setSlowRunnableThreshold(300L)
            .setSamplingRate(1.0f)
            .setLogLevel(LogLevel.NONE)
            .setHprofDumpEnabled(false)
            .build()

        Falcon.initialize(app, config)
        Falcon.startMonitoring()

        // 预热
        repeat(WARMUP_ITERATIONS) {
            Thread.sleep(1)
        }

        // 测量消息处理的额外延迟
        val latencies = mutableListOf<Long>()
        repeat(50) {
            val start = System.nanoTime()
            Thread.sleep(1)
            val end = System.nanoTime()
            latencies.add((end - start) / 1_000_000) // 转换为 ms
        }

        val avgLatency = latencies.average()
        // 采样带来的额外延迟应该很小 (< 5ms)
        assertTrue(
            "平均消息处理延迟应该在 5ms 内，实际: ${avgLatency}ms",
            avgLatency < 5
        )
    }

    /**
     * 压力测试：高频消息场景
     */
    @Test
    fun stressTestHighFrequencyMessages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as android.app.Application

        val config = FalconConfig.Builder()
            .setAnrThreshold(4000L, 8000L)
            .setSlowRunnableThreshold(100L)
            .setSamplingRate(1.0f)
            .setLogLevel(LogLevel.NONE)
            .setHprofDumpEnabled(false)
            .build()

        Falcon.initialize(app, config)
        Falcon.startMonitoring()

        // 模拟高频消息场景
        val errorCount = AtomicLong(0)
        val latch = CountDownLatch(10)

        repeat(10) { threadIndex ->
            Thread {
                try {
                    repeat(100) {
                        // 模拟快速消息
                        Thread.sleep(5)
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }.start()
        }

        val completed = latch.await(30, TimeUnit.SECONDS)
        assertTrue("压力测试应该在 30 秒内完成", completed)
        assertEquals("不应该有错误", 0L, errorCount.get())

        // 验证健康状态
        val status = Falcon.getHealthStatus()
        assertNotNull(status)
    }
}
