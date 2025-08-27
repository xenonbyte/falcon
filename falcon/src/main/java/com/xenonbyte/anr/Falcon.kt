package com.xenonbyte.anr

import android.app.Application
import android.os.Debug
import com.xenonbyte.activitywatcher.ActivityWatcher
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 用于监测Android Anr和主线程耗时Runnable
 *
 * @author xubo
 */
class Falcon private constructor() {

    private object Holder {
        val INSTANCE = Falcon()
    }

    companion object {
        /**
         * 获取Falcon实例
         * @return [Falcon]实例
         */
        private fun getInstance(): Falcon = Holder.INSTANCE

        /**
         * 获取日志输出
         *
         * @return [FalconLogger]实例
         */
        internal fun getLogger(): FalconLogger? {
            return getInstance().logger
        }

        /**
         * 初始化
         * @param app 应用Application
         * @param config 配置
         */
        @JvmStatic
        fun initialize(app: Application, config: FalconConfig) {
            getInstance().init(app, config)
        }

        /**
         * 开启监测
         */
        @JvmStatic
        fun startMonitoring() {
            getInstance().start()
        }

        /**
         * 停止监测
         */
        @JvmStatic
        fun stopMonitoring() {
            getInstance().stop()
        }
    }

    //日志输出
    private var logger: FalconLogger? = null

    //控制器
    private var controller: FalconController? = null

    //是否初始化
    private var isInitialize = AtomicBoolean(false)

    //是否开启检测
    private var isEnable = AtomicBoolean(false)

    /**
     * 初始化
     */
    private fun init(app: Application, config: FalconConfig) {
        if (isInitialize.compareAndSet(false, true)) {
            //activity堆栈监听初始化
            ActivityWatcher.initialize(app)
            //创建控制器
            val controller = FalconController(
                app,
                config.foregroundAnrThreshold,
                config.backgroundAnrThreshold,
                config.slowMessageThreshold,
                config.messageSamplingMaxCacheSize,
                config.hprofDumpEnabled,
                config.dumperMap
            )
            //控制器初始化
            controller.initialize()
            //控制器设置监听
            controller.setFalconListener(config.listener)
            this.controller = controller
            this.logger = FalconLogger(config.logLevel, config.logPrinter)
        }
    }

    /**
     * 开启监测
     */
    private fun start() {
        if (isEnable.compareAndSet(false, true)) {
            //开启检测
            controller?.start()
        }
    }

    /**
     * 停止监测
     */
    private fun stop() {
        if (isEnable.compareAndSet(false, true)) {
            //关闭检测
            controller?.stop()
        }
    }
}