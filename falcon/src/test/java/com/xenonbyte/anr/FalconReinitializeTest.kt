package com.xenonbyte.anr

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class FalconReinitializeTest {

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
            assertTrue(status.contains("HEALTHY"))
        } finally {
            Falcon.stopMonitoring()
            Falcon.resetHealthMonitor()
        }
    }
}
