package com.xenonbyte.anr.sampling

import com.github.xenonbyte.ObjectPoolStore
import com.github.xenonbyte.ObjectPoolStoreOwner
import com.xenonbyte.anr.data.SamplingStatus
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MessageSamplingThreadTest {

    private lateinit var owner: ObjectPoolStoreOwner
    private lateinit var model: MessageSamplingModel
    private var samplingThread: MessageSamplingThread? = null

    @Before
    fun setup() {
        owner = object : ObjectPoolStoreOwner {
            override val store = ObjectPoolStore()
        }
        model = MessageSamplingModel(
            owner = owner,
            isLowMemoryDevice = false,
            maxCacheSize = 10
        )
    }

    @After
    fun tearDown() {
        samplingThread?.stopSampling()
        samplingThread?.join(1000)
    }

    @Test
    fun `同一次dispatch被采样时应该同时收到START和END`() {
        val statuses = CopyOnWriteArrayList<SamplingStatus>()
        val latch = CountDownLatch(2)

        samplingThread = MessageSamplingThread(model, 0.5f) { 0.1f }.also { thread ->
            thread.setSamplingListener(object : MessageSamplingListener {
                override fun onSampling(data: com.xenonbyte.anr.data.MessageSamplingData) {
                    statuses.add(data.getStatus())
                    latch.countDown()
                }
            })
            thread.startSampling()
        }

        samplingThread?.dispatchMessage(">>>>> Dispatching to Handler (test) {1} 0x1")
        samplingThread?.dispatchMessage("<<<<< Finished to Handler (test) {1} 0x1")

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(listOf(SamplingStatus.START, SamplingStatus.END), statuses)
    }

    @Test
    fun `同一次dispatch未被采样时应该同时跳过START和END`() {
        val statuses = CopyOnWriteArrayList<SamplingStatus>()

        samplingThread = MessageSamplingThread(model, 0.5f) { 0.9f }.also { thread ->
            thread.setSamplingListener(object : MessageSamplingListener {
                override fun onSampling(data: com.xenonbyte.anr.data.MessageSamplingData) {
                    statuses.add(data.getStatus())
                }
            })
            thread.startSampling()
        }

        samplingThread?.dispatchMessage(">>>>> Dispatching to Handler (test) {1} 0x1")
        samplingThread?.dispatchMessage("<<<<< Finished to Handler (test) {1} 0x1")
        Thread.sleep(200)

        assertTrue(statuses.isEmpty())
    }
}
