package com.xenonbyte.anr

import android.util.Log
import com.xenonbyte.anr.FalconLogger.Companion.TAG
import com.xenonbyte.anr.dump.Dumper

/**
 * Falcon 监控配置
 *
 * 使用 Builder 模式构建配置：
 * ```kotlin
 * val config = FalconConfig.Builder()
 *     .setAnrThreshold(4000L, 8000L)
 *     .setSamplingRate(0.5f)  // 50% 采样率
 *     .setSlowRunnableThreshold(300L)
 *     .build()
 * ```
 *
 * @param foregroundAnrThreshold 前台 ANR 触发阈值（毫秒）
 * @param backgroundAnrThreshold 后台 ANR 触发阈值（毫秒）
 * @param slowMessageThreshold 慢任务触发阈值（毫秒）
 * @param messageSamplingMaxCacheSize 消息采样数据最大缓存数量
 * @param samplingRate 消息采样率（0.0f ~ 1.0f，默认 1.0f 即 100%）
 * @param dumperMap 事件数据转储器映射
 * @param logLevel 日志级别
 * @param logPrinter 日志打印器
 * @param hprofDumpEnabled 是否开启 Hprof 数据转储
 * @param listener 事件监听器
 * @author xubo
 */
class FalconConfig(
    val foregroundAnrThreshold: Long,
    val backgroundAnrThreshold: Long,
    val slowMessageThreshold: Long,
    val messageSamplingMaxCacheSize: Int,
    val samplingRate: Float,
    val dumperMap: Map<FalconEvent, LinkedHashSet<Dumper<*>>>,
    val logLevel: LogLevel,
    val logPrinter: LogPrinter,
    val hprofDumpEnabled: Boolean,
    val listener: FalconEventListener?,
) {
    val dumpCollectionEnabled: Boolean
        get() = hprofDumpEnabled

    private companion object {
        // 默认前台Anr触发阈值
        const val DEFAULT_FOREGROUND_ANR_THRESHOLD = 4000L

        // 默认后台Anr触发阈值
        const val DEFAULT_BACKGROUND_ANR_THRESHOLD = 8000L

        // 默认慢任务触发阈值
        const val DEFAULT_SLOW_RUNNABLE_THRESHOLD = 300L

        // 消息采样数据最大缓存数量
        const val DEFAULT_MESSAGE_SAMPLING_MAX_CACHE_SIZE = 30

        // 默认采样率 (100%)
        const val DEFAULT_SAMPLING_RATE = 1.0f
    }

    /**
     * [FalconConfig] 构建器
     *
     * 提供链式调用方式配置 Falcon 监控参数：
     * - ANR 阈值（前台/后台）
     * - 慢任务阈值
     * - 消息采样率
     * - 日志级别
     * - 数据转储器
     *
     * @author xubo
     */
    class Builder {
        private var foregroundThreshold = DEFAULT_FOREGROUND_ANR_THRESHOLD
        private var backgroundAnrThreshold = DEFAULT_BACKGROUND_ANR_THRESHOLD
        private var slowRunnableThreshold = DEFAULT_SLOW_RUNNABLE_THRESHOLD
        private var messageSamplingMaxCacheSize = DEFAULT_MESSAGE_SAMPLING_MAX_CACHE_SIZE
        private var samplingRate = DEFAULT_SAMPLING_RATE
        private var logLevel: LogLevel = LogLevel.WARN
        private var logPrinter: LogPrinter = DefaultLogPrinter()
        private var listener: FalconEventListener? = null
        private val dumperMap = linkedMapOf<FalconEvent, LinkedHashSet<Dumper<*>>>()
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
         *
         * 当发生 [FalconEvent.ANR_EVENT] 时可获取的消息最大回放量
         *
         * @param maxSize 最大数量（1 ~ 1000）
         * @return [FalconConfig.Builder] 对象
         */
        fun setMessageSamplingMaxCacheSize(maxSize: Int): Builder {
            this.messageSamplingMaxCacheSize = maxSize
            return this
        }

        /**
         * 设置消息采样率
         *
         * 采样率决定了监控多少比例的主线程消息：
         * - 1.0f (100%): 采样所有消息，最精确但开销最大
         * - 0.5f (50%): 采样一半消息，平衡精度和性能
         * - 0.1f (10%): 采样 10% 消息，性能开销小但可能漏检
         *
         * **注意**: 采样率低于 100% 可能导致部分 ANR 漏检，
         * 生产环境建议不低于 50%。
         *
         * @param rate 采样率，范围 [0.0f, 1.0f]
         * @return [FalconConfig.Builder] 对象
         */
        fun setSamplingRate(rate: Float): Builder {
            this.samplingRate = rate
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
        @Deprecated(message = "推荐使用setDumpCollectionEnabled(Boolean)方法")
        fun setHprofDumpEnabled(hprofDumpEnabled: Boolean): Builder {
            this.hprofDumpEnabled = hprofDumpEnabled
            return this
        }

        /**
         * 设置是否开启 Dumper 数据采集。
         *
         * 这是对 [setHprofDumpEnabled] 的语义化别名，推荐优先使用。
         *
         * @param enabled 是否开启
         * @return [FalconConfig.Builder]对象
         */
        fun setDumpCollectionEnabled(enabled: Boolean): Builder {
            this.hprofDumpEnabled = enabled
            return this
        }

        /**
         * 构建 Falcon 配置
         *
         * @return [FalconConfig] 对象
         * @throws IllegalArgumentException 参数不合法时抛出
         */
        fun build(): FalconConfig {
            // 参数校验
            require(foregroundThreshold > 0) {
                "Foreground ANR threshold must be positive, but was $foregroundThreshold"
            }
            require(backgroundAnrThreshold > 0) {
                "Background ANR threshold must be positive, but was $backgroundAnrThreshold"
            }
            require(backgroundAnrThreshold >= foregroundThreshold) {
                "Background ANR threshold ($backgroundAnrThreshold) must be >= foreground threshold ($foregroundThreshold)"
            }
            require(slowRunnableThreshold > 0) {
                "Slow runnable threshold must be positive, but was $slowRunnableThreshold"
            }
            require(slowRunnableThreshold < foregroundThreshold) {
                "Slow runnable threshold ($slowRunnableThreshold) must be < foreground ANR threshold ($foregroundThreshold)"
            }
            require(messageSamplingMaxCacheSize > 0) {
                "Message sampling max cache size must be positive, but was $messageSamplingMaxCacheSize"
            }
            require(messageSamplingMaxCacheSize <= 1000) {
                "Message sampling max cache size should not exceed 1000 for memory efficiency"
            }
            require(samplingRate in 0.0f..1.0f) {
                "Sampling rate must be in range [0.0, 1.0], but was $samplingRate"
            }

            return FalconConfig(
                foregroundThreshold,
                backgroundAnrThreshold,
                slowRunnableThreshold,
                messageSamplingMaxCacheSize,
                samplingRate,
                dumperMap.toMap(),
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
                // 无需打印
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
