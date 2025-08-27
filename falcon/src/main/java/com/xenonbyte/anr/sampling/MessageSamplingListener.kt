package com.xenonbyte.anr.sampling

import androidx.annotation.WorkerThread
import com.xenonbyte.anr.data.MessageSamplingData

/**
 * 消息采样监听
 *
 * @author xubo
 */
internal interface MessageSamplingListener {

    /**
     * 消息采样
     *
     * @param data 消息采样数据
     */
    @WorkerThread
    fun onSampling(data: MessageSamplingData)
}