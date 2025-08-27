package com.xenonbyte.anr.dump

import android.app.ActivityManager
import android.app.Application
import android.content.Context.ACTIVITY_SERVICE
import android.os.Build
import android.text.TextUtils
import java.io.File

/**
 * 设备数据dumper
 *
 * @author xubo
 */
class DeviceDumper : Dumper<DeviceData>("DeviceDumper") {

    override fun collectData(app: Application): DeviceData {
        val brand = Build.BRAND
        val board = Build.BOARD
        val hardware = Build.HARDWARE
        val product = Build.PRODUCT
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val display = Build.DISPLAY
        val version = Build.VERSION.RELEASE

        var totalMemory = 0L
        var freeMemory = 0L
        val activityManager = app.getSystemService(ACTIVITY_SERVICE) as? ActivityManager
        activityManager?.apply {
            try {
                val outInfo = ActivityManager.MemoryInfo()
                getMemoryInfo(outInfo)
                totalMemory = outInfo.totalMem
                freeMemory = outInfo.availMem
            } catch (e: Exception) {

            }
        }

        val cpuMinFreqPath = "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq"
        val cpuMaxFreqPath = "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"
        val cpuCurFreqPath = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
        val cpuApi = TextUtils.join(",", Build.SUPPORTED_ABIS)
        val cpuCore = Runtime.getRuntime().availableProcessors()
        val cpuMinFreq = try {
            File(cpuMinFreqPath).readText().trim()
        } catch (e: Exception) {
            ""
        }
        val cpuMaxFreq = try {
            File(cpuMaxFreqPath).readText().trim()
        } catch (e: Exception) {
            ""
        }
        val cpuCurFreq = try {
            File(cpuCurFreqPath).readText().trim()
        } catch (e: Exception) {
            ""
        }

        return DeviceData(
            brand,
            board,
            hardware,
            product,
            manufacturer,
            model,
            display,
            version,
            totalMemory,
            freeMemory,
            cpuApi,
            cpuCore,
            cpuMinFreq,
            cpuMaxFreq,
            cpuCurFreq
        )
    }
}