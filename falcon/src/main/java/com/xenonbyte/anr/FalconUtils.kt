package com.xenonbyte.anr

import java.text.SimpleDateFormat
import java.util.Date

/**
 * 工具类
 *
 * @author xubo
 */
internal object FalconUtils {
    /**
     * 捕获线程堆栈
     *
     * @param thread 线程
     * @return 线程堆栈字符串
     */
    fun captureStackTrace(thread: Thread): String {
        return stackTraceToString(thread.stackTrace)
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
     * 时间戳格式化
     *
     * @param timestamp 时间戳
     * @param pattern 格式化模版
     */
    fun formatTimestamp(timestamp: Long, pattern: String): String {
        return try {
            val format = SimpleDateFormat(pattern)
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
}