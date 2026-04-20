package com.xenonbyte.anr

import com.xenonbyte.anr.data.MessageSamplingData
import com.xenonbyte.anr.dump.FalconDumpPayload
import java.util.Deque

/**
 * Falcon 生命周期状态。
 */
enum class FalconLifecycleState {
    NOT_INITIALIZED,
    STOPPED,
    MONITORING,
}

/**
 * Falcon 对外健康状态。
 */
enum class FalconHealthState {
    NOT_INITIALIZED,
    HEALTHY,
    DEGRADED,
}

/**
 * 提供空实现，方便按需覆写回调。
 */
open class FalconEventAdapter : FalconEventListener {
    override fun onAnr(
        currentTimestamp: Long,
        mainStackTrace: String,
        messageSamplingData: MessageSamplingData?,
        messageSamplingDataDeque: Deque<MessageSamplingData>,
        hprofData: String
    ) {
    }

    override fun onSlowRunnable(
        currentTimestamp: Long,
        mainStackTrace: String,
        messageSamplingData: MessageSamplingData,
        hprofData: String
    ) {
    }
}

/**
 * 在不改变旧回调签名的前提下，直接向上层暴露结构化的 [FalconDumpPayload]。
 *
 * 适合希望避免手动 `FalconDumpPayload.parse(hprofData)` 的接入方。
 */
open class FalconDumpPayloadEventAdapter : FalconEventAdapter() {

    final override fun onAnr(
        currentTimestamp: Long,
        mainStackTrace: String,
        messageSamplingData: MessageSamplingData?,
        messageSamplingDataDeque: Deque<MessageSamplingData>,
        hprofData: String
    ) {
        onAnr(
            currentTimestamp = currentTimestamp,
            mainStackTrace = mainStackTrace,
            messageSamplingData = messageSamplingData,
            messageSamplingDataDeque = messageSamplingDataDeque,
            dumpPayload = FalconDumpPayload.parse(hprofData)
        )
    }

    final override fun onSlowRunnable(
        currentTimestamp: Long,
        mainStackTrace: String,
        messageSamplingData: MessageSamplingData,
        hprofData: String
    ) {
        onSlowRunnable(
            currentTimestamp = currentTimestamp,
            mainStackTrace = mainStackTrace,
            messageSamplingData = messageSamplingData,
            dumpPayload = FalconDumpPayload.parse(hprofData)
        )
    }

    open fun onAnr(
        currentTimestamp: Long,
        mainStackTrace: String,
        messageSamplingData: MessageSamplingData?,
        messageSamplingDataDeque: Deque<MessageSamplingData>,
        dumpPayload: FalconDumpPayload
    ) {
    }

    open fun onSlowRunnable(
        currentTimestamp: Long,
        mainStackTrace: String,
        messageSamplingData: MessageSamplingData,
        dumpPayload: FalconDumpPayload
    ) {
    }
}
