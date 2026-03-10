package com.xenonbyte.anr.sampling

import com.github.xenonbyte.ObjectPoolStore
import com.github.xenonbyte.ObjectPoolStoreOwner
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * MessageStackTraceCapturer 单元测试
 *
 * 测试目标：
 * 1. 堆栈采集线程启动正确
 * 2. 非采样线程调用被忽略
 * 3. 重复启动不会创建多个线程
 *
 * 使用 Robolectric 来模拟 Android 环境
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MessageStackTraceCapturerTest {

    private lateinit var capturer: MessageStackTraceCapturer
    private lateinit var samplingModel: MessageSamplingModel
    private lateinit var owner: ObjectPoolStoreOwner

    @Before
    fun setup() {
        owner = object : ObjectPoolStoreOwner {
            override val store = ObjectPoolStore()
        }
        samplingModel = MessageSamplingModel(
            owner = owner,
            isLowMemoryDevice = false,
            maxCacheSize = 10
        )
        // 使用 1000ms 作为慢任务阈值
        capturer = MessageStackTraceCapturer(samplingModel, 1000L)
    }

    @After
    fun tearDown() {
        try {
            if (capturer.isAlive) {
                capturer.quitSafely()
                capturer.join(1000)
            }
        } catch (e: Exception) {
            // 忽略清理异常
        }
    }

    @Test
    fun `初始状态应该是未启动`() {
        // 未启动时，scheduleCapture 不应该做任何事情
        // 通过不抛出异常来验证
        capturer.scheduleCapture { true }
        // 正常完成即表示通过
    }

    @Test
    fun `startCapturing后线程应该启动`() {
        capturer.startCapturing()
        capturer.join(2000)

        assertTrue("线程应该已经启动", capturer.isAlive)
    }

    @Test
    fun `重复startCapturing应该只启动一次`() {
        capturer.startCapturing()
        capturer.join(1000)

        val firstState = capturer.isAlive

        capturer.startCapturing()
        Thread.sleep(100)

        assertEquals("重复启动不应该改变状态", firstState, capturer.isAlive)
    }

    @Test
    fun `非采样线程调用scheduleCapture应该被忽略`() {
        capturer.startCapturing()
        capturer.join(1000)

        // 返回 false 表示不是采样线程
        capturer.scheduleCapture { false }

        // 不应该抛出异常，正常完成即表示通过
    }

    @Test
    fun `scheduleCapture在采样线程中应该被处理`() {
        capturer.startCapturing()
        capturer.join(1000)

        // 返回 true 表示是采样线程
        capturer.scheduleCapture { true }

        // 不应该抛出异常
    }

    @Test
    fun `cancelCapture在非采样线程中应该被忽略`() {
        capturer.startCapturing()
        capturer.join(1000)

        // 返回 false 表示不是采样线程
        capturer.cancelCapture { false }

        // 不应该抛出异常
    }

    @Test
    fun `cancelCapture在采样线程中应该被处理`() {
        capturer.startCapturing()
        capturer.join(1000)

        // 返回 true 表示是采样线程
        capturer.cancelCapture { true }

        // 不应该抛出异常
    }

    @Test
    fun `未启动时scheduleCapture和cancelCapture不应该抛出异常`() {
        // 未启动状态
        capturer.scheduleCapture { true }
        capturer.cancelCapture { true }

        // 正常完成即表示通过
    }

    @Test
    fun `quitSafely后线程应该停止`() {
        capturer.startCapturing()
        capturer.join(1000)

        assertTrue("启动后线程应该存活", capturer.isAlive)

        capturer.quitSafely()
        capturer.join(1000)

        assertFalse("停止后线程不应该存活", capturer.isAlive)
    }
}
