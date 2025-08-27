package com.xenonbyte.anr.bomb

import android.os.Handler
import android.os.Looper
import com.xenonbyte.activitywatcher.ActivityWatcher
import com.xenonbyte.anr.FalconUtils
import com.xenonbyte.anr.LogLevel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Anr模拟
 *
 * @param foregroundAnrThreshold Anr后台触发阈值
 * @param backgroundAnrThreshold Anr前台触发阈值
 * @param anrBombThread Anr炸弹线程
 * @author xubo
 */
internal class AnrBattlefield(
    private val foregroundAnrThreshold: Long,
    private val backgroundAnrThreshold: Long,
    private val anrBombThread: AnrBombThread
) {
    companion object {
        const val BOMB_DEFUSING_INTERVAL_DURATION = 500L
    }

    //Anr炸弹爆炸监听
    private var listener: AnrBombExplosionListener? = null

    //Anr炸弹工兵-Anr炸弹线程
    private var bomber: AnrEngineer? = null

    //扫雷工兵-主线程
    private var defuser: AnrEngineer? = null

    //Anr战斗进行中
    private val inBattle = AtomicBoolean(false)

    //雷区爆炸任务
    private val bombTask: () -> Unit = {
        if (inBattle.compareAndSet(true, false)) {
            FalconUtils.log(LogLevel.DEBUG) {
                "AnrBattlefield:bomb explosion"
            }
            listener?.onAnrBombExplosion()
            defuser?.cancel()
        }
    }

    //扫雷任务
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
        this.listener = listener
    }

    /**
     * 布置Anr战斗任务
     * <p>
     * 该方法由消息采样线程触发，无需考虑并发情况
     *
     * @param message 消息
     * @param isSamplingThread 是否消息采样线程
     */
    fun deployAnrBattle(message: String, isSamplingThread: (currentThread: Thread) -> Boolean) {
        //非消息采样线程不处理
        if (!isSamplingThread.invoke(Thread.currentThread())) {
            return
        }
        //Anr雷区未开启, 不安排战斗任务
        if (!anrBombThread.isStartBombSpace()) {
            return
        }
        //Anr雷区Looper未创建, 不安排战斗任务
        val loop = anrBombThread.getBombLooper() ?: return
        //创建Anr炸弹工兵和扫雷工兵
        if (bomber == null) {
            bomber = AnrEngineer(Handler(loop), bombTask)
        }
        if (defuser == null) {
            defuser = AnrEngineer(Handler(Looper.getMainLooper()), defuseBombTask)
        }
        //扫雷消息不安排战斗任务
        if (defuser?.isTaskMessage(message) == true) {
            return
        }
        //Anr战斗进行中, 不安排战斗任务
        if (inBattle.compareAndSet(false, true)) {
            var delay = if (isAppForeground()) foregroundAnrThreshold else backgroundAnrThreshold
            if (delay < BOMB_DEFUSING_INTERVAL_DURATION) {
                delay = BOMB_DEFUSING_INTERVAL_DURATION
            }
            val bombDelay = delay
            val defusingDelay = bombDelay - BOMB_DEFUSING_INTERVAL_DURATION
            //炸弹工兵雷区埋下地雷，等待爆炸
            bomber?.schedule(bombDelay)
            //工兵赶往雷区排雷
            defuser?.schedule(defusingDelay)
            FalconUtils.log(LogLevel.DEBUG) {
                "AnrBattlefield:deploy Anr Battle"
            }
        }
    }

    /**
     * 应用是否前台
     *
     * @return true前台, false后台
     */
    private fun isAppForeground(): Boolean {
        return !ActivityWatcher.isAppBackground()
    }

}