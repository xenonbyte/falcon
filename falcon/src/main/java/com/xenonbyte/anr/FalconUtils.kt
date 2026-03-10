package com.xenonbyte.anr

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 工具类
 *
 * @author xubo
 */
internal object FalconUtils {

    // 使用 ThreadLocal 保证线程安全，避免每次创建 SimpleDateFormat 对象
    private val dateFormatCache = ThreadLocal<MutableMap<String, SimpleDateFormat>>()

    /**
     * 获取线程安全的 SimpleDateFormat 实例
     *
     * @param pattern 日期格式
     * @return SimpleDateFormat 实例
     */
    private fun getDateFormat(pattern: String): SimpleDateFormat {
        var cache = dateFormatCache.get()
        if (cache == null) {
            cache = mutableMapOf()
            dateFormatCache.set(cache)
        }

        var format = cache[pattern]
        if (format == null) {
            format = SimpleDateFormat(pattern, Locale.getDefault())
            cache[pattern] = format
        }
        return format
    }

    /**
     * 捕获线程堆栈
     *
     * @param thread 线程
     * @return 线程堆栈字符串
     */
    fun captureStackTrace(thread: Thread): String {
        val stackTrace = stackTraceToString(thread.stackTrace)
        return if (stackTrace.isEmpty()) {
            "Thread: ${thread.name}"
        } else {
            "Thread: ${thread.name}\n$stackTrace"
        }
    }

    /**
     * 堆栈转换成字符串
     *
     * @param stackTrace 堆栈元素数组
     * @return 堆栈字符串
     */
    fun stackTraceToString(stackTrace: Array<StackTraceElement>): String {
        val buffer = StringBuffer()
        if (stackTrace.isNotEmpty()) {
            val indices = stackTrace.indices
            for (i in indices) {
                buffer.append(stackTrace[i].toString())
                if (i < indices.last) {
                    buffer.append("\n")
                }
            }
        }
        return buffer.toString()
    }

    /**
     * 时间戳格式化（优化版，使用线程缓存提高性能）
     *
     * @param timestamp 时间戳
     * @param pattern 格式化模版
     * @return 格式化后的时间字符串
     */
    fun formatTimestamp(timestamp: Long, pattern: String): String {
        return try {
            val format = getDateFormat(pattern)
            format.format(Date(timestamp))
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 输出日志
     *
     * @param level 日志等级
     * @param messageBlock 日志信息block
     */
    fun log(level: LogLevel, messageBlock: () -> String) {
        Falcon.getLogger()?.log(level, messageBlock)
    }

    /**
     * 清理当前线程的日期格式缓存
     */
    fun clearDateFormatCache() {
        dateFormatCache.remove()
    }
}
