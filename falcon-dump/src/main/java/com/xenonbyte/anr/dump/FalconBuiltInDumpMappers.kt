package com.xenonbyte.anr.dump

import com.xenonbyte.anr.dump.internal.AppData
import com.xenonbyte.anr.dump.internal.MemoryData
import com.xenonbyte.anr.dump.internal.ThreadData
import com.xenonbyte.anr.dump.internal.ThreadInfo

fun AppData.toStableModel(): FalconAppData {
    return FalconAppData(
        versionName = versionName,
        versionCode = versionCode,
        packageName = packageName
    )
}

fun MemoryData.toStableModel(): FalconMemoryData {
    return FalconMemoryData(
        appMaxMemory = appMaxMemory,
        appTotalMemory = appTotalMemory,
        appFreeMemory = appFreeMemory,
        appNativeHeapMaxMemory = appNativeHeapMaxMemory,
        appNativeHeapTotalMemory = appNativeHeapTotalMemory,
        appNativeHeapFreeMemory = appNativeHeapFreeMemory,
        deviceMaxMemory = deviceMaxMemory,
        deviceFreeMemory = deviceFreeMemory,
        deviceThresholdMemory = deviceThresholdMemory,
        deviceLowMemory = deviceLowMemory
    )
}

fun ThreadData.toStableModel(): FalconThreadData {
    return FalconThreadData(
        threads = list.map { it.toStableModel() }
    )
}

fun ThreadInfo.toStableModel(): FalconThreadInfo {
    return FalconThreadInfo(
        threadId = threadId,
        threadName = threadName,
        threadState = threadState,
        threadPriority = threadPriority,
        threadAlive = threadAlive,
        threadInterrupted = threadInterrupted,
        threadDaemon = threadDaemon
    )
}
