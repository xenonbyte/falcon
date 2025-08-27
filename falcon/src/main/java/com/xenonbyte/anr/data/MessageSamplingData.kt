package com.xenonbyte.anr.data

import com.github.xenonbyte.Reusable
import com.xenonbyte.anr.FalconTimestamp
import org.json.JSONObject

/**
 * 消息采样数据
 *
 * 每个消息采样数据都对应着主线程的一个Runnable任务
 *
 * @param id 采样数据id
 * @param index 采样数据索引
 * @param message 采样数据消息
 * @param startTimestamp 采样起始时间戳
 * @param endTimestamp 采样结束时间戳
 * @param duration 采样消息执行时长
 * @param status 采样消息状态
 * @param complete 采样消息是否执行完成
 * @param stackTrace 采样消息堆栈
 * @author xubo
 */
class MessageSamplingData private constructor(
    private var id: String,
    private var index: Long,
    private var message: String,
    private var startTimestamp: Long,
    private var endTimestamp: Long,
    private var duration: Long,
    private var status: SamplingStatus,
    private var complete: Boolean,
    private var stackTrace: String
) : Reusable {

    constructor(
        index: Long, message: String, startTimestamp: Long
    ) : this(
        FalconTimestamp.currentTimeMillis().toString(),
        index,
        message,
        startTimestamp,
        -1L,
        -1L,
        SamplingStatus.START,
        false,
        ""
    )

    /**
     * 消息采样完成
     *
     * @param endTimestamp 采样结束时间戳
     */
    internal fun complete(endTimestamp: Long) {
        this.endTimestamp = endTimestamp
        this.duration = endTimestamp - startTimestamp
        this.status = SamplingStatus.END
        this.complete = true
    }

    /**
     * 采样数据重置
     *
     * @param index 采样数据索引
     * @param message 采样数据消息
     * @param startTimestamp 采样开始时间戳
     */
    internal fun rest(index: Long, message: String, startTimestamp: Long) {
        this.id = FalconTimestamp.currentTimeMillis().toString()
        this.index = index
        this.message = message
        this.startTimestamp = startTimestamp
        this.endTimestamp = -1
        this.duration = -1
        this.status = SamplingStatus.START
        this.complete = false
        this.stackTrace = ""
    }

    /**
     * 设置主线程堆栈信息
     */
    internal fun setMainStackTrace(stackTrace: String) {
        this.stackTrace = stackTrace
    }

    /**
     * 获取采样数据索引
     *
     * @return 采样数据索引
     */
    fun getIndex(): Long {
        return index
    }

    /**
     * 获取采样数据消息
     *
     * @return 采样数据消息
     */
    fun getMessage(): String {
        return message
    }

    /**
     * 获取采样开始时间戳
     *
     * @return 采样开始时间戳
     */
    fun getStartTimestamp(): Long {
        return startTimestamp
    }

    /**
     * 获取采样结束时间戳
     *
     * @return 采样结束时间戳
     */
    fun getEndTimestamp(): Long {
        return endTimestamp
    }

    /**
     * 获取采样消息执行时长
     *
     * @return 采样消息执行时长
     */
    fun getDuration(): Long {
        return duration
    }

    /**
     * 获取采样消息状态
     *
     * @return [SamplingStatus.START]采样开始, [SamplingStatus.END]采样结束
     */
    fun getStatus(): SamplingStatus {
        return status
    }

    /**
     * 采样消息是否执行完成
     * @return true 采样完成(任务执行完成), false 采样未完成(任务未执行完)
     */
    fun getComplete(): Boolean {
        return complete
    }

    /**
     * 获取采样消息对应的任务堆栈
     *
     * @return 任务堆栈
     */
    fun getStackTrace(): String {
        return stackTrace
    }

    /**
     * 转换成Json对象
     *
     * @return [JSONObject]实例
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put(KEY_ID, id)
            put(KEY_INDEX, index)
            put(KEY_MESSAGE, message)
            put(KEY_START_TIMESTAMP, startTimestamp)
            put(KEY_END_TIMESTAMP, endTimestamp)
            put(KEY_DURATION, duration)
            put(KEY_STATUS, status)
            put(KEY_COMPLETE, complete)
            put(KEY_STACK_TRACE, stackTrace)
        }
    }
}

const val KEY_ID = "id"
const val KEY_INDEX = "index"
const val KEY_MESSAGE = "message"
const val KEY_START_TIMESTAMP = "startTimestamp"
const val KEY_END_TIMESTAMP = "endTimestamp"
const val KEY_DURATION = "duration"
const val KEY_STATUS = "status"
const val KEY_COMPLETE = "complete"
const val KEY_STACK_TRACE = "stackTrace"

enum class SamplingStatus {
    START, END;
}
