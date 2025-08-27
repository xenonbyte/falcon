package com.xenonbyte.anr

import android.util.ArrayMap
import android.util.Log
import com.xenonbyte.anr.FalconLogger.Companion.TAG
import com.xenonbyte.anr.dump.Dumper

/**
 * 监控配置
 *
 * @author xubo
 */
class FalconConfig(
    val foregroundAnrThreshold: Long,
    val backgroundAnrThreshold: Long,
    val slowMessageThreshold: Long,
    val messageSamplingMaxCacheSize: Int,
    val dumperMap: ArrayMap<FalconEvent, LinkedHashSet<Dumper<*>>>,
    val logLevel: LogLevel,
    val logPrinter: LogPrinter,
    val hprofDumpEnabled: Boolean,
    val listener: FalconEventListener?,
) {

    private companion object {
        //默认前台Anr触发阈值
        const val DEFAULT_FOREGROUND_ANR_THRESHOLD = 4000L

        //默认后台Anr触发阈值
        const val DEFAULT_BACKGROUND_ANR_THRESHOLD = 8000L

        //默认慢任务触发阈值
        const val DEFAULT_SLOW_RUNNABLE_THRESHOLD = 300L

        //消息采样数据最大缓存数量
        const val DEFAULT_MESSAGE_SAMPLING_MAX_CACHE_SIZE = 30
    }

    /**
     * [FalconConfig]构建器
     *
     * @param app 应用Application
     * @author xubo
     */
    class Builder {
        private var foregroundThreshold = DEFAULT_FOREGROUND_ANR_THRESHOLD
        private var backgroundAnrThreshold = DEFAULT_BACKGROUND_ANR_THRESHOLD
        private var slowRunnableThreshold = DEFAULT_SLOW_RUNNABLE_THRESHOLD
        private var messageSamplingMaxCacheSize = DEFAULT_MESSAGE_SAMPLING_MAX_CACHE_SIZE
        private var logLevel: LogLevel = LogLevel.WARN
        private var logPrinter: LogPrinter = DefaultLogPrinter()
        private var listener: FalconEventListener? = null
        private val dumperMap = ArrayMap<FalconEvent, LinkedHashSet<Dumper<*>>>()
        private var hprofDumpEnabled = true

        /**
         * 设置Anr触发阈值
         * <p>
         * 当主线程阻塞超过该阈值将触发[FalconEventListener.onAnr]回调
         *
         * @param foregroundThreshold 前台Anr触发阈值
         * @param backgroundThreshold 后台Anr触发阈值
         * @return [FalconConfig.Builder]对象
         */
        fun setAnrThreshold(foregroundThreshold: Long, backgroundThreshold: Long): Builder {
            this.foregroundThreshold = foregroundThreshold
            this.backgroundAnrThreshold = backgroundThreshold
            return this
        }

        /**
         * 设置慢任务触发阈值
         * <p>
         * 当主线程任务执行时长超过该阈值将触发[FalconEventListener.onSlowRunnable]回调
         *
         * @param threshold 慢任务触发阈值
         * @return [FalconConfig.Builder]对象
         */
        fun setSlowRunnableThreshold(threshold: Long): Builder {
            this.slowRunnableThreshold = threshold
            return this
        }

        /**
         * 设置消息采样数据缓存最大数量
         * <p>
         * 当发生[FalconEvent.ANR_EVENT]时可获取的消息最大回放量
         *
         * @param maxSize 最大数量
         * @return [FalconConfig.Builder]对象
         */
        fun setMessageSamplingMaxCacheSize(maxSize: Int): Builder {
            this.messageSamplingMaxCacheSize = maxSize
            return this
        }

        /**
         * 添加指定事件[FalconEvent]数据转储器
         * <p>
         * 当触发指定事件[FalconEvent]时，会使用该数据转储器采集数据
         *
         * @param event Falcon事件
         * @param dumper 数据dumper
         * @return [FalconConfig.Builder]对象
         */
        fun addEventDumper(event: FalconEvent, dumper: Dumper<*>): Builder {
            val dumperList = dumperMap.getOrPut(event) { LinkedHashSet() }
            dumperList.add(dumper)
            return this
        }

        /**
         * 设置事件监听
         *
         * @param listener Falcon事件监听
         * @return [FalconConfig.Builder]对象
         */
        fun setEventListener(listener: FalconEventListener): Builder {
            this.listener = listener
            return this
        }

        /**
         * 设置日志级别
         *
         * @param level 日志级别
         * @return [FalconConfig.Builder]对象
         */
        fun setLogLevel(level: LogLevel): Builder {
            this.logLevel = level
            return this
        }

        /**
         * 设置日志打印器
         *
         * @param printer 日志打印器
         * @return [FalconConfig.Builder]对象
         */
        fun setLogPrinter(printer: LogPrinter): Builder {
            this.logPrinter = printer
            return this
        }

        /**
         * 设置是否开启数据分析
         *
         * @param hprofDumpEnabled 是否开启
         * @return [FalconConfig.Builder]对象
         */
        fun setHprofDumpEnabled(hprofDumpEnabled: Boolean): Builder {
            this.hprofDumpEnabled = hprofDumpEnabled
            return this
        }

        /**
         * 构建Falcon配置
         *
         * @return [FalconConfig]对象
         */
        fun build(): FalconConfig {
            return FalconConfig(
                foregroundThreshold,
                backgroundAnrThreshold,
                slowRunnableThreshold,
                messageSamplingMaxCacheSize,
                dumperMap,
                logLevel,
                logPrinter,
                hprofDumpEnabled,
                listener,
            )
        }
    }
}

/**
 * 默认日志打印
 *
 * @author xubo
 */
private class DefaultLogPrinter : LogPrinter {
    override fun print(level: LogLevel, message: String) {
        when (level) {
            LogLevel.NONE -> {
                //无需打印
            }

            LogLevel.ERROR -> {
                Log.e(TAG, message)
            }

            LogLevel.WARN -> {
                Log.w(TAG, message)
            }

            LogLevel.DEBUG -> {
                Log.d(TAG, message)
            }
        }
    }
}
