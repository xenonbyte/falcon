package com.xenonbyte.anr.dump.internal

import android.app.Application
import android.os.Looper
import com.xenonbyte.anr.dump.Dumper

/**
 * 线程数据dumper
 *
 * @author xubo
 */
class ThreadDumper : Dumper<ThreadData>("ThreadDumper") {

    override fun collectData(app: Application): ThreadData {
        var mainThreadInfo: ThreadInfo? = null
        val threadInfoList = ArrayList<ThreadInfo>()
        Thread.getAllStackTraces().keys.forEach {
            val threadId = it.id
            val threadName = it.name
            val threadState = it.state.name
            val threadPriority = it.priority
            val threadAlive = it.isAlive
            val threadInterrupted = it.isInterrupted
            val threadDaemon = it.isDaemon
            val threadInfo = ThreadInfo(
                threadId,
                threadName,
                threadState,
                threadPriority,
                threadAlive,
                threadInterrupted,
                threadDaemon
            )
            if (it == Looper.getMainLooper().thread) {
                mainThreadInfo = threadInfo
            } else {
                threadInfoList.add(threadInfo)
            }
        }
        //主线程信息放最前面
        mainThreadInfo?.let {
            threadInfoList.add(0, it)
        }
        return ThreadData(threadInfoList)
    }

}