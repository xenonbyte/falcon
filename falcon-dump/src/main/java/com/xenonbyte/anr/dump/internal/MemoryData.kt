package com.xenonbyte.anr.dump.internal

import com.xenonbyte.anr.dump.DumpData
import org.json.JSONObject

/**
 * 内存数据结构
 *
 * @param appMaxMemory
 * @param appTotalMemory
 * @param appFreeMemory
 * @param appNativeHeapMaxMemory
 * @param appNativeHeapTotalMemory
 * @param appNativeHeapFreeMemory
 * @param deviceMaxMemory 设备物理内存
 * @param deviceFreeMemory 设备空闲内存
 * @param deviceThresholdMemory 设备低内存阈值
 * @param deviceLowMemory 设备是否低内存
 * @author xubo
 */
class MemoryData(
    val appMaxMemory: Long,
    val appTotalMemory: Long,
    val appFreeMemory: Long,
    val appNativeHeapMaxMemory: Long,
    val appNativeHeapTotalMemory: Long,
    val appNativeHeapFreeMemory: Long,
    val deviceMaxMemory: Long,
    val deviceFreeMemory: Long,
    val deviceThresholdMemory: Long,
    val deviceLowMemory: Boolean,
) : DumpData {

    companion object {
        //当前应用Java堆最大可用内存
        const val APP_MAX_MEMORY = "app_max_memory"

        //当前应用Java堆已分配内存
        const val APP_TOTAL_MEMORY = "app_total_memory"

        //当前应用Java堆空闲内存
        const val APP_FREE_MEMORY = "app_free_memory"

        //当前应用native堆最大可用内存
        const val APP_NATIVE_HEAP_MAX_MEMORY = "app_native_heap_max_memory"

        //当前应用native堆已分配内存
        const val APP_NATIVE_HEAP_TOTAL_MEMORY = "app_native_heap_total_memory"

        //当前应用native堆空闲内存
        const val APP_NATIVE_HEAP_FREE_MEMORY = "app_native_heap_free_memory"

        //设备最大内存
        const val DEVICE_MAX_MEMORY = "device_max_memory"

        //设备最大可用内存
        const val DEVICE_FREE_MEMORY = "device_free_memory"

        //设备内存不足阈值
        const val DEVICE_THRESHOLD_MEMORY = "device_threshold_memory"

        //设备是否内存不足
        const val DEVICE_LOW_MEMORY = "device_low_memory"
    }

    override fun toJson(): JSONObject {
        return JSONObject().apply {
            put(APP_MAX_MEMORY, appMaxMemory)
            put(APP_TOTAL_MEMORY, appTotalMemory)
            put(APP_FREE_MEMORY, appFreeMemory)
            put(APP_NATIVE_HEAP_MAX_MEMORY, appNativeHeapMaxMemory)
            put(APP_NATIVE_HEAP_TOTAL_MEMORY, appNativeHeapTotalMemory)
            put(APP_NATIVE_HEAP_FREE_MEMORY, appNativeHeapFreeMemory)
            put(DEVICE_MAX_MEMORY, deviceMaxMemory)
            put(DEVICE_FREE_MEMORY, deviceFreeMemory)
            put(DEVICE_THRESHOLD_MEMORY, deviceThresholdMemory)
            put(DEVICE_LOW_MEMORY, deviceLowMemory)
        }
    }
}