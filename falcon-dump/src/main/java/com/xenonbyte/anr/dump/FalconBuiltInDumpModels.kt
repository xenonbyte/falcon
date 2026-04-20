package com.xenonbyte.anr.dump

import org.json.JSONArray
import org.json.JSONObject

/**
 * 内置 App Dumper 的稳定公开模型。
 */
data class FalconAppData(
    val versionName: String,
    val versionCode: Long,
    val packageName: String,
) : DumpData {
    companion object {
        const val VERSION_NAME = "version_name"
        const val VERSION_CODE = "version_code"
        const val PACKAGE_NAME = "package_name"
    }

    override fun toJson(): JSONObject {
        return JSONObject().apply {
            put(VERSION_NAME, versionName)
            put(VERSION_CODE, versionCode)
            put(PACKAGE_NAME, packageName)
        }
    }
}

/**
 * 内置 Memory Dumper 的稳定公开模型。
 */
data class FalconMemoryData(
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
        const val APP_MAX_MEMORY = "app_max_memory"
        const val APP_TOTAL_MEMORY = "app_total_memory"
        const val APP_FREE_MEMORY = "app_free_memory"
        const val APP_NATIVE_HEAP_MAX_MEMORY = "app_native_heap_max_memory"
        const val APP_NATIVE_HEAP_TOTAL_MEMORY = "app_native_heap_total_memory"
        const val APP_NATIVE_HEAP_FREE_MEMORY = "app_native_heap_free_memory"
        const val DEVICE_MAX_MEMORY = "device_max_memory"
        const val DEVICE_FREE_MEMORY = "device_free_memory"
        const val DEVICE_THRESHOLD_MEMORY = "device_threshold_memory"
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

/**
 * 内置 Thread Dumper 的稳定公开模型。
 */
data class FalconThreadData(
    val threads: List<FalconThreadInfo>,
) : DumpData {
    companion object {
        const val THREAD_INFO_LIST = "thread_info_list"
    }

    override fun toJson(): JSONObject {
        return JSONObject().apply {
            put(
                THREAD_INFO_LIST,
                JSONArray().apply {
                    threads.forEach { put(it.toJson()) }
                }
            )
        }
    }
}

/**
 * 单个线程的稳定公开模型。
 */
data class FalconThreadInfo(
    val threadId: Long,
    val threadName: String,
    val threadState: String,
    val threadPriority: Int,
    val threadAlive: Boolean,
    val threadInterrupted: Boolean,
    val threadDaemon: Boolean,
) {
    companion object {
        const val THREAD_ID = "thread_id"
        const val THREAD_NAME = "thread_name"
        const val THREAD_STATE = "thread_state"
        const val THREAD_PRIORITY = "thread_priority"
        const val THREAD_ALIVE = "thread_alive"
        const val THREAD_INTERRUPTED = "thread_interrupted"
        const val THREAD_DAEMON = "thread_daemon"
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put(THREAD_ID, threadId)
            put(THREAD_NAME, threadName)
            put(THREAD_STATE, threadState)
            put(THREAD_PRIORITY, threadPriority)
            put(THREAD_ALIVE, threadAlive)
            put(THREAD_INTERRUPTED, threadInterrupted)
            put(THREAD_DAEMON, threadDaemon)
        }
    }
}
