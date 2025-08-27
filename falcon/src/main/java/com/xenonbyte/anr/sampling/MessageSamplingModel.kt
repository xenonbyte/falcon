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
 * @param owner 对象缓存池owner
 * @param isLowMemoryDevice 对象缓存池owner
 * @param maxCacheSize 采样数据最大缓存大小
 * @author xubo
 */
internal class MessageSamplingModel(
    private val owner: ObjectPoolStoreOwner,
    private val isLowMemoryDevice: Boolean,
    private val maxCacheSize: Int,
) {
    companion object {
        private const val MESSAGE_START_HEAD: String = ">>>>> Dispatching to "
        private const val MESSAGE_END_HEAD: String = "<<<<< Finished to "
    }

    //采样数据当前索引
    private var samplingCurrentIndex = 0L

    //采样数据map
    private val samplingDataMap = SparseArray<MessageSamplingData>()

    //读写锁
    private val lock = ReentrantReadWriteLock()

    //采样数据复用池大小
    private val samplingDataPoolSize: Int = if (isLowMemoryDevice) maxCacheSize else (maxCacheSize * 1.5F).toInt()

    //采样数据复用池
    private val samplingDataPool = ObjectPoolProvider.create(owner)
        .get(MessageSamplingData::class.java, object : ObjectFactory<MessageSamplingData> {
            override fun create(vararg args: Any?): MessageSamplingData {
                val index = args[0] as Long
                val message = args[1] as String
                val timestamp = args[2] as Long
                return MessageSamplingData(index, message, timestamp);
            }

            override fun reuse(instance: MessageSamplingData, vararg args: Any?) {
                val index = args[0] as Long
                val message = args[1] as String
                val timestamp = args[2] as Long
                instance.rest(index, message, timestamp)
            }

        }, samplingDataPoolSize)

    /**
     * 处理消息数据
     * <p>
     * 该方法只能由消息采样线程触发
     *
     * @param messageData 消息数据
     * @return 采样数据
     */
    fun handleMessageData(
        messageData: MessageData,
        isSamplingThread: (currentThread: Thread) -> Boolean
    ): MessageSamplingData? {
        //非消息采样线程不处理
        if (!isSamplingThread(Thread.currentThread())) {
            return null
        }
        var samplingData: MessageSamplingData? = null
        lock.writeLock().lock()
        if (messageData.getMessage().startsWith(MESSAGE_START_HEAD)) {
            val startIndex = MESSAGE_START_HEAD.length
            val message = messageData.getMessage().substring(startIndex)
            samplingData = samplingStartMessage(message, messageData.getTimestamp())
        } else if (messageData.getMessage().startsWith(MESSAGE_END_HEAD)) {
            val startIndex = MESSAGE_END_HEAD.length
            val message = messageData.getMessage().substring(startIndex)
            samplingData = samplingEndMessage(message, messageData.getTimestamp())
        }
        lock.writeLock().unlock()
        return samplingData
    }

    /**
     * 获取当前所有消息采样数据队列(消息时间倒序，队列头为当前消息采样数据)
     * 最多返回[maxCacheSize]数量的采样数据
     *
     * @return 采样数据集合
     */
    fun getSamplingDataDeque(): Deque<MessageSamplingData> {
        lock.readLock().lock()
        val samplingSize = samplingDataMap.size()
        val currentMapIndex = (samplingCurrentIndex % maxCacheSize).toInt()
        val messageSamplingDataDeque = LinkedList<MessageSamplingData>()
        for (i in 0 until samplingSize) {
            var index = currentMapIndex - i
            if (index < 0) {
                index += maxCacheSize
            }
            val samplingData = samplingDataMap.valueAt(index)
            samplingData?.apply {
                messageSamplingDataDeque.offer(this)
            }
        }
        lock.readLock().unlock()
        return messageSamplingDataDeque
    }

    /**
     * 获取当前采样数据
     *
     * @return [MessageSamplingData]采样数据实例
     */
    fun getCurrentSamplingData(): MessageSamplingData? {
        lock.readLock().lock()
        val samplingData = getSamplingData(samplingCurrentIndex)
        lock.readLock().unlock()
        return samplingData
    }

    /**
     * 清空采样数据
     */
    fun clear() {
        lock.readLock().lock()
        samplingCurrentIndex = 0L
        samplingDataMap.clear()
        lock.readLock().unlock()
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
        if (samplingData == null) { //消息未采样过，抛弃该消息
            FalconUtils.log(LogLevel.DEBUG) {
                "MessageSamplingModel discard samplingEndMessage:\nsamplingData=null}"
            }
            return null
        }
        if (!samplingData.getMessage().contains(message)) { //消息未采样过，抛弃该消息
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
        if (samplingIndex > samplingCurrentIndex
            && samplingIndex <= samplingCurrentIndex - maxCacheSize
        ) {
            return null
        }
        val samplingIndex = samplingIndex
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

}