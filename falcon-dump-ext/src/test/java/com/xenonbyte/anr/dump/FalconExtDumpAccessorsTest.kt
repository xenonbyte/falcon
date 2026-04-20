package com.xenonbyte.anr.dump

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class FalconExtDumpAccessorsTest {

    @Test
    fun `扩展dumper accessor应该返回结构化数据`() {
        val payload = FalconDumpPayload.parse(createRawPayload())

        assertEquals("[MainActivity]", payload.activityData()?.activityStack)
        assertEquals(true, payload.activityData()?.appForeground)
        assertEquals("[MainActivity]", payload.requireActivityData().activityStack)

        assertEquals(88, payload.batteryData()?.capacity)
        assertEquals("Charging", payload.batteryData()?.chargingStatus)
        assertEquals(88, payload.requireBatteryData().capacity)

        val deviceData = payload.deviceData()
        assertNotNull(deviceData)
        assertEquals("Pixel", deviceData?.model)
        assertEquals(8, deviceData?.cpuCore)
        assertEquals("1500", deviceData?.cpuCurFreq)
        assertEquals("Pixel", payload.requireDeviceData().model)

        assertEquals(123, payload.fdData()?.openFd)
        assertEquals(123, payload.requireFdData().openFd)
    }

    private fun createRawPayload(): String {
        return JSONArray()
            .put(createActivityEntry())
            .put(createBatteryEntry())
            .put(createDeviceEntry())
            .put(createFdEntry())
            .toString()
    }

    private fun createActivityEntry(): JSONObject {
        return JSONObject()
            .put(KEY_DUMPER_NAME, "ActivityDumper")
            .put(
                KEY_DUMPER_DATA,
                JSONObject()
                    .put("activity_stack", "[MainActivity]")
                    .put("app_foreground", true)
            )
    }

    private fun createBatteryEntry(): JSONObject {
        return JSONObject()
            .put(KEY_DUMPER_NAME, "BatteryDumper")
            .put(
                KEY_DUMPER_DATA,
                JSONObject()
                    .put("capacity", 88)
                    .put("charging_status", "Charging")
            )
    }

    private fun createDeviceEntry(): JSONObject {
        return JSONObject()
            .put(KEY_DUMPER_NAME, "DeviceDumper")
            .put(
                KEY_DUMPER_DATA,
                JSONObject()
                    .put("brand", "Google")
                    .put("board", "board")
                    .put("hardware", "hardware")
                    .put("product", "product")
                    .put("manufacturer", "manufacturer")
                    .put("model", "Pixel")
                    .put("display", "display")
                    .put("version", "14")
                    .put("total_memory", 1024L)
                    .put("free_memory", 512L)
                    .put("cpu_api", "arm64-v8a")
                    .put("cpu_core", 8)
                    .put("cpu_min_freq", "1000")
                    .put("cpu_max_freq", "3000")
                    .put("cpu_cur_freq", "1500")
            )
    }

    private fun createFdEntry(): JSONObject {
        return JSONObject()
            .put(KEY_DUMPER_NAME, "FdDumper")
            .put(
                KEY_DUMPER_DATA,
                JSONObject().put("open_fd", 123)
            )
    }

    @Test
    fun `requireBatteryData在缺字段payload上应该抛异常`() {
        val payload = FalconDumpPayload.parse(
            JSONArray()
                .put(
                    JSONObject()
                        .put(KEY_DUMPER_NAME, "BatteryDumper")
                        .put(
                            KEY_DUMPER_DATA,
                            JSONObject()
                                .put("capacity", 88)
                        )
                )
                .toString()
        )

        try {
            payload.requireBatteryData()
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("charging_status") == true)
            return
        }

        throw AssertionError("Expected IllegalStateException")
    }
}
