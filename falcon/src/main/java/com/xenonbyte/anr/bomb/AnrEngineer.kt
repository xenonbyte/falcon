package com.xenonbyte.anr.bomb

import android.os.Handler
import java.util.concurrent.atomic.AtomicInteger

/**
 * ANR 工兵 - 炸弹-扫雷算法的执行单元
 *
 * AnrEngineer 是炸弹-扫雷算法的核心执行单元，负责调度和取消延时任务。
 * 每个工兵实例绑定一个 [Handler] 和一个任务，可以：
 * - 调度延时任务（埋雷或扫雷）
 * - 取消已调度的任务
 * - 识别自己的任务消息
 *
 * ## 在炸弹-扫雷算法中的角色
 *
 * 1. **炸弹工兵 (Bomber)**: 在 AnrBombThread 上延时执行炸弹任务
 *    - 如果任务执行，说明主线程阻塞超过阈值，判定为 ANR
 *
 * 2. **扫雷工兵 (Defuser)**: 在主线程上延时执行扫雷任务
 *    - 如果任务执行，说明主线程已处理完扫雷消息，取消炸弹
 *
 * ## 线程安全
 *
 * - [schedule] 和 [cancel] 可以在不同线程调用
 * - [isTaskMessage] 是无状态的，线程安全
 *
 * ## 使用示例
 *
 * ```kotlin
 * val handler = Handler(Looper.getMainLooper())
 * val engineer = AnrEngineer.create(handler) {
 *     // 任务执行时的回调
 *     onTaskExecuted()
 * }
 *
 * // 调度延时任务
 * engineer.schedule(4000) // 4秒后执行
 *
 * // 如果需要取消
 * engineer.cancel()
 * ```
 *
 * @param handler 任务执行的 Handler
 * @param task 任务执行时的回调
 * @param taskTag 任务标签，用于日志识别
 * @author xubo
 * @see AnrBattlefield
 */
internal class AnrEngineer(
    private val handler: Handler,
    private val task: () -> Unit,
    taskTag: String
) {
    /**
     * 自定义 Runnable，重写 toString() 以便在日志中识别
     *
     * 日志格式: `FalconAnrTask_id_{唯一ID}`
     */
    private val taskRunnable: Runnable = object : Runnable {
        override fun run() {
            task.invoke()
        }

        override fun toString(): String {
            return "FalconAnrTask_$taskTag"
        }
    }

    /**
     * 调度延时任务
     *
     * 在指定的延迟时间后执行任务。如果已有相同任务被调度，
     * 不会取消之前的任务，而是会重复调度。
     *
     * **注意**: 如果需要重新调度，请先调用 [cancel]。
     *
     * @param delay 延迟时间（毫秒）
     */
    fun schedule(delay: Long) {
        handler.postDelayed(taskRunnable, delay)
    }

    /**
     * 取消已调度的任务
     *
     * 移除 Handler 中所有待执行的相同任务。
     * 如果任务已经开始执行，则无法取消。
     */
    fun cancel() {
        handler.removeCallbacks(taskRunnable)
    }

    /**
     * 判断消息是否属于此工兵的任务
     *
     * 通过检查消息字符串是否包含任务标识符来判断。
     * 用于过滤扫雷消息，避免对扫雷消息再次部署战斗。
     *
     * @param message 消息字符串
     * @return 如果消息属于此工兵的任务，返回 true
     */
    fun isTaskMessage(message: String): Boolean {
        return message.contains(taskRunnable.toString())
    }

    companion object {
        /**
         * 用于生成唯一任务 ID 的原子计数器
         */
        private val taskIdGenerator = AtomicInteger(0)

        /**
         * 创建 AnrEngineer 实例并自动分配唯一 ID
         *
         * 工厂方法，自动为每个工兵分配唯一的任务标签。
         * 标签格式: `id_{自增序号}`
         *
         * @param handler 任务执行的 Handler
         * @param task 任务执行时的回调
         * @return 新创建的 AnrEngineer 实例
         */
        fun create(handler: Handler, task: () -> Unit): AnrEngineer {
            return AnrEngineer(handler, task, "id_${taskIdGenerator.incrementAndGet()}")
        }
    }
}
