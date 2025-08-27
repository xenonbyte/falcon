package com.xenonbyte.anr

import androidx.annotation.WorkerThread
import com.xenonbyte.anr.data.MessageSamplingData
import java.util.Deque

/**
 * 事件监听
 *
 * @author xubo
 */
interface FalconEventListener {

    /**
     * Anr回调（非主线程）
     *
     * 注意：发生ANR时当前的消息并未执行完，当前消息采样数据是未完成状态且无执行时长
     *
     * @param currentTimestamp 发生ANR时当前时间戳
     * @param mainStackTrace 发生ANR时主线程堆栈
     * @param messageSamplingData 发生ANR时当前消息采样数据
     * @param messageSamplingDataDeque 发生ANR时消息采样数据队列（消息倒序，队列头为当前采样消息），最多返回设置的采样数据最大缓存量[FalconConfig.Builder.setMessageSamplingMaxCacheSize]
     * @param hprofData 数据转储器集生成的分析数据
     */
    @WorkerThread
    fun onAnr(
        currentTimestamp: Long,
        mainStackTrace: String,
        messageSamplingData: MessageSamplingData?,
        messageSamplingDataDeque: Deque<MessageSamplingData>,
        hprofData: String
    )

    /**
     * 慢任务回调（非主线程）
     *
     * @param currentTimestamp 发生慢任务时当前时间戳
     * @param mainStackTrace 发生慢任务时主线程堆栈
     * @param messageSamplingData 发生慢任务时当前消息采样数据
     * @param hprofData 数据转储器集生成的分析数据
     */
    @WorkerThread
    fun onSlowRunnable(
        currentTimestamp: Long,
        mainStackTrace: String,
        messageSamplingData: MessageSamplingData,
        hprofData: String
    )
}