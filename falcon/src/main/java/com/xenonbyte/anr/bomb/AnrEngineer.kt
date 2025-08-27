package com.xenonbyte.anr.bomb

import android.os.Handler

/**
 * Anr工兵
 *
 * @param handler 处理器
 * @param task 任务
 * @author xubo
 */
internal class AnrEngineer(
    private val handler: Handler,
    private val task: () -> Unit,
) {
    private val taskRunnable: Runnable = Runnable { task.invoke() }

    /**
     * 调度任务
     *
     * @param delay 延迟时长
     */
    fun schedule(delay: Long) {
        handler.postDelayed(taskRunnable, delay)
    }

    /**
     * 取消已调度的任务
     */
    fun cancel() {
        handler.removeCallbacks(taskRunnable)
    }

    /**
     * 是否任务消息
     */
    fun isTaskMessage(message: String): Boolean {
        return message.contains(taskRunnable.toString())
    }
}