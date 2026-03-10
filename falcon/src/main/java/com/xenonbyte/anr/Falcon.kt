package com.xenonbyte.anr

import android.app.Application
import com.xenonbyte.activitywatcher.ActivityWatcher
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Falcon ANR 监控库主入口
 *
 * Falcon 是一个轻量级、高性能的 Android ANR 监控库，使用炸弹-扫雷算法精确检测 ANR。
 *
 * ## 快速开始
 *
 * ```kotlin
 * // 1. 创建配置
 * val config = FalconConfig.Builder()
 *     .setAnrThreshold(4000L, 8000L)  // 前台 4 秒，后台 8 秒
 *     .setSlowRunnableThreshold(300)  // 慢任务阈值 300ms
 *     .setSamplingRate(0.8f)          // 80% 采样率
 *     .setListener(object : FalconEventListener {
 *         override fun onAnr(...) { /* 处理 ANR */ }
 *         override fun onSlowRunnable(...) { /* 处理慢任务 */ }
 *     })
 *     .build()
 *
 * // 2. 初始化（通常在 Application.onCreate 中）
 * Falcon.initialize(application, config)
 *
 * // 3. 开始监控
 * Falcon.startMonitoring()
 * ```
 *
 * ## 功能特性
 *
 * - **精确 ANR 检测**: 使用炸弹-扫雷算法，避免轮询开销
 * - **慢任务监控**: 监控主线程耗时操作
 * - **堆栈采集**: 捕获 ANR/慢任务发生时的线程堆栈
 * - **数据转储**: 支持转储内存、线程、设备等信息
 * - **健康监控**: 内置健康检查，异常时自动降级
 * - **采样率控制**: 支持配置采样率以平衡性能和精度
 *
 * ## 炸弹-扫雷算法原理
 *
 * 1. 当主线程开始处理消息时，在专用线程上延迟埋下一颗"炸弹"
 * 2. 同时向主线程发送一个"扫雷"消息
 * 3. 如果主线程能在炸弹爆炸前处理完扫雷消息，则说明没有阻塞
 * 4. 如果扫雷消息在阈值时间内未被处理，说明主线程阻塞，触发 ANR 回调
 *
 * ## 线程安全
 *
 * 所有公共方法都是线程安全的，可以在任何线程调用。
 *
 * @author xubo
 * @see FalconConfig
 * @see FalconEventListener
 * @see FalconHealthMonitor
 */
class Falcon private constructor() {

    private object Holder {
        val INSTANCE = Falcon()
    }

    companion object {
        /**
         * 获取 Falcon 实例
         * @return [Falcon] 实例
         */
        private fun getInstance(): Falcon = Holder.INSTANCE

        /**
         * 获取日志输出（内部使用）
         *
         * @return [FalconLogger] 实例，未初始化时返回 null
         */
        internal fun getLogger(): FalconLogger? {
            return getInstance().logger
        }

        /**
         * 初始化 Falcon 监控
         *
         * 必须在调用 [startMonitoring] 之前调用此方法。
         * 通常在 Application.onCreate() 中初始化。
         *
         * ```kotlin
         * class MyApplication : Application() {
         *     override fun onCreate() {
         *         super.onCreate()
         *         val config = FalconConfig.Builder().build()
         *         Falcon.initialize(this, config)
         *         Falcon.startMonitoring()
         *     }
         * }
         * ```
         *
         * @param app 应用 Application
         * @param config 监控配置
         * @throws IllegalStateException 如果重复初始化
         */
        @JvmStatic
        fun initialize(app: Application, config: FalconConfig) {
            getInstance().init(app, config)
        }

        /**
         * 开始监控
         *
         * 调用此方法后，Falcon 开始监控主线程消息和 ANR。
         * 必须先调用 [initialize] 进行初始化。
         */
        @JvmStatic
        fun startMonitoring() {
            getInstance().start()
        }

        /**
         * 停止监控
         *
         * 停止后不再监控主线程消息和 ANR。
         * 可以再次调用 [startMonitoring] 重新开始监控。
         */
        @JvmStatic
        fun stopMonitoring() {
            getInstance().stop()
        }

        /**
         * 获取健康状态
         *
         * 用于调试和监控 Falcon 的运行状态。
         * 返回包含健康状态、错误计数、最近错误等信息的字符串。
         *
         * @return 健康状态信息字符串，未初始化时返回 "Falcon not initialized"
         */
        @JvmStatic
        fun getHealthStatus(): String {
            return getInstance().controller?.getHealthStatus() ?: "Falcon not initialized"
        }

        /**
         * 重置健康监控器
         *
         * 用于从错误状态中恢复，清除所有错误计数和状态。
         * 调用后健康状态将恢复为 HEALTHY。
         */
        @JvmStatic
        fun resetHealthMonitor() {
            getInstance().controller?.resetHealthMonitor()
        }
    }

    // 日志输出
    private var logger: FalconLogger? = null

    // 控制器
    private var controller: FalconController? = null

    // 是否开启检测
    private var isEnable = AtomicBoolean(false)

    /**
     * 初始化内部状态
     */
    @Synchronized
    private fun init(app: Application, config: FalconConfig) {
        val wasEnabled = isEnable.get()
        if (wasEnabled) {
            controller?.stop()
        }

        // activity堆栈监听初始化
        ActivityWatcher.initialize(app)
        // 创建控制器
        val controller = FalconController(
            app,
            config.foregroundAnrThreshold,
            config.backgroundAnrThreshold,
            config.slowMessageThreshold,
            config.messageSamplingMaxCacheSize,
            config.samplingRate,
            config.hprofDumpEnabled,
            config.dumperMap
        )
        // 控制器初始化
        controller.initialize()
        // 控制器设置监听
        controller.setFalconListener(config.listener)
        this.controller = controller
        this.logger = FalconLogger(config.logLevel, config.logPrinter)

        if (wasEnabled) {
            controller.start()
        }
    }

    /**
     * 开始监控
     */
    private fun start() {
        if (isEnable.compareAndSet(false, true)) {
            // 开启检测
            controller?.start()
        }
    }

    /**
     * 停止监控
     */
    private fun stop() {
        if (isEnable.compareAndSet(true, false)) {
            // 关闭检测
            controller?.stop()
            // 清理线程本地缓存
            FalconUtils.clearDateFormatCache()
        }
    }
}
