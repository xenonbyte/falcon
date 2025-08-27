package com.xenonbyte.anr

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Looper
import android.util.ArrayMap
import com.github.xenonbyte.ObjectPoolStore
import com.github.xenonbyte.ObjectPoolStoreOwner
import com.xenonbyte.anr.bomb.AnrBattlefield
import com.xenonbyte.anr.bomb.AnrBombExplosionListener
import com.xenonbyte.anr.bomb.AnrBombThread
import com.xenonbyte.anr.data.MessageSamplingData
import com.xenonbyte.anr.data.SamplingStatus
import com.xenonbyte.anr.dump.Dumper
import com.xenonbyte.anr.dump.collectDataToJson
import com.xenonbyte.anr.sampling.MessageSamplingListener
import com.xenonbyte.anr.sampling.MessageSamplingModel
import com.xenonbyte.anr.sampling.MessageSamplingThread
import com.xenonbyte.anr.sampling.MessageStackTraceCapturer
import org.json.JSONArray
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * 控制器
 *
 * @author xubo
 */
internal class FalconController(
    private val app: Application,
    private val foregroundAnrThreshold: Long,
    private val backgroundAnrThreshold: Long,
    private val slowMessageThreshold: Long,
    private val messageSamplingMaxCacheSize: Int,
    private val hprofDumpEnabled: Boolean,
    private val dumperMap: ArrayMap<FalconEvent, LinkedHashSet<Dumper<*>>>
) : MessageSamplingListener, AnrBombExplosionListener, ObjectPoolStoreOwner {

    override val store = ObjectPoolStore()

    //事件监听
    private var listener: FalconEventListener? = null

    //事件处理线程池
    private var threadPool: ThreadPoolExecutor? = null

    //采样数据模型
    private val messageSamplingModel = MessageSamplingModel(
        this,
        isLowMemoryDevice(app),
        messageSamplingMaxCacheSize
    )

    //消息采样线程
    private val messageSamplingThread = MessageSamplingThread(messageSamplingModel)

    //消息堆栈采集器
    private val messageStackTraceCapturer = MessageStackTraceCapturer(messageSamplingModel, slowMessageThreshold)

    //Anr炸弹线程
    private val anrBombThread = AnrBombThread()

    //Anr模拟战场
    private val anrBattlefield: AnrBattlefield = AnrBattlefield(
        foregroundAnrThreshold,
        backgroundAnrThreshold,
        anrBombThread
    )

    /**
     * 初始化
     */
    fun initialize() {
        //消息采样线程设置采样监听
        messageSamplingThread.setSamplingListener(this)
        //开启消息采样
        messageSamplingThread.startSampling()

        //开启消息堆栈采集
        messageStackTraceCapturer.startCapturing()

        //Anr模拟战场设置Anr炸弹爆炸监听
        anrBattlefield.setBombExplosionListener(this)
        //开启Anr雷区
        anrBombThread.startBombSpace()
    }

    /**
     * 开启监测
     */
    fun start() {
        //任务不排队且核心线程只有1条的线程池
        threadPool = ThreadPoolExecutor(
            1,
            Int.MAX_VALUE,
            0L,
            TimeUnit.SECONDS,
            SynchronousQueue(),
            FalconEventThreadFactory()
        )
        //主线程消息分发给采样线程
        Looper.getMainLooper().setMessageLogging { message ->
            messageSamplingThread.dispatchMessage(message)
        }
    }

    /**
     * 停止监测
     */
    fun stop() {
        //主线程消息分发断开
        Looper.getMainLooper().setMessageLogging(null)
        //停止线程池
        threadPool?.shutdown()
        threadPool = null
        //清空采样数据模型数据
        messageSamplingModel.clear()
        //清空缓存池
        store.clear()
    }

    override fun onSampling(data: MessageSamplingData) {
        if (data.getStatus() == SamplingStatus.START) {  //采样起始消息
            //调度安排Anr游戏
            anrBattlefield.deployAnrBattle(data.getMessage()) {
                it == messageSamplingThread
            }
            //调度安排堆栈采集任务
            messageStackTraceCapturer.scheduleCapture {
                it == messageSamplingThread
            }
        } else if (data.getStatus() == SamplingStatus.END) { //采样结束消息
            //取消堆栈采集任务
            messageStackTraceCapturer.cancelCapture {
                it == messageSamplingThread
            }
            if (data.getDuration() >= slowMessageThreshold) {
                //慢任务回调处理
                threadPool?.execute {
                    FalconUtils.log(LogLevel.WARN) {
                        val startTime = FalconUtils.formatTimestamp(data.getStartTimestamp(), "yyyy-MM-dd HH:mm:ss.SSS")
                        val endTime = FalconUtils.formatTimestamp(data.getEndTimestamp(), "yyyy-MM-dd HH:mm:ss.SSS")
                        val duration = data.getDuration()
                        "-----Slow Message-----\nstartTime=$startTime endTime=$endTime duration=${duration}ms message=${data.getMessage()}\n${data.getStackTrace()}"
                    }
                    val hprofData = dumpHprofData(app, dumperMap[FalconEvent.SLOW_RUNNABLE_EVENT])
                    listener?.onSlowRunnable(FalconTimestamp.currentTimeMillis(), data.getStackTrace(), data, hprofData)
                }
            }
        }
    }

    override fun onAnrBombExplosion() {
        val stackTrace = FalconUtils.captureStackTrace(Looper.getMainLooper().thread)
        val currentSamplingData = messageSamplingModel.getCurrentSamplingData()
        currentSamplingData?.takeIf { it.getStackTrace() == null }?.setMainStackTrace(stackTrace)
        val messageSamplingDataDeque = messageSamplingModel.getSamplingDataDeque()
        //Anr回调处理
        threadPool?.execute {
            FalconUtils.log(LogLevel.ERROR) {
                val startTime = FalconUtils.formatTimestamp(
                    currentSamplingData?.getStartTimestamp() ?: 0, "yyyy-MM-dd HH:mm:ss.SSS"
                )
                "-----Anr Event-----\nstartTime=$startTime message=${currentSamplingData?.getMessage()}\n$stackTrace"
            }
            val hprofData = dumpHprofData(app, dumperMap[FalconEvent.ANR_EVENT])
            listener?.onAnr(
                FalconTimestamp.currentTimeMillis(),
                stackTrace,
                currentSamplingData,
                messageSamplingDataDeque,
                hprofData
            )
        }
    }

    /**
     * 设置事件监听
     *
     * @param listener 事件监听
     */
    fun setFalconListener(listener: FalconEventListener?) {
        this.listener = listener
    }

    /**
     * 转储分析数据
     *
     * @param app 应用Application
     * @param dumpers 数据转储器集合
     */
    private fun dumpHprofData(app: Application, dumpers: LinkedHashSet<Dumper<*>>?): String {
        if (!hprofDumpEnabled) {
            return ""
        }
        val jsonArray = JSONArray()
        dumpers?.let {
            it.forEach { dumper ->
                val dumpDataJson = dumper.collectDataToJson(app)
                jsonArray.put(dumpDataJson)
            }
        }
        return jsonArray.toString()
    }

    /**
     * 是否低内存设备
     *
     * @param context 上下文
     */
    private fun isLowMemoryDevice(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.isLowRamDevice
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 线程创建工厂
     *
     * @author xubo
     */
    class FalconEventThreadFactory : ThreadFactory {
        private val threadName = "com.xenonbyte:FalconEventThread"

        override fun newThread(r: Runnable?): Thread {
            return Thread(r, threadName)
        }

    }

}