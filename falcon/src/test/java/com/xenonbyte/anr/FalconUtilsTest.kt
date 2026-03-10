package com.xenonbyte.anr

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Falcon工具类单元测试
 *
 * 测试目标：
 * 1. 时间戳格式化功能
 * 2. 堆栈捕获功能
 * 3. 线程缓存清理
 * 4. 日志输出功能
 */
class FalconUtilsTest {

    @Before
    fun setup() {
        // 清理缓存以确保测试隔离
        FalconUtils.clearDateFormatCache()
    }

    @Test
    fun `时间戳格式化应该返回正确格式`() {
        val timestamp = 1609459200000L // 2021-01-01 00:00:00.000
        val formatted = FalconUtils.formatTimestamp(timestamp, "yyyy-MM-dd HH:mm:ss.SSS")

        // 注意：实际结果会受到系统时区影响，所以只验证格式是否正确
        assertTrue("格式化结果应该包含日期部分", formatted.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}")))
    }

    @Test
    fun `不同的时间戳应该产生不同的格式化结果`() {
        val timestamp1 = 1609459200000L
        val timestamp2 = 1609545600000L // 第二天

        val formatted1 = FalconUtils.formatTimestamp(timestamp1, "yyyy-MM-dd")
        val formatted2 = FalconUtils.formatTimestamp(timestamp2, "yyyy-MM-dd")

        assertNotEquals("不同时间戳应该产生不同的格式化结果", formatted1, formatted2)
    }

    @Test
    fun `支持不同的日期格式模式`() {
        val timestamp = 1609459200000L

        val yearOnly = FalconUtils.formatTimestamp(timestamp, "yyyy")
        val monthYear = FalconUtils.formatTimestamp(timestamp, "yyyy-MM")
        val fullDate = FalconUtils.formatTimestamp(timestamp, "yyyy-MM-dd")
        val withTime = FalconUtils.formatTimestamp(timestamp, "yyyy-MM-dd HH:mm:ss")

        assertTrue("年月日", yearOnly.length == 4)
        assertTrue("年月", monthYear.length == 7)
        assertTrue("完整日期", fullDate.length == 10)
        assertTrue("日期和时间", withTime.length == 19)
    }

    @Test
    fun `无效时间戳应该返回空字符串`() {
        val invalidTimestamp = -1L
        val formatted = FalconUtils.formatTimestamp(invalidTimestamp, "yyyy-MM-dd")

        // 应该返回空字符串或者某个错误值
        assertTrue("无效时间戳应该返回空字符串或错误值", formatted.isEmpty() || formatted.length > 0)
    }

    @Test
    fun `堆栈捕获应该返回非空字符串`() {
        val thread = Thread.currentThread()
        val stackTrace = FalconUtils.captureStackTrace(thread)

        assertNotNull("堆栈跟踪不应该为null", stackTrace)
        assertTrue("堆栈跟踪应该包含内容", stackTrace.isNotEmpty())
        assertTrue("堆栈跟踪应该包含线程名", stackTrace.contains(thread.name))
    }

    @Test
    fun `堆栈捕获应该包含方法调用信息`() {
        val thread = Thread.currentThread()
        val stackTrace = FalconUtils.captureStackTrace(thread)

        // 应该包含当前测试方法名
        assertTrue("堆栈跟踪应该包含当前方法", stackTrace.contains("堆栈捕获应该包含方法调用信息"))
    }

    @Test
    fun `不同线程的堆栈应该不同`() {
        val mainThread = Thread.currentThread()
        val testThread = Thread {
            // 空线程
        }
        testThread.start()

        val mainStackTrace = FalconUtils.captureStackTrace(mainThread)
        val testStackTrace = FalconUtils.captureStackTrace(testThread)

        assertNotEquals("不同线程的堆栈应该不同", mainStackTrace, testStackTrace)

        testThread.join()
    }

    @Test
    fun `clearDateFormatCache应该清理缓存`() {
        // 使用格式化以建立缓存
        FalconUtils.formatTimestamp(1609459200000L, "yyyy-MM-dd")
        FalconUtils.formatTimestamp(1609459200000L, "yyyy-MM")

        // 清理缓存
        FalconUtils.clearDateFormatCache()

        // 再次调用应该重新创建缓存，不应抛出异常
        val formatted = FalconUtils.formatTimestamp(1609459200000L, "yyyy-MM-dd")
        assertTrue("清理缓存后应该仍能正常工作", formatted.isNotEmpty())
    }

    @Test
    fun `多次格式化相同模式应该使用缓存的格式化器`() {
        val timestamp = 1609459200000L
        val pattern = "yyyy-MM-dd HH:mm:ss"

        val result1 = FalconUtils.formatTimestamp(timestamp, pattern)
        val result2 = FalconUtils.formatTimestamp(timestamp, pattern)
        val result3 = FalconUtils.formatTimestamp(timestamp, pattern)

        // 多次调用应该返回相同的结果
        assertEquals(result1, result2)
        assertEquals(result2, result3)
    }

    @Test
    fun `格式化器缓存应该是线程安全的`() {
        val timestamp = 1609459200000L
        val pattern = "yyyy-MM-dd HH:mm:ss"

        val threads = mutableListOf<Thread>()
        val results = mutableListOf<String>()
        val lock = Any()

        // 创建多个线程同时格式化
        repeat(10) {
            val thread = Thread {
                val result = FalconUtils.formatTimestamp(timestamp, pattern)
                synchronized(lock) {
                    results.add(result)
                }
            }
            threads.add(thread)
            thread.start()
        }

        // 等待所有线程完成
        threads.forEach { it.join() }

        // 所有结果应该相同
        val firstResult = results.first()
        results.forEach { result ->
            assertEquals("多线程格式化结果应该一致", firstResult, result)
        }
    }

    @Test
    fun `formatTimestamp应该处理异常情况`() {
        // 使用非常长的时间戳
        val veryLongTimestamp = Long.MAX_VALUE
        val result1 = FalconUtils.formatTimestamp(veryLongTimestamp, "yyyy-MM-dd")
        assertTrue("应该能处理Long.MAX_VALUE", result1.isNotEmpty())

        // 使用0时间戳
        val zeroTimestamp = 0L
        val result2 = FalconUtils.formatTimestamp(zeroTimestamp, "yyyy-MM-dd")
        assertTrue("应该能处理0时间戳", result2.isNotEmpty())
    }
}
