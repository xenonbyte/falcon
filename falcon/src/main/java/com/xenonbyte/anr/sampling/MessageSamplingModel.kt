package com.xenonbyte.anr.sampling

import android.util.SparseArray
import com.github.xenonbyte.ObjectFactory
import com.github.xenonbyte.ObjectPoolProvider
import com.github.xenonbyte.ObjectPoolStoreOwner
import com.xenonbyte.anr.FalconUtils
import com.xenonbyte.anr.LogLevel
import com.xenonbyte.anr.data.MessageData
import com.xenonbyte.anr.data.MessageSamplingData
import java.util.Deque
import java.util.LinkedList
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * 消息采样数据模型
 *
 * 负责管理主线程消息的采样数据，包括消息配对、缓存管理和数据查询。
 *
 * ## 核心功能
 *
 * 1. **消息配对**: 将主线程的 "Dispatching" 和 "Finished" 消息配对，
 *    形成完整的消息采样记录。
 *
 * 2. **缓存管理**: 使用环形缓冲区存储最近的采样数据，
 *    超出 [maxCacheSize] 的旧数据会被覆盖。
 *
 * 3. **对象池**: 复用 [MessageSamplingData] 对象，减少 GC 压力。
 *
 * ## 消息格式
 *
 * Android 主线程消息日志格式：
 * - 开始: `>>>>> Dispatching to Handler (xxx) {xxx} 0x1`
 * - 结束: `<<<<< Finished to Handler (xxx) {xxx} 0x1`
 *
 * ## 线程安全
 *
 * 使用 [ReentrantReadWriteLock] 保证线程安全：
 * - 写操作: [handleMessageData], [clear]
 * - 读操作: [getSamplingDataDeque], [getCurrentSamplingData]
 *
 * ## 低内存设备优化
 *
 * 当 [isLowMemoryDevice] 为 true 时：
 * - 对象池大小 = [maxCacheSize]
 * - 否则对象池大小 = [maxCacheSize] * 1.5
 *
 * @param owner 对象缓存池的拥有者
 * @param isLowMemoryDevice 是否为低内存设备
 * @param maxCacheSize 采样数据最大缓存大小
 * @author xubo
 * @see MessageSamplingData
 * @see MessageSamplingThread
 */
internal class MessageSamplingModel(
    private val owner: ObjectPoolStoreOwner,
    private val isLowMemoryDevice: Boolean,
    private val maxCacheSize: Int,
) {
    companion object {
        /**
         * 主线程消息开始标识
         * 格式: `>>>>> Dispatching to `
         */
        private const val MESSAGE_START_HEAD: String = ">>>>> Dispatching to "

        /**
         * 主线程消息结束标识
         * 格式: `<<<<< Finished to `
         */
        private const val MESSAGE_END_HEAD: String = "<<<<< Finished to "
    }

    // 采样数据当前索引（递增，用于环形缓冲区定位）
    private var samplingCurrentIndex = 0L

    // 采样数据存储（SparseArray + 环形索引）
    private val samplingDataMap = SparseArray<MessageSamplingData>()

    // 读写锁，保证线程安全
    private val lock = ReentrantReadWriteLock()

    // 采样数据复用池大小（根据设备内存情况调整）
    private val samplingDataPoolSize: Int = if (isLowMemoryDevice) maxCacheSize else (maxCacheSize * 1.5F).toInt()

    // 采样数据复用池
    private val samplingDataPool = ObjectPoolProvider.create(owner)
        .get(
            MessageSamplingData::class.java,
            object : ObjectFactory<MessageSamplingData> {
                override fun create(vararg args: Any?): MessageSamplingData {
                    val index = args[0] as Long
                    val message = args[1] as String
                    val timestamp = args[2] as Long
                    return MessageSamplingData(index, message, timestamp)
                }

                override fun reuse(instance: MessageSamplingData, vararg args: Any?) {
                    val index = args[0] as Long
                    val message = args[1] as String
                    val timestamp = args[2] as Long
                    instance.rest(index, message, timestamp)
                }
            },
            samplingDataPoolSize
        )

    /**
     * 处理消息数据
     *
     * 解析主线程消息日志，提取采样信息：
     * - 开始消息 (`>>>>> Dispatching`): 创建新的采样记录
     * - 结束消息 (`<<<<< Finished`): 完成对应的采样记录，计算耗时
     *
     * **线程安全**: 使用写锁保护
     *
     * **调用限制**: 只能由消息采样线程调用，其他线程调用会返回 null
     *
     * @param messageData 原始消息数据
     * @param isSamplingThread 判断当前线程是否为消息采样线程的函数
     * @return 采样数据。如果是开始消息，返回新创建的采样数据；
     *         如果是结束消息，返回完成的采样数据；
     *         其他情况返回 null
     */
    fun handleMessageData(
        messageData: MessageData,
        isSamplingThread: (currentThread: Thread) -> Boolean
    ): MessageSamplingData? {
        // 非消息采样线程不处理
        if (!isSamplingThread(Thread.currentThread())) {
            return null
        }
        var samplingData: MessageSamplingData? = null
        lock.writeLock().lock()
        try {
            if (messageData.getMessage().startsWith(MESSAGE_START_HEAD)) {
                val startIndex = MESSAGE_START_HEAD.length
                val message = messageData.getMessage().substring(startIndex)
                samplingData = samplingStartMessage(message, messageData.getTimestamp())
            } else if (messageData.getMessage().startsWith(MESSAGE_END_HEAD)) {
                val startIndex = MESSAGE_END_HEAD.length
                val message = messageData.getMessage().substring(startIndex)
                samplingData = samplingEndMessage(message, messageData.getTimestamp())
            }
        } finally {
            lock.writeLock().unlock()
        }
        return samplingData
    }

    /**
     * 获取所有消息采样数据队列
     *
     * 返回按时间倒序排列的采样数据，队列头为最新的采样数据。
     * 最多返回 [maxCacheSize] 数量的采样数据。
     *
     * **用途**: 在 ANR 发生时，提供历史消息上下文，帮助定位问题。
     *
     * **线程安全**: 使用读锁保护
     *
     * @return 采样数据队列（时间倒序，队列头为当前采样消息）
     */
    fun getSamplingDataDeque(): Deque<MessageSamplingData> {
        lock.readLock().lock()
        try {
            return buildSamplingDataDeque { it }
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * 获取采样数据队列快照，避免异步消费阶段读到对象池复用后的数据。
     */
    fun getSamplingDataDequeSnapshot(): Deque<MessageSamplingData> {
        lock.readLock().lock()
        try {
            return buildSamplingDataDeque { it.snapshot() }
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * 获取当前正在处理的消息采样数据
     *
     * 返回尚未完成（尚未收到 Finished 消息）的采样数据。
     * 在 ANR 发生时，这个数据代表导致 ANR 的消息。
     *
     * **线程安全**: 使用读锁保护
     *
     * @return 当前采样数据，如果没有正在处理的消息则返回 null
     */
    fun getCurrentSamplingData(): MessageSamplingData? {
        lock.readLock().lock()
        return try {
            getSamplingData(samplingCurrentIndex)
        } finally {
            lock.readLock().unlock()
        }
    }

    /**
     * 清空所有采样数据
     *
     * 重置索引和清空数据存储。通常在停止监控时调用。
     *
     * **线程安全**: 使用写锁保护
     */
    fun clear() {
        lock.writeLock().lock()
        try {
            samplingCurrentIndex = 0L
            samplingDataMap.clear()
        } finally {
            lock.writeLock().unlock()
        }
    }

    /**
     * 采样起始消息
     *
     * @param message 消息
     * @param timestamp 时间戳
     * @return [MessageSamplingData]采样数据实例
     */
    private fun samplingStartMessage(message: String, timestamp: Long): MessageSamplingData {
        val samplingIndex = samplingCurrentIndex
        val samplingData = createSamplingData(samplingIndex, message, timestamp)
        return samplingData
    }

    /**
     * 采样结束消息
     *
     * @param message 消息
     * @param timestamp 时间戳
     * @return [MessageSamplingData]采样数据实例
     */
    private fun samplingEndMessage(message: String, timestamp: Long): MessageSamplingData? {
        val samplingIndex = samplingCurrentIndex
        val samplingData = getSamplingData(samplingIndex)
        if (samplingData == null) { // 消息未采样过，抛弃该消息
            FalconUtils.log(LogLevel.DEBUG) {
                "MessageSamplingModel discard samplingEndMessage:\nsamplingData=null}"
            }
            return null
        }
        if (!samplingData.getMessage().contains(message)) { // 消息未采样过，抛弃该消息
            FalconUtils.log(LogLevel.DEBUG) {
                "MessageSamplingModel discard samplingEndMessage:\nindex=${samplingData.getIndex()} message=${samplingData.getMessage()}"
            }
            return null
        }
        samplingData.complete(timestamp)
        samplingCurrentIndex++
        return samplingData
    }

    /**
     * 获取指定索引的采样数据
     *
     * @param samplingIndex 采样索引
     * @return[MessageSamplingData]采样数据实例
     */
    private fun getSamplingData(samplingIndex: Long): MessageSamplingData? {
        if (samplingIndex < 0 ||
            samplingIndex > samplingCurrentIndex ||
            samplingIndex <= samplingCurrentIndex - maxCacheSize
        ) {
            return null
        }
        val mapIndex = (samplingIndex % maxCacheSize).toInt()
        val samplingData = samplingDataMap.get(mapIndex)
        return samplingData
    }

    /**
     * 创建指定索引的采样数据
     *
     * @param samplingIndex 采样索引
     * @param message 消息
     * @param timestamp 时间戳
     * @return [MessageSamplingData]采样数据实例
     */
    private fun createSamplingData(samplingIndex: Long, message: String, timestamp: Long): MessageSamplingData {
        val samplingData = samplingDataPool.obtain(samplingIndex, message, timestamp)
        val mapIndex = (samplingIndex % maxCacheSize).toInt()
        val oldSamplingData = samplingDataMap.get(mapIndex)
        samplingDataMap.put(mapIndex, samplingData)
        if (oldSamplingData != null && oldSamplingData !== samplingData) {
            samplingDataPool.recycle(oldSamplingData)
        }
        return samplingData
    }

    private fun buildSamplingDataDeque(transform: (MessageSamplingData) -> MessageSamplingData): Deque<MessageSamplingData> {
        val samplingSize = samplingDataMap.size()
        val messageSamplingDataDeque = LinkedList<MessageSamplingData>()
        if (samplingSize == 0) {
            return messageSamplingDataDeque
        }

        val latestSamplingIndex = getLatestSamplingIndex() ?: return messageSamplingDataDeque
        for (i in 0 until samplingSize) {
            val samplingData = getSamplingData(latestSamplingIndex - i) ?: continue
            messageSamplingDataDeque.offer(transform(samplingData))
        }
        return messageSamplingDataDeque
    }

    private fun getLatestSamplingIndex(): Long? {
        return when {
            getSamplingData(samplingCurrentIndex) != null -> samplingCurrentIndex
            samplingCurrentIndex > 0 -> samplingCurrentIndex - 1
            else -> null
        }
    }
}
