package com.xenonbyte.anr

import android.os.SystemClock

/**
 * 时间戳计算
 *
 * 防止修改系统时间导致时间戳错误
 *
 * @author xubo
 */
internal object FalconTimestamp {
    //记录时间戳
    private val recordTimeMillis = System.currentTimeMillis()

    //记录开机时间
    private val recordClockTimeMillis = SystemClock.elapsedRealtime()

    /**
     * 获取当前时间戳
     *
     * @return 当前时间戳
     */
    fun currentTimeMillis(): Long {
        val diff = SystemClock.elapsedRealtime() - recordClockTimeMillis
        return recordTimeMillis + diff
    }
}