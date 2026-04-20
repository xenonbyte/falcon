package com.xenonbyte.anr.dump

import android.app.ActivityManager
import android.app.Application
import android.content.Context.ACTIVITY_SERVICE
import android.os.Build
import java.io.File

/**
 * 设备数据dumper
 *
 * @author xubo
 */
class DeviceDumper private constructor(
    private val cpuSystemDir: File? = null,
    private val readText: ((File) -> String)? = null
) : Dumper<DeviceData>("DeviceDumper") {
    companion object {
        private const val CPU_SYSTEM_DIR_PATH = "/sys/devices/system/cpu"
        private val CPU_DIR_PATTERN = Regex("cpu\\d+")

        internal fun createForTest(
            cpuSystemDir: File,
            readText: (File) -> String
        ): DeviceDumper {
            return DeviceDumper(
                cpuSystemDir = cpuSystemDir,
                readText = readText
            )
        }
    }

    constructor() : this(null, null)

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
        activityManager?.let { manager ->
            runCatching {
                ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
            }.getOrNull()?.let { outInfo ->
                totalMemory = outInfo.totalMem
                freeMemory = outInfo.availMem
            }
        }

        val cpuApi = Build.SUPPORTED_ABIS.joinToString(",")
        val cpuCore = Runtime.getRuntime().availableProcessors()
        val cpuMinFreq = readCpuFrequency("cpuinfo_min_freq")
        val cpuMaxFreq = readCpuFrequency("cpuinfo_max_freq")
        val cpuCurFreq = readCpuFrequency("scaling_cur_freq")

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

    private fun readCpuFrequency(fileName: String): String {
        return getCpuDirectories().asSequence()
            .map { cpuDir -> File(cpuDir, "cpufreq/$fileName") }
            .map { readTrimmedOrEmpty(it) }
            .firstOrNull { it.isNotEmpty() }
            ?: ""
    }

    private fun getCpuDirectories(): List<File> {
        return resolveCpuSystemDir()
            .listFiles()
            ?.filter { it.isDirectory && CPU_DIR_PATTERN.matches(it.name) }
            ?.sortedBy { it.name.removePrefix("cpu").toIntOrNull() ?: Int.MAX_VALUE }
            .orEmpty()
    }

    private fun resolveCpuSystemDir(): File {
        return cpuSystemDir ?: File(CPU_SYSTEM_DIR_PATH)
    }

    private fun readTrimmedOrEmpty(file: File): String {
        val readTextProvider = readText
        return runCatching {
            if (readTextProvider != null) {
                readTextProvider.invoke(file)
            } else {
                file.readText().trim()
            }
        }.getOrDefault("")
    }
}
