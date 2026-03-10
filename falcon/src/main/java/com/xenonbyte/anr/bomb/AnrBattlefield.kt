package com.xenonbyte.anr.bomb

import android.os.Handler
import android.os.Looper
import com.xenonbyte.activitywatcher.ActivityWatcher
import com.xenonbyte.anr.FalconUtils
import com.xenonbyte.anr.LogLevel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * ANR 模拟战场 - 炸弹-扫雷算法核心实现
 *
 * 1. **埋雷（炸弹工兵）**: 当主线程开始处理消息时，在专用线程（AnrBombThread）上
 *    延迟埋下一颗"炸弹"。如果在 ANR 阈值时间内炸弹未被拆除，则判定为 ANR。
 *
 * 2. **扫雷（扫雷工兵）**: 同时向主线程发送一个"扫雷"消息。如果主线程能在炸弹
 *    爆炸前处理完扫雷消息，则说明主线程没有阻塞，炸弹被拆除。
 *
 * 3. **爆炸判定**: 如果扫雷消息在阈值时间内未被处理，说明主线程阻塞，炸弹爆炸，
 *    触发 ANR 回调。
 *
 * ## 为什么使用 500ms 扫雷间隔？
 *
 * `BOMB_DEFUSING_INTERVAL_DURATION = 500ms` 是扫雷消息提前于炸弹爆炸的时间。
 * - 给主线程足够时间处理扫雷消息
 * - 避免边界情况下的误判
 *
 * ## 前后台阈值
 *
 * - 前台: 默认 4 秒（用户可感知的卡顿）
 * - 后台: 默认 8 秒（系统 ANR 监控阈值约为 5 秒）
 *
 * @param foregroundAnrThreshold 前台 ANR 触发阈值（毫秒）
 * @param backgroundAnrThreshold 后台 ANR 触发阈值（毫秒）
 * @param anrBombThread ANR 炸弹专用线程
 * @author xubo
 * @see AnrEngineer
 * @see AnrBombThread
 */
internal class AnrBattlefield(
    private val foregroundAnrThreshold: Long,
    private val backgroundAnrThreshold: Long,
    private val anrBombThread: AnrBombThread
) {
    companion object {
        /**
         * 扫雷消息提前于炸弹爆炸的时间间隔
         * 500ms 是经验值，给主线程足够时间处理扫雷消息
         */
        const val BOMB_DEFUSING_INTERVAL_DURATION = 500L
    }

    // Anr炸弹爆炸监听 - 使用 AtomicReference 保证线程安全
    private val listenerRef = AtomicReference<AnrBombExplosionListener?>(null)

    // Anr炸弹工兵-Anr炸弹线程 - 使用 volatile + 双重检查锁定
    @Volatile
    private var bomber: AnrEngineer? = null
    private val bomberLock = Any()

    // 扫雷工兵-主线程 - 使用 volatile + 双重检查锁定
    @Volatile
    private var defuser: AnrEngineer? = null
    private val defuserLock = Any()

    // Anr战斗进行中
    private val inBattle = AtomicBoolean(false)

    // 雷区爆炸任务
    private val bombTask: () -> Unit = {
        if (inBattle.compareAndSet(true, false)) {
            FalconUtils.log(LogLevel.DEBUG) {
                "AnrBattlefield:bomb explosion"
            }
            listenerRef.get()?.onAnrBombExplosion()
            defuser?.cancel()
        }
    }

    // 扫雷任务
    private val defuseBombTask: () -> Unit = {
        if (inBattle.compareAndSet(true, false)) {
            FalconUtils.log(LogLevel.DEBUG) {
                "AnrBattlefield:defuse bomb"
            }
            bomber?.cancel()
        }
    }

    /**
     * 设置Anr炸弹爆炸监听
     *
     * @param listener Anr炸弹爆炸监听
     */
    fun setBombExplosionListener(listener: AnrBombExplosionListener) {
        listenerRef.set(listener)
    }

    /**
     * 布置Anr战斗任务
     *
     * 该方法由消息采样线程触发，采用线程安全的设计：
     * - 使用 AtomicReference 管理监听器
     * - 使用 volatile + 双重检查锁定管理工兵实例
     * - 使用 AtomicBoolean 管理战斗状态
     *
     * @param message 消息
     * @param isSamplingThread 判断当前线程是否为消息采样线程
     */
    fun deployAnrBattle(message: String, isSamplingThread: (currentThread: Thread) -> Boolean) {
        // 非消息采样线程不处理
        if (!isSamplingThread.invoke(Thread.currentThread())) {
            return
        }
        // Anr雷区未开启, 不安排战斗任务
        if (!anrBombThread.isStartBombSpace()) {
            return
        }
        // ANR战斗进行中, 不安排新的战斗任务
        if (inBattle.get()) {
            return
        }
        // Anr雷区Looper未创建, 不安排战斗任务
        val looper = anrBombThread.getBombLooper() ?: return

        try {
            // 创建Anr炸弹工兵和扫雷工兵（使用双重检查锁定）
            val bomberInstance = getOrCreateBomber(looper)
            val defuserInstance = getOrCreateDefuser()

            // 扫雷消息不安排战斗任务
            if (defuserInstance.isTaskMessage(message)) {
                return
            }

            // Anr战斗进行中, 不安排战斗任务
            if (inBattle.compareAndSet(false, true)) {
                var delay = if (isAppForeground()) foregroundAnrThreshold else backgroundAnrThreshold
                if (delay < BOMB_DEFUSING_INTERVAL_DURATION) {
                    delay = BOMB_DEFUSING_INTERVAL_DURATION
                }
                val bombDelay = delay
                val defusingDelay = bombDelay - BOMB_DEFUSING_INTERVAL_DURATION

                // 炸弹工兵雷区埋下地雷，等待爆炸
                bomberInstance.schedule(bombDelay)
                // 工兵赶往雷区排雷
                defuserInstance.schedule(defusingDelay)

                FalconUtils.log(LogLevel.DEBUG) {
                    "AnrBattlefield:deploy Anr Battle, bombDelay=$bombDelay, defusingDelay=$defusingDelay"
                }
            }
        } catch (e: Exception) {
            FalconUtils.log(LogLevel.ERROR) {
                "AnrBattlefield:deployAnrBattle error: ${e.message}"
            }
        }
    }

    /**
     * 获取或创建炸弹工兵（双重检查锁定）
     */
    private fun getOrCreateBomber(looper: Looper): AnrEngineer {
        return bomber ?: synchronized(bomberLock) {
            bomber ?: AnrEngineer.create(Handler(looper), bombTask).also { bomber = it }
        }
    }

    /**
     * 获取或创建扫雷工兵（双重检查锁定）
     */
    private fun getOrCreateDefuser(): AnrEngineer {
        return defuser ?: synchronized(defuserLock) {
            defuser ?: AnrEngineer.create(Handler(Looper.getMainLooper()), defuseBombTask).also { defuser = it }
        }
    }

    /**
     * 应用是否前台
     *
     * @return true前台, false后台，异常时默认为前台
     */
    private fun isAppForeground(): Boolean {
        return try {
            !ActivityWatcher.isAppBackground()
        } catch (e: Exception) {
            // 异常时默认为前台，使用更严格的ANR阈值
            FalconUtils.log(LogLevel.WARN) {
                "Failed to check app foreground status, defaulting to foreground: ${e.message}"
            }
            true
        }
    }

    /**
     * 取消当前雷区中的所有任务并重置状态
     */
    fun resetBattle() {
        bomber?.cancel()
        defuser?.cancel()
        inBattle.set(false)
    }
}
