package com.xenonbyte.anr.bomb

import android.os.HandlerThread
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Anr炸弹线程
 *
 * @author xubo
 */
internal class AnrBombThread : HandlerThread(THREAD_NAME) {

    companion object {
        private const val THREAD_NAME = "com.xenonbyte:AnrBombThread"
    }

    //Anr炸弹线程是否启动
    private val mThreadStarted = AtomicBoolean(false)

    /**
     * 开启Anr雷区
     */
    fun startBombSpace() {
        if (mThreadStarted.compareAndSet(false, true)) {
            start()
        }
    }

    /**
     * 是否开启Anr雷区
     *
     * @return true雷区开启, false雷区未开启
     */
    fun isStartBombSpace(): Boolean {
        return isStarted() && isAlive
    }

    /**
     * 获取Anr雷区Looper
     *
     * @return 雷区Looper
     */
    fun getBombLooper(): Looper? {
        if (!isStartBombSpace()) {
            return null
        }
        return looper
    }

    /**
     * Anr炸弹线程是否启动
     */
    private fun isStarted(): Boolean {
        return mThreadStarted.get()
    }

}