package com.xenonbyte.anr.sampling

import com.github.xenonbyte.ObjectPoolStore
import com.github.xenonbyte.ObjectPoolStoreOwner
import com.xenonbyte.anr.data.MessageData
import com.xenonbyte.anr.data.SamplingStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * MessageSamplingModel 单元测试
 *
 * 测试目标：
 * 1. 消息配对（START/END）正确
 * 2. 采样数据缓存管理
 * 3. 线程安全访问
 */
class MessageSamplingModelTest {

    private lateinit var model: MessageSamplingModel
    private lateinit var mockOwner: ObjectPoolStoreOwner

    @Before
    fun setup() {
        mockOwner = object : ObjectPoolStoreOwner {
            override val store = ObjectPoolStore()
        }
        model = MessageSamplingModel(
            owner = mockOwner,
            isLowMemoryDevice = false,
            maxCacheSize = 10
        )
    }

    @Test
    fun `处理开始消息应该返回START状态的采样数据`() {
        // 使用实际的 Android Looper 日志格式
        val messageData = MessageData(
            ">>>>> Dispatching to Handler (com.example.MyHandler) {12345} 0x1",
            System.currentTimeMillis()
        )

        val result = model.handleMessageData(messageData) { true }

        assertNotNull(result)
        assertEquals(SamplingStatus.START, result!!.getStatus())
        assertTrue(result.getMessage().contains("Handler"))
    }

    @Test
    fun `非采样线程调用应该返回null`() {
        val messageData = MessageData(
            ">>>>> Dispatching to Handler (com.example.MyHandler) {12345} 0x1",
            System.currentTimeMillis()
        )

        val result = model.handleMessageData(messageData) { false }

        assertNull(result)
    }

    @Test
    fun `不相关的消息格式应该返回null`() {
        val messageData = MessageData(
            "Some random log message",
            System.currentTimeMillis()
        )

        val result = model.handleMessageData(messageData) { true }

        assertNull(result)
    }

    @Test
    fun `clear应该清空所有数据`() {
        // 添加一些数据
        val startMessage = MessageData(
            ">>>>> Dispatching to Handler (com.example.MyHandler) {12345} 0x1",
            System.currentTimeMillis()
        )
        model.handleMessageData(startMessage) { true }

        // 清空
        model.clear()

        val deque = model.getSamplingDataDeque()
        assertTrue(deque.isEmpty())
    }

    @Test
    fun `低内存设备应该正常工作`() {
        val lowMemoryModel = MessageSamplingModel(
            owner = mockOwner,
            isLowMemoryDevice = true,
            maxCacheSize = 5
        )

        val startMessage = MessageData(
            ">>>>> Dispatching to Handler (com.example.MyHandler) {12345} 0x1",
            System.currentTimeMillis()
        )

        val result = lowMemoryModel.handleMessageData(startMessage) { true }

        assertNotNull(result)
    }
}
