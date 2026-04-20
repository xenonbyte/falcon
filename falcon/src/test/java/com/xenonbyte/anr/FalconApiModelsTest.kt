package com.xenonbyte.anr

import com.xenonbyte.anr.data.MessageSamplingData
import com.xenonbyte.anr.dump.FalconDumpPayload
import com.xenonbyte.anr.dump.stableAppData
import java.util.ArrayDeque
import java.util.Deque
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class FalconApiModelsTest {

    @Test
    fun `FalconDumpPayloadEventAdapter应该向ANR回调提供结构化payload`() {
        val currentSamplingData = MessageSamplingData(1L, "dispatch", 100L)
        val history = ArrayDeque<MessageSamplingData>().apply {
            add(currentSamplingData)
            add(MessageSamplingData(2L, "traversal", 200L))
        }
        val rawPayload = """
            [
              {
                "name":"AppDumper",
                "data":{
                  "version_name":"1.0.0",
                  "version_code":3,
                  "package_name":"com.demo"
                }
              }
            ]
        """.trimIndent()
        val callbackState = AnrCallbackState()
        val listener = object : FalconDumpPayloadEventAdapter() {
            override fun onAnr(
                currentTimestamp: Long,
                mainStackTrace: String,
                messageSamplingData: MessageSamplingData?,
                messageSamplingDataDeque: Deque<MessageSamplingData>,
                dumpPayload: FalconDumpPayload
            ) {
                callbackState.timestamp = currentTimestamp
                callbackState.stackTrace = mainStackTrace
                callbackState.messageSamplingData = messageSamplingData
                callbackState.messageSamplingDataDeque = messageSamplingDataDeque
                callbackState.dumpPayload = dumpPayload
            }
        }

        listener.onAnr(1234L, "main-stack", currentSamplingData, history, rawPayload)

        assertEquals(1234L, callbackState.timestamp)
        assertEquals("main-stack", callbackState.stackTrace)
        assertSame(currentSamplingData, callbackState.messageSamplingData)
        assertSame(history, callbackState.messageSamplingDataDeque)
        assertNotNull(callbackState.dumpPayload)
        assertEquals("com.demo", callbackState.dumpPayload?.stableAppData()?.packageName)
    }

    @Test
    fun `FalconDumpPayloadEventAdapter应该向慢任务回调提供空payload`() {
        val samplingData = MessageSamplingData(3L, "input", 300L)
        var callbackPayload: FalconDumpPayload? = null
        val listener = object : FalconDumpPayloadEventAdapter() {
            override fun onSlowRunnable(
                currentTimestamp: Long,
                mainStackTrace: String,
                messageSamplingData: MessageSamplingData,
                dumpPayload: FalconDumpPayload
            ) {
                assertEquals(5678L, currentTimestamp)
                assertEquals("slow-stack", mainStackTrace)
                assertSame(samplingData, messageSamplingData)
                callbackPayload = dumpPayload
            }
        }

        listener.onSlowRunnable(5678L, "slow-stack", samplingData, "")

        assertNotNull(callbackPayload)
        assertTrue(callbackPayload?.isEmpty() == true)
        assertFalse(callbackPayload?.hasErrors() == true)
    }

    private class AnrCallbackState {
        var timestamp: Long = 0L
        var stackTrace: String = ""
        var messageSamplingData: MessageSamplingData? = null
        var messageSamplingDataDeque: Deque<MessageSamplingData>? = null
        var dumpPayload: FalconDumpPayload? = null
    }
}
