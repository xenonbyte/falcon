package com.xenonbyte.anr

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class FalconReinitializeTest {

    @Test
    fun `未初始化 startMonitoring 应该是 no-op`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        resetFalconSingleton()

        Falcon.startMonitoring()

        assertFalse(isFalconEnabled())
        assertEquals(FalconLifecycleState.NOT_INITIALIZED, Falcon.getLifecycleState())
        assertEquals(FalconHealthState.NOT_INITIALIZED, Falcon.getHealthState())
        assertEquals("Falcon not initialized", Falcon.getHealthStatus())

        Falcon.initialize(
            app,
            FalconConfig.Builder()
                .setLogLevel(LogLevel.NONE)
                .build()
        )

        assertFalse(isFalconEnabled())
        assertNotNull(getFalconController())
        assertEquals(FalconLifecycleState.STOPPED, Falcon.getLifecycleState())
        assertEquals(FalconHealthState.HEALTHY, Falcon.getHealthState())

        Falcon.stopMonitoring()
        Falcon.resetHealthMonitor()
        resetFalconSingleton()
    }

    @Test
    fun `重复初始化应该使用最新配置`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        Falcon.initialize(
            app,
            FalconConfig.Builder()
                .setLogLevel(LogLevel.NONE)
                .build()
        )

        Falcon.initialize(
            app,
            FalconConfig.Builder()
                .setLogLevel(LogLevel.DEBUG)
                .build()
        )

        Falcon.startMonitoring()
        try {
            val status = Falcon.getHealthStatus()
            assertEquals(FalconLifecycleState.MONITORING, Falcon.getLifecycleState())
            assertEquals(FalconHealthState.HEALTHY, Falcon.getHealthState())
            assertTrue(status.contains("HEALTHY"))
        } finally {
            Falcon.stopMonitoring()
            Falcon.resetHealthMonitor()
            resetFalconSingleton()
        }
    }

    @Test
    fun `监控中重复初始化应该保持开启状态`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        resetFalconSingleton()

        Falcon.initialize(
            app,
            FalconConfig.Builder()
                .setLogLevel(LogLevel.NONE)
                .build()
        )

        Falcon.startMonitoring()
        Falcon.initialize(
            app,
            FalconConfig.Builder()
                .setLogLevel(LogLevel.DEBUG)
                .build()
        )

        try {
            assertTrue(isFalconEnabled())
            assertEquals(FalconLifecycleState.MONITORING, Falcon.getLifecycleState())
            assertEquals(FalconHealthState.HEALTHY, Falcon.getHealthState())
            assertTrue(getFalconControllerMessageSamplingThreadAlive())
        } finally {
            Falcon.stopMonitoring()
            Falcon.resetHealthMonitor()
            resetFalconSingleton()
        }
    }

    private fun resetFalconSingleton() {
        Falcon.stopMonitoring()
        val instance = getFalconInstance()
        setFalconField(instance, "controller", null)
        setFalconField(instance, "logger", null)
        (getFalconField(instance, "isEnable") as AtomicBoolean).set(false)
    }

    private fun isFalconEnabled(): Boolean {
        val instance = getFalconInstance()
        return (getFalconField(instance, "isEnable") as AtomicBoolean).get()
    }

    private fun getFalconController(): Any? {
        return getFalconField(getFalconInstance(), "controller")
    }

    private fun getFalconControllerMessageSamplingThreadAlive(): Boolean {
        val controller = getFalconController() ?: return false
        val samplingThread = getDeclaredField(controller.javaClass, "messageSamplingThread").get(controller) as? Thread
        return samplingThread?.isAlive == true
    }

    private fun getFalconInstance(): Any {
        val holderClass = Class.forName("com.xenonbyte.anr.Falcon\$Holder")
        val holder = getDeclaredField(holderClass, "INSTANCE").get(null)
        return holderClass.getDeclaredMethod("getINSTANCE").invoke(holder)
    }

    private fun getFalconField(target: Any, name: String): Any? {
        return getDeclaredField(target.javaClass, name).get(target)
    }

    private fun setFalconField(target: Any, name: String, value: Any?) {
        getDeclaredField(target.javaClass, name).set(target, value)
    }

    private fun getDeclaredField(clazz: Class<*>, name: String) = clazz.getDeclaredField(name).apply {
        isAccessible = true
    }
}
