package com.xenonbyte.anr.dump.internal

import android.app.ActivityManager
import android.app.Application
import android.content.Context.ACTIVITY_SERVICE
import android.os.Debug
import com.xenonbyte.anr.dump.Dumper

/**
 * 内存数据dumper
 *
 * @author xubo
 */
class MemoryDumper : Dumper<MemoryData>("MemoryDumper") {
    override fun collectData(app: Application): MemoryData {
        val appMaxMemory = Runtime.getRuntime().maxMemory()
        val appTotalMemory = Runtime.getRuntime().totalMemory()
        val appFreeMemory = Runtime.getRuntime().freeMemory()
        val appNativeHeapMaxMemory = Debug.getNativeHeapSize()
        val appNativeHeapTotalMemory = Debug.getNativeHeapAllocatedSize()
        val appNativeHeapFreeMemory = Debug.getNativeHeapFreeSize()

        var deviceMaxMemory = 0L
        var deviceFreeMemory = 0L
        var deviceThresholdMemory = 0L
        var deviceLowMemory = false
        val activityManager = app.getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        activityManager?.apply {
            try {
                val outInfo = ActivityManager.MemoryInfo()
                getMemoryInfo(outInfo)
                deviceMaxMemory = outInfo.totalMem
                deviceFreeMemory = outInfo.availMem
                deviceThresholdMemory = outInfo.threshold
                deviceLowMemory = outInfo.lowMemory
            } catch (e: Exception) {
                println("Failed to get memory info from ActivityManager, Exception: ${e.message}")
            }
        }

        val memoryData = MemoryData(
            appMaxMemory,
            appTotalMemory,
            appFreeMemory,
            appNativeHeapMaxMemory,
            appNativeHeapTotalMemory,
            appNativeHeapFreeMemory,
            deviceMaxMemory,
            deviceFreeMemory,
            deviceThresholdMemory,
            deviceLowMemory
        )
        return memoryData
    }
}