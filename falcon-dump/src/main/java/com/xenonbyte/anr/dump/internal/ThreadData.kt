package com.xenonbyte.anr.dump.internal

import com.xenonbyte.anr.dump.DumpData
import org.json.JSONArray
import org.json.JSONObject

/**
 * 线程数据结构
 *
 * @param list 线程信息列表
 * @author xubo
 */
class ThreadData(
    val list: List<ThreadInfo>,
) : DumpData {
    companion object {
        //线程信息列表
        const val THREAD_INFO_LIST = "thread_info_list"
    }

    override fun toJson(): JSONObject {
        return JSONObject().apply {
            val threadInfoList = JSONArray().apply {
                list.forEach {
                    put(it.toJson())
                }
            }
            put(THREAD_INFO_LIST, threadInfoList)
        }
    }

}

/**
 * 线程信息
 *
 * @param threadId 线程id
 * @param threadName 线程名
 * @param threadState 线程状态
 * @param threadPriority 线程优先级
 * @param threadAlive 线程是否存活
 * @param threadInterrupted 线程是否中断
 * @param threadDaemon 是否守护线程
 * @author xubo
 */
class ThreadInfo(
    val threadId: Long,
    val threadName: String,
    val threadState: String,
    val threadPriority: Int,
    val threadAlive: Boolean,
    val threadInterrupted: Boolean,
    val threadDaemon: Boolean,
) {

    companion object {
        //线程id
        const val THREAD_ID = "thread_id"

        //线程名
        const val THREAD_NAME = "thread_name"

        //线程状态
        const val THREAD_STATE = "thread_state"

        //线程优先级
        const val THREAD_PRIORITY = "thread_priority"

        //线程是否存活
        const val THREAD_ALIVE = "thread_alive"

        //线程是否中断
        const val THREAD_INTERRUPTED = "thread_interrupted"

        //线程是否是守护线程
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