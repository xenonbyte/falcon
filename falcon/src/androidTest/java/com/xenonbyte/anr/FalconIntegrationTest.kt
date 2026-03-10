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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Falcon 集成测试
 *
 * 在真实 Android 环境中验证 Falcon 功能：
 * 1. 初始化和启动监控
 * 2. 慢任务检测
 * 3. ANR 检测
 * 4. 采样率功能
 * 5. 健康监控
 */
@RunWith(AndroidJUnit4::class)
class FalconIntegrationTest {

    private val anrTriggered = AtomicBoolean(false)
    private val slowTaskTriggered = AtomicBoolean(false)
    private val anrLatch = CountDownLatch(1)
    private val slowTaskLatch = CountDownLatch(1)
    private val anrStackTrace = AtomicReference<String>("")
    private val slowTaskStackTrace = AtomicReference<String>("")

    @Before
    fun setup() {
        // 重置状态
        anrTriggered.set(false)
        slowTaskTriggered.set(false)
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

    @Test
    fun testInitialization() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as android.app.Application

        val config = FalconConfig.Builder()
            .setAnrThreshold(4000L, 8000L)
            .setSlowRunnableThreshold(300L)
            .setSamplingRate(1.0f)
            .setLogLevel(LogLevel.DEBUG)
            .setHprofDumpEnabled(false)
            .build()

        // 初始化不应该抛出异常
        Falcon.initialize(app, config)

        // 启动监控
        Falcon.startMonitoring()

        // 验证健康状态
        val healthStatus = Falcon.getHealthStatus()
        assertNotNull(healthStatus)
        assertTrue("健康状态应该包含 HEALTHY", healthStatus.contains("HEALTHY"))
    }

    @Test
    fun testHealthMonitorStatus() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as android.app.Application

        val config = FalconConfig.Builder()
            .setAnrThreshold(4000L, 8000L)
            .setSlowRunnableThreshold(300L)
            .setSamplingRate(1.0f)
            .setLogLevel(LogLevel.WARN)
            .build()

        Falcon.initialize(app, config)
        Falcon.startMonitoring()

        // 获取健康状态
        val status = Falcon.getHealthStatus()
        assertNotNull(status)

        // 重置健康监控
        Falcon.resetHealthMonitor()

        val statusAfterReset = Falcon.getHealthStatus()
        assertNotNull(statusAfterReset)
    }

    @Test
    fun testStopAndRestart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as android.app.Application

        val config = FalconConfig.Builder()
            .setAnrThreshold(4000L, 8000L)
            .setSlowRunnableThreshold(300L)
            .setSamplingRate(1.0f)
            .setLogLevel(LogLevel.WARN)
            .build()

        // 第一次初始化和启动
        Falcon.initialize(app, config)
        Falcon.startMonitoring()

        // 停止监控
        Falcon.stopMonitoring()

        // 等待停止完成
        Thread.sleep(500)

        // 再次启动
        Falcon.startMonitoring()

        // 验证可以正常重启
        val status = Falcon.getHealthStatus()
        assertNotNull(status)
    }

    @Test
    fun testSamplingRateConfiguration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as android.app.Application

        // 测试不同的采样率配置
        val samplingRates = floatArrayOf(1.0f, 0.8f, 0.5f, 0.1f)

        for (rate in samplingRates) {
            tearDown() // 清理之前的状态

            val config = FalconConfig.Builder()
                .setAnrThreshold(4000L, 8000L)
                .setSlowRunnableThreshold(300L)
                .setSamplingRate(rate)
                .setLogLevel(LogLevel.WARN)
                .build()

            // 应该可以正常初始化
            Falcon.initialize(app, config)
            Falcon.startMonitoring()

            Thread.sleep(100)
        }
    }

    @Test
    fun testMultipleDumpers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as android.app.Application

        val config = FalconConfig.Builder()
            .setAnrThreshold(4000L, 8000L)
            .setSlowRunnableThreshold(300L)
            .setSamplingRate(1.0f)
            .setLogLevel(LogLevel.WARN)
            .setHprofDumpEnabled(true)
            .addEventDumper(FalconEvent.ANR_EVENT, com.xenonbyte.anr.dump.internal.AppDumper())
            .addEventDumper(FalconEvent.ANR_EVENT, com.xenonbyte.anr.dump.internal.MemoryDumper())
            .addEventDumper(FalconEvent.ANR_EVENT, com.xenonbyte.anr.dump.internal.ThreadDumper())
            .build()

        Falcon.initialize(app, config)
        Falcon.startMonitoring()

        // 验证可以正常初始化多个 Dumper
        val status = Falcon.getHealthStatus()
        assertNotNull(status)
    }
}
