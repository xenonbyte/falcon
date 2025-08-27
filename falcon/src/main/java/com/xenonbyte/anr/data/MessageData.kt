package com.xenonbyte.anr.data

import com.github.xenonbyte.Reusable

/**
 * 消息数据
 *
 * @param message 消息
 * @param timestamp 时间戳
 * @author xubo
 */
internal class MessageData(
    private var message: String,
    private var timestamp: Long
) : Reusable {

    /**
     * 消息数据重置
     */
    fun rest(content: String, timestamp: Long) {
        this.message = content
        this.timestamp = timestamp
    }

    /**
     * 获取消息
     */
    fun getMessage(): String {
        return message
    }

    /**
     * 获取时间戳
     */
    fun getTimestamp(): Long {
        return timestamp
    }
}
