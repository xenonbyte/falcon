package com.xenonbyte.anr.bomb

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AnrBombThread 单元测试
 *
 * 测试目标：
 * 1. 线程启动正常工作
 * 2. 重复启动不会创建多个线程
 * 3. Looper 正确创建
 * 4. 线程状态检查正确
 *
 * 使用 Robolectric 来模拟 Android 环境
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AnrBombThreadTest {

    private lateinit var bombThread: AnrBombThread

    @Before
    fun setup() {
        bombThread = AnrBombThread()
    }

    @After
    fun tearDown() {
        // 确保线程被清理
        try {
            if (bombThread.isAlive) {
                bombThread.quitSafely()
                bombThread.join(1000)
            }
        } catch (e: Exception) {
            // 忽略清理异常
        }
    }

    @Test
    fun `初始状态应该是未启动`() {
        // 未启动前，isStartBombSpace 应该返回 false
        // 因为 isAlive 为 false
        assertFalse(bombThread.isStartBombSpace())
    }

    @Test
    fun `startBombSpace后应该处于启动状态`() {
        bombThread.startBombSpace()

        // 等待线程启动
        bombThread.join(2000)

        // 验证线程已启动
        assertTrue("线程应该已经启动", bombThread.isStartBombSpace())
    }

    @Test
    fun `重复startBombSpace应该只启动一次`() {
        bombThread.startBombSpace()
        bombThread.join(1000)

        // 记录第一次启动后的状态
        val firstState = bombThread.isStartBombSpace()

        // 再次调用启动 - 应该不会重复启动
        bombThread.startBombSpace()
        Thread.sleep(100)

        // 状态应该保持不变
        assertEquals("重复启动不应该改变状态", firstState, bombThread.isStartBombSpace())
    }

    @Test
    fun `getBombLooper未启动时应该返回null`() {
        val looper = bombThread.getBombLooper()
        assertNull("未启动时 Looper 应该为 null", looper)
    }

    @Test
    fun `getBombLooper启动后应该返回非null`() {
        bombThread.startBombSpace()
        bombThread.join(2000)

        val looper = bombThread.getBombLooper()
        assertNotNull("启动后 Looper 不应该为 null", looper)
    }

    @Test
    fun `quitSafely后isStartBombSpace应该返回false`() {
        bombThread.startBombSpace()
        bombThread.join(1000)

        assertTrue("启动后应该返回 true", bombThread.isStartBombSpace())

        bombThread.quitSafely()
        bombThread.join(1000)

        // 线程停止后，isAlive 会变为 false
        assertFalse("停止后 isAlive 应该为 false", bombThread.isAlive)
    }
}
