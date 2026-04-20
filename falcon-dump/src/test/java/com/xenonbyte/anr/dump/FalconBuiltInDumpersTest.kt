package com.xenonbyte.anr.dump

import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class FalconBuiltInDumpersTest {

    @Test
    fun `公开built-in dumper应该保持兼容的payload name`() {
        assertEquals("AppDumper", FalconAppDumper().name)
        assertEquals("MemoryDumper", FalconMemoryDumper().name)
        assertEquals("ThreadDumper", FalconThreadDumper().name)
    }

    @Test
    fun `公开built-in dumper应该返回稳定公开模型`() {
        val app = RuntimeEnvironment.getApplication()

        val appData = FalconAppDumper().collectData(app)
        val memoryData = FalconMemoryDumper().collectData(app)
        val threadData = FalconThreadDumper().collectData(app)

        assertEquals(app.packageName, appData.packageName)
        assertTrue(appData.versionCode >= 0L)

        assertTrue(memoryData.appMaxMemory > 0L)
        assertTrue(memoryData.appTotalMemory > 0L)

        assertFalse(threadData.threads.isEmpty())
        assertEquals(Looper.getMainLooper().thread.name, threadData.threads.first().threadName)
    }
}
