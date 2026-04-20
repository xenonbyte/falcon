package com.xenonbyte.anr

import com.xenonbyte.anr.data.MessageSamplingData
import com.xenonbyte.anr.dump.Dumper
import java.util.Deque
import org.junit.Assert.*
import org.junit.Test

/**
 * Falcon配置类单元测试
 *
 * 测试目标：
 * 1. 配置参数验证
 * 2. 默认值验证
 * 3. 参数合法性检查
 * 4. Builder模式正确性
 */
class FalconConfigTest {

    @Test
    fun `默认配置应该构建成功`() {
        val config = FalconConfig.Builder().build()

        assertEquals(4000L, config.foregroundAnrThreshold)
        assertEquals(8000L, config.backgroundAnrThreshold)
        assertEquals(300L, config.slowMessageThreshold)
        assertEquals(30, config.messageSamplingMaxCacheSize)
        assertEquals(LogLevel.WARN, config.logLevel)
        assertTrue(config.hprofDumpEnabled)
        assertTrue(config.dumpCollectionEnabled)
        assertEquals(1.0f, config.samplingRate)
    }

    @Test
    fun `自定义配置应该正确保存`() {
        val listener = object : FalconEventListener {
            override fun onSlowRunnable(
                currentTimestamp: Long,
                mainStackTrace: String,
                messageSamplingData: MessageSamplingData,
                hprofData: String
            ) {
            }

            override fun onAnr(
                currentTimestamp: Long,
                mainStackTrace: String,
                messageSamplingData: MessageSamplingData?,
                messageSamplingHistory: Deque<MessageSamplingData>,
                hprofData: String
            ) {
            }
        }

        val config = FalconConfig.Builder()
            .setAnrThreshold(5000L, 10000L)
            .setSlowRunnableThreshold(500L)
            .setMessageSamplingMaxCacheSize(50)
            .setSamplingRate(0.5f)
            .setLogLevel(LogLevel.DEBUG)
            .setHprofDumpEnabled(false)
            .setEventListener(listener)
            .build()

        assertEquals(5000L, config.foregroundAnrThreshold)
        assertEquals(10000L, config.backgroundAnrThreshold)
        assertEquals(500L, config.slowMessageThreshold)
        assertEquals(50, config.messageSamplingMaxCacheSize)
        assertEquals(0.5f, config.samplingRate)
        assertEquals(LogLevel.DEBUG, config.logLevel)
        assertFalse(config.hprofDumpEnabled)
        assertFalse(config.dumpCollectionEnabled)
        assertEquals(listener, config.listener)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `前台ANR阈值为负数应该抛出异常`() {
        FalconConfig.Builder()
            .setAnrThreshold(-1L, 8000L)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `后台ANR阈值为负数应该抛出异常`() {
        FalconConfig.Builder()
            .setAnrThreshold(4000L, -1L)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `后台ANR阈值小于前台阈值应该抛出异常`() {
        FalconConfig.Builder()
            .setAnrThreshold(5000L, 3000L)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `慢任务阈值为负数应该抛出异常`() {
        FalconConfig.Builder()
            .setSlowRunnableThreshold(-1L)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `慢任务阈值大于等于前台ANR阈值应该抛出异常`() {
        FalconConfig.Builder()
            .setSlowRunnableThreshold(4000L)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `消息采样缓存大小为负数应该抛出异常`() {
        FalconConfig.Builder()
            .setMessageSamplingMaxCacheSize(-1)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `消息采样缓存大小超过1000应该抛出异常`() {
        FalconConfig.Builder()
            .setMessageSamplingMaxCacheSize(1001)
            .build()
    }

    @Test
    fun `添加Dumper应该正确保存`() {
        val dumper = TestDumper()
        val config = FalconConfig.Builder()
            .addEventDumper(FalconEvent.ANR_EVENT, dumper)
            .build()

        assertTrue(config.dumperMap.containsKey(FalconEvent.ANR_EVENT))
        assertTrue(config.dumperMap[FalconEvent.ANR_EVENT]?.contains(dumper) == true)
    }

    @Test
    fun `Builder链式调用应该正常工作`() {
        val config = FalconConfig.Builder()
            .setAnrThreshold(3000L, 6000L)
            .setSlowRunnableThreshold(200L)
            .setMessageSamplingMaxCacheSize(40)
            .setLogLevel(LogLevel.NONE)
            .setHprofDumpEnabled(false)
            .build()

        assertEquals(3000L, config.foregroundAnrThreshold)
        assertEquals(6000L, config.backgroundAnrThreshold)
        assertEquals(200L, config.slowMessageThreshold)
        assertEquals(40, config.messageSamplingMaxCacheSize)
        assertEquals(LogLevel.NONE, config.logLevel)
        assertFalse(config.hprofDumpEnabled)
        assertFalse(config.dumpCollectionEnabled)
    }

    @Test
    fun `Dumper数据采集开关别名应该正确保存`() {
        val config = FalconConfig.Builder()
            .setDumpCollectionEnabled(false)
            .build()

        assertFalse(config.hprofDumpEnabled)
        assertFalse(config.dumpCollectionEnabled)
    }

    @Test
    fun `多个Dumper添加到同一事件应该都保存`() {
        val dumper1 = TestDumper("dumper1")
        val dumper2 = TestDumper("dumper2")
        val dumper3 = TestDumper("dumper3")

        val config = FalconConfig.Builder()
            .addEventDumper(FalconEvent.ANR_EVENT, dumper1)
            .addEventDumper(FalconEvent.ANR_EVENT, dumper2)
            .addEventDumper(FalconEvent.ANR_EVENT, dumper3)
            .build()

        val dumpers = config.dumperMap[FalconEvent.ANR_EVENT]
        assertEquals(3, dumpers?.size)
        assertTrue(dumpers?.contains(dumper1) == true)
        assertTrue(dumpers?.contains(dumper2) == true)
        assertTrue(dumpers?.contains(dumper3) == true)
    }

    @Test
    fun `不同事件的Dumper应该分开保存`() {
        val anrDumper = TestDumper("anr_dumper")
        val slowDumper = TestDumper("slow_dumper")

        val config = FalconConfig.Builder()
            .addEventDumper(FalconEvent.ANR_EVENT, anrDumper)
            .addEventDumper(FalconEvent.SLOW_RUNNABLE_EVENT, slowDumper)
            .build()

        assertTrue(config.dumperMap[FalconEvent.ANR_EVENT]?.contains(anrDumper) == true)
        assertTrue(config.dumperMap[FalconEvent.SLOW_RUNNABLE_EVENT]?.contains(slowDumper) == true)
        assertFalse(config.dumperMap[FalconEvent.ANR_EVENT]?.contains(slowDumper) == true)
        assertFalse(config.dumperMap[FalconEvent.SLOW_RUNNABLE_EVENT]?.contains(anrDumper) == true)
    }

    /**
     * 测试用的Dumper实现
     */
    class TestData(val dataName: String) : com.xenonbyte.anr.dump.DumpData {
        override fun toJson(): org.json.JSONObject {
            return org.json.JSONObject().apply {
                put("name", dataName)
            }
        }
    }

    class TestDumper(dumperName: String = "test") :
        com.xenonbyte.anr.dump.Dumper<TestData>(dumperName) {
        override fun collectData(app: android.app.Application): TestData {
            return TestData(name)
        }
    }
}
