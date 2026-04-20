package com.xenonbyte.anr.dump

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class FalconDumpPayloadTest {

    @Test
    fun `空字符串应该解析为空payload`() {
        val payload = FalconDumpPayload.parse("")

        assertTrue(payload.isEmpty())
        assertFalse(payload.hasErrors())
        assertEquals("", payload.raw)
    }

    @Test
    fun `合法json应该解析为结构化条目`() {
        val raw = JSONArray()
            .put(
                JSONObject()
                    .put(KEY_DUMPER_NAME, "AppDumper")
                    .put(KEY_DUMPER_DATA, JSONObject().put("package_name", "demo"))
            )
            .put(
                JSONObject()
                    .put(KEY_DUMPER_NAME, "BatteryDumper")
                    .put(KEY_DUMPER_ERROR, "collect data failed: permission denied")
            )
            .toString()

        val payload = FalconDumpPayload.parse(raw)

        assertFalse(payload.isEmpty())
        assertTrue(payload.hasErrors())

        val appEntry = payload.require("AppDumper")
        assertTrue(appEntry.isSuccess())
        assertNotNull(appEntry.data)
        assertEquals("demo", appEntry.data?.getString("package_name"))

        val batteryEntry = payload.require("BatteryDumper")
        assertFalse(batteryEntry.isSuccess())
        assertNull(batteryEntry.data)
        assertEquals("collect data failed: permission denied", batteryEntry.error)
    }

    @Test
    fun `内置dumper accessor应该返回结构化数据`() {
        val payload = FalconDumpPayload.parse(createBuiltInPayload())

        assertEquals("com.demo", payload.stableAppData()?.packageName)
        assertEquals("1.2.3", payload.stableAppData()?.versionName)
        assertEquals(7L, payload.stableAppData()?.versionCode)
        assertEquals("com.demo", payload.requireStableAppData().packageName)

        assertEquals(100L, payload.stableMemoryData()?.appMaxMemory)
        assertEquals(120L, payload.stableMemoryData()?.deviceFreeMemory)
        assertEquals(false, payload.stableMemoryData()?.deviceLowMemory)
        assertEquals(100L, payload.requireStableMemoryData().appMaxMemory)

        val stableThreadData = payload.stableThreadData()
        assertNotNull(stableThreadData)
        assertEquals(1, stableThreadData?.threads?.size)
        assertEquals("main", stableThreadData?.threads?.first()?.threadName)
        assertEquals("main", payload.requireStableThreadData().threads.first().threadName)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `旧built-in accessor应该继续兼容`() {
        val payload = FalconDumpPayload.parse(createBuiltInPayload())

        assertEquals("com.demo", payload.appData()?.packageName)
        assertEquals(100L, payload.memoryData()?.appMaxMemory)
        assertEquals("main", payload.threadData()?.list?.first()?.threadName)
    }

    private fun createBuiltInPayload(): String {
        return JSONArray()
            .put(createAppEntry())
            .put(createMemoryEntry())
            .put(createThreadEntry())
            .toString()
    }

    private fun createAppEntry(): JSONObject {
        return JSONObject()
            .put(KEY_DUMPER_NAME, "AppDumper")
            .put(
                KEY_DUMPER_DATA,
                JSONObject()
                    .put("version_name", "1.2.3")
                    .put("version_code", 7L)
                    .put("package_name", "com.demo")
            )
    }

    private fun createMemoryEntry(): JSONObject {
        return JSONObject()
            .put(KEY_DUMPER_NAME, "MemoryDumper")
            .put(
                KEY_DUMPER_DATA,
                JSONObject()
                    .put("app_max_memory", 100L)
                    .put("app_total_memory", 80L)
                    .put("app_free_memory", 20L)
                    .put("app_native_heap_max_memory", 50L)
                    .put("app_native_heap_total_memory", 40L)
                    .put("app_native_heap_free_memory", 10L)
                    .put("device_max_memory", 200L)
                    .put("device_free_memory", 120L)
                    .put("device_threshold_memory", 30L)
                    .put("device_low_memory", false)
            )
    }

    private fun createThreadEntry(): JSONObject {
        return JSONObject()
            .put(KEY_DUMPER_NAME, "ThreadDumper")
            .put(
                KEY_DUMPER_DATA,
                JSONObject().put("thread_info_list", JSONArray().put(createThreadInfoJson()))
            )
    }

    private fun createThreadInfoJson(): JSONObject {
        return JSONObject()
            .put("thread_id", 1L)
            .put("thread_name", "main")
            .put("thread_state", "RUNNABLE")
            .put("thread_priority", 5)
            .put("thread_alive", true)
            .put("thread_interrupted", false)
            .put("thread_daemon", false)
    }

    @Test
    fun `非法json应该返回解析错误条目而不是抛异常`() {
        val payload = FalconDumpPayload.parse("not-json")

        assertFalse(payload.isEmpty())
        assertTrue(payload.hasErrors())
        assertEquals("__payload_parse_error__", payload.entries.single().name)
        assertTrue(payload.entries.single().error?.contains("parse dump payload failed") == true)
    }

    @Test
    fun `找不到指定dumper时require应该抛出异常`() {
        val payload = FalconDumpPayload.empty()

        try {
            payload.require("MissingDumper")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("MissingDumper") == true)
            return
        }

        throw AssertionError("Expected IllegalArgumentException")
    }

    @Test
    fun `通用decode在错误条目上应该返回null`() {
        val entry = FalconDumpEntry(
            name = "BatteryDumper",
            data = JSONObject().put("capacity", 80),
            error = "collect data failed"
        )

        val decoded = entry.decode { it.optInt("capacity") }

        assertNull(decoded)
    }

    @Test
    fun `requireDecoded在错误条目上应该抛异常`() {
        val raw = JSONArray()
            .put(
                JSONObject()
                    .put(KEY_DUMPER_NAME, "BatteryDumper")
                    .put(KEY_DUMPER_ERROR, "collect data failed")
            )
            .toString()

        val payload = FalconDumpPayload.parse(raw)

        try {
            payload.requireDecoded("BatteryDumper") { it.optInt("capacity") }
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("BatteryDumper") == true)
            return
        }

        throw AssertionError("Expected IllegalStateException")
    }

    @Test
    fun `requireStableAppData在缺字段payload上应该抛异常`() {
        val raw = JSONArray()
            .put(
                JSONObject()
                    .put(KEY_DUMPER_NAME, "AppDumper")
                    .put(
                        KEY_DUMPER_DATA,
                        JSONObject()
                            .put("version_name", "1.2.3")
                            .put("version_code", 7L)
                    )
            )
            .toString()

        val payload = FalconDumpPayload.parse(raw)

        try {
            payload.requireStableAppData()
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("package_name") == true)
            return
        }

        throw AssertionError("Expected IllegalStateException")
    }
}
