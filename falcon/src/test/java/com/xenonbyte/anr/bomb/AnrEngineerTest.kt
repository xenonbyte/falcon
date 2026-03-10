package com.xenonbyte.anr.bomb

import android.os.Handler
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * AnrEngineer 单元测试
 *
 * 测试目标：
 * 1. 任务调度正常工作
 * 2. 取消任务成功阻止执行
 * 3. 消息类型识别正确
 * 4. 工厂方法创建唯一实例
 */
class AnrEngineerTest {

    private lateinit var mockHandler: Handler

    @Before
    fun setup() {
        mockHandler = mockk(relaxed = true)
    }

    @Test
    fun `schedule应该调用handler的postDelayed`() {
        var taskExecuted = false
        val engineer = AnrEngineer.create(mockHandler) {
            taskExecuted = true
        }

        engineer.schedule(1000L)

        verify { mockHandler.postDelayed(any(), 1000L) }
    }

    @Test
    fun `cancel应该调用handler的removeCallbacks`() {
        val engineer = AnrEngineer.create(mockHandler) {}

        engineer.schedule(1000L)
        engineer.cancel()

        verify { mockHandler.removeCallbacks(any()) }
    }

    @Test
    fun `isTaskMessage应该正确识别任务消息`() {
        val engineer = AnrEngineer.create(mockHandler) {}
        val runnableSlot = slot<Runnable>()

        engineer.schedule(100L)

        verify { mockHandler.postDelayed(capture(runnableSlot), 100L) }

        val messageContainingTaskId = "Some prefix ${runnableSlot.captured} suffix"
        assertTrue(engineer.isTaskMessage(messageContainingTaskId))
        assertFalse(engineer.isTaskMessage("Some other message"))
    }

    @Test
    fun `isTaskMessage应该拒绝不相关的消息`() {
        val engineer = AnrEngineer.create(mockHandler) {}

        assertFalse(engineer.isTaskMessage("completely unrelated message"))
        assertFalse(engineer.isTaskMessage("Handler.dispatchMessage"))
        assertFalse(engineer.isTaskMessage(""))
    }

    @Test
    fun `create工厂方法应该创建不同的实例`() {
        val engineer1 = AnrEngineer.create(mockHandler) {}
        val engineer2 = AnrEngineer.create(mockHandler) {}

        assertNotSame(engineer1, engineer2)
    }

    @Test
    fun `多个调度应该各自独立`() {
        var counter = 0
        val engineer1 = AnrEngineer.create(mockHandler) { counter++ }
        val engineer2 = AnrEngineer.create(mockHandler) { counter += 10 }

        engineer1.schedule(100L)
        engineer2.schedule(200L)

        verify(exactly = 1) { mockHandler.postDelayed(any(), 100L) }
        verify(exactly = 1) { mockHandler.postDelayed(any(), 200L) }
    }

    @Test
    fun `取消后再调度应该可以重新调度`() {
        val engineer = AnrEngineer.create(mockHandler) {}

        engineer.schedule(1000L)
        engineer.cancel()
        engineer.schedule(2000L)

        verify(exactly = 2) { mockHandler.postDelayed(any(), any()) }
        verify(exactly = 1) { mockHandler.removeCallbacks(any()) }
    }

    @Test
    fun `任务应该能够执行`() {
        val executed = AtomicBoolean(false)
        val engineer = AnrEngineer.create(mockHandler) {
            executed.set(true)
        }

        // 手动触发任务执行（模拟Handler行为）
        engineer.schedule(100L)

        // 验证postDelayed被调用，任务Runnable被正确传递
        verify {
            mockHandler.postDelayed(
                match { runnable ->
                    // 执行runnable来验证任务逻辑
                    runnable.run()
                    executed.get()
                },
                100L
            )
        }
    }
}
