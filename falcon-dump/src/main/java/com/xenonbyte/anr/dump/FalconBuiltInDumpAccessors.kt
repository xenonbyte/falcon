package com.xenonbyte.anr.dump

import com.xenonbyte.anr.dump.internal.AppData
import com.xenonbyte.anr.dump.internal.MemoryData
import com.xenonbyte.anr.dump.internal.ThreadData
import com.xenonbyte.anr.dump.internal.ThreadInfo
import org.json.JSONObject

private const val APP_DUMPER_NAME = "AppDumper"
private const val MEMORY_DUMPER_NAME = "MemoryDumper"
private const val THREAD_DUMPER_NAME = "ThreadDumper"

/**
 * 兼容旧 API，返回 `internal` 包下的数据模型。
 *
 * 新接入建议优先使用 [stableAppData]。
 */
@Deprecated(
    message = "返回 internal 包模型，推荐使用 stableAppData()",
    replaceWith = ReplaceWith("stableAppData()")
)
fun FalconDumpPayload.appData(): AppData? = decode(APP_DUMPER_NAME, ::parseAppData)

/**
 * 兼容旧 API，返回 `internal` 包下的数据模型。
 *
 * 新接入建议优先使用 [stableMemoryData]。
 */
@Deprecated(
    message = "返回 internal 包模型，推荐使用 stableMemoryData()",
    replaceWith = ReplaceWith("stableMemoryData()")
)
fun FalconDumpPayload.memoryData(): MemoryData? = decode(MEMORY_DUMPER_NAME, ::parseMemoryData)

/**
 * 兼容旧 API，返回 `internal` 包下的数据模型。
 *
 * 新接入建议优先使用 [stableThreadData]。
 */
@Deprecated(
    message = "返回 internal 包模型，推荐使用 stableThreadData()",
    replaceWith = ReplaceWith("stableThreadData()")
)
fun FalconDumpPayload.threadData(): ThreadData? = decode(THREAD_DUMPER_NAME, ::parseThreadData)

fun FalconDumpPayload.stableAppData(): FalconAppData? {
    return decode(APP_DUMPER_NAME) { json -> parseAppData(json).toStableModel() }
}

fun FalconDumpPayload.requireStableAppData(): FalconAppData {
    return requireDecoded(APP_DUMPER_NAME) { json -> parseAppData(json).toStableModel() }
}

fun FalconDumpPayload.stableMemoryData(): FalconMemoryData? {
    return decode(MEMORY_DUMPER_NAME) { json -> parseMemoryData(json).toStableModel() }
}

fun FalconDumpPayload.requireStableMemoryData(): FalconMemoryData {
    return requireDecoded(MEMORY_DUMPER_NAME) { json -> parseMemoryData(json).toStableModel() }
}

fun FalconDumpPayload.stableThreadData(): FalconThreadData? {
    return decode(THREAD_DUMPER_NAME) { json -> parseThreadData(json).toStableModel() }
}

fun FalconDumpPayload.requireStableThreadData(): FalconThreadData {
    return requireDecoded(THREAD_DUMPER_NAME) { json -> parseThreadData(json).toStableModel() }
}

private fun parseAppData(json: JSONObject): AppData {
    return AppData(
        versionName = json.optString(AppData.VERSION_NAME),
        versionCode = json.optLong(AppData.VERSION_CODE),
        packageName = json.optString(AppData.PACKAGE_NAME)
    )
}

private fun parseMemoryData(json: JSONObject): MemoryData {
    return MemoryData(
        appMaxMemory = json.optLong(MemoryData.APP_MAX_MEMORY),
        appTotalMemory = json.optLong(MemoryData.APP_TOTAL_MEMORY),
        appFreeMemory = json.optLong(MemoryData.APP_FREE_MEMORY),
        appNativeHeapMaxMemory = json.optLong(MemoryData.APP_NATIVE_HEAP_MAX_MEMORY),
        appNativeHeapTotalMemory = json.optLong(MemoryData.APP_NATIVE_HEAP_TOTAL_MEMORY),
        appNativeHeapFreeMemory = json.optLong(MemoryData.APP_NATIVE_HEAP_FREE_MEMORY),
        deviceMaxMemory = json.optLong(MemoryData.DEVICE_MAX_MEMORY),
        deviceFreeMemory = json.optLong(MemoryData.DEVICE_FREE_MEMORY),
        deviceThresholdMemory = json.optLong(MemoryData.DEVICE_THRESHOLD_MEMORY),
        deviceLowMemory = json.optBoolean(MemoryData.DEVICE_LOW_MEMORY)
    )
}

private fun parseThreadData(json: JSONObject): ThreadData {
    val threadInfoList = json.optJSONArray(ThreadData.THREAD_INFO_LIST)
    val list = buildList(threadInfoList?.length() ?: 0) {
        if (threadInfoList == null) {
            return@buildList
        }
        for (index in 0 until threadInfoList.length()) {
            val threadJson = threadInfoList.optJSONObject(index) ?: continue
            add(
                ThreadInfo(
                    threadId = threadJson.optLong(ThreadInfo.THREAD_ID),
                    threadName = threadJson.optString(ThreadInfo.THREAD_NAME),
                    threadState = threadJson.optString(ThreadInfo.THREAD_STATE),
                    threadPriority = threadJson.optInt(ThreadInfo.THREAD_PRIORITY),
                    threadAlive = threadJson.optBoolean(ThreadInfo.THREAD_ALIVE),
                    threadInterrupted = threadJson.optBoolean(ThreadInfo.THREAD_INTERRUPTED),
                    threadDaemon = threadJson.optBoolean(ThreadInfo.THREAD_DAEMON)
                )
            )
        }
    }
    return ThreadData(list)
}
