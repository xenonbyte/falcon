package com.xenonbyte.anr

/**
 * 日志输出
 *
 * @param level 日志输出级别
 * @param printer 日志打印器
 * @author xubo
 */
internal class FalconLogger(level: LogLevel, printer: LogPrinter) {
    companion object {
        const val TAG = "Falcon_TAG"
    }

    private val realOutLevel = level
    private val realPrinter = printer

    /**
     * 输出日志
     *
     * @param level 日志级别
     * @param messageBlock 日志信息block
     */
    fun log(level: LogLevel, messageBlock: () -> String) {
        if (level.ordinal > realOutLevel.ordinal) {
            return
        }
        val message = messageBlock.invoke()
        realPrinter.print(level, message)
    }
}

/**
 * 日志打印器
 *
 * @author xubo
 */
fun interface LogPrinter {

    /**
     * 打印日志
     *
     * @param level 日志级别
     * @param message 日志消息
     */
    fun print(level: LogLevel, message: String)
}

/**
 * 日志级别
 *
 * @author xubo
 */
enum class LogLevel {
    //不输出日志
    NONE,

    //只输出ERROR日志
    //ANR触发时将输出错误日志
    ERROR,

    //输出ERROR和WARN日志
    //慢任务触发时将输出警告日志
    WARN,

    //输出包含DEBUG在内的所有日志
    DEBUG,
    ;
}