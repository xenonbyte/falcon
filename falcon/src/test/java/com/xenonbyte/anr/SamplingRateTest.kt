package com.xenonbyte.anr

import org.junit.Assert.*
import org.junit.Test

/**
 * 采样率功能测试
 *
 * 测试目标：
 * 1. 采样率参数验证
 * 2. 默认值验证
 * 3. 极值边界测试
 */
class SamplingRateTest {

    @Test
    fun `默认采样率应该是100%`() {
        val config = FalconConfig.Builder().build()
        assertEquals(1.0f, config.samplingRate, 0.001f)
    }

    @Test
    fun `设置有效采样率应该成功`() {
        val config = FalconConfig.Builder()
            .setSamplingRate(0.5f)
            .build()

        assertEquals(0.5f, config.samplingRate, 0.001f)
    }

    @Test
    fun `采样率为0应该成功（完全禁用采样）`() {
        val config = FalconConfig.Builder()
            .setSamplingRate(0.0f)
            .build()

        assertEquals(0.0f, config.samplingRate, 0.001f)
    }

    @Test
    fun `采样率为1应该成功（100%采样）`() {
        val config = FalconConfig.Builder()
            .setSamplingRate(1.0f)
            .build()

        assertEquals(1.0f, config.samplingRate, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `采样率为负数应该抛出异常`() {
        FalconConfig.Builder()
            .setSamplingRate(-0.1f)
            .build()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `采样率大于1应该抛出异常`() {
        FalconConfig.Builder()
            .setSamplingRate(1.1f)
            .build()
    }

    @Test
    fun `极小采样率应该被接受`() {
        val config = FalconConfig.Builder()
            .setSamplingRate(0.001f)
            .build()

        assertEquals(0.001f, config.samplingRate, 0.0001f)
    }

    @Test
    fun `采样率与其他配置一起使用应该正常工作`() {
        val config = FalconConfig.Builder()
            .setAnrThreshold(5000L, 10000L)
            .setSlowRunnableThreshold(500L)
            .setSamplingRate(0.8f)
            .setLogLevel(LogLevel.DEBUG)
            .build()

        assertEquals(5000L, config.foregroundAnrThreshold)
        assertEquals(10000L, config.backgroundAnrThreshold)
        assertEquals(500L, config.slowMessageThreshold)
        assertEquals(0.8f, config.samplingRate, 0.001f)
        assertEquals(LogLevel.DEBUG, config.logLevel)
    }

    @Test
    fun `采样率设置应该支持链式调用`() {
        val config = FalconConfig.Builder()
            .setSamplingRate(0.3f)
            .setSamplingRate(0.7f) // 覆盖之前的值
            .build()

        assertEquals(0.7f, config.samplingRate, 0.001f)
    }
}
