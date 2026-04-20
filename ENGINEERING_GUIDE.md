# Falcon ANR 监测库 - 工程化指南

## 项目概述

Falcon 是一款轻量级、无侵入性的 ANR（Application Not Responding）监测库，基于 Android `Looper.setMessageLogging` 机制实现，提供精准的 ANR 和主线程耗时任务监控能力。

### 核心特性

- **零侵入性**: 基于消息分发机制，无需修改业务代码
- **精准监测**: 使用炸弹-扫雷算法精准识别 ANR
- **消息回放**: 提供完整的主线程消息历史记录
- **灵活扩展**: 支持自定义数据采集器（Dumper）
- **生产就绪**: 内置健康监控和熔断机制

---

## 快速开始

### 1. 添加依赖

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.xenonbyte:falcon:2.0.0'

    // 可选：扩展数据采集器
    implementation 'com.github.xenonbyte:dumper-ext:1.0.1'
}
```

### 2. 基础配置

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = FalconConfig.Builder()
            // 设置ANR阈值（前台5秒，后台10秒）
            .setAnrThreshold(5000L, 10000L)
            // 设置慢任务阈值（300ms）
            .setSlowRunnableThreshold(300L)
            // 设置日志级别
            .setLogLevel(LogLevel.WARN)
            // 设置事件监听
            .setEventListener(object : FalconEventListener {
                override fun onSlowRunnable(
                    currentTimestamp: Long,
                    mainStackTrace: String,
                    messageSamplingData: MessageSamplingData,
                    hprofData: String
                ) {
                    // 处理慢任务（工作线程）
                    // 注意：hprofData 是历史命名，实际内容为 Dumper 输出的 JSON 字符串
                    Log.w("Falcon", "慢任务检测: ${messageSamplingData.duration}ms")
                }

                override fun onAnr(
                    currentTimestamp: Long,
                    mainStackTrace: String,
                    messageSamplingData: MessageSamplingData?,
                    messageSamplingHistory: Deque<MessageSamplingData>,
                    hprofData: String
                ) {
                    // 处理ANR（工作线程）
                    Log.e("Falcon", "ANR检测!")
                    // 上报到服务器
                    reportToServer(mainStackTrace, hprofData)
                }
            })
            // 添加数据采集器
            .addEventDumper(FalconEvent.ANR_EVENT, FalconAppDumper())
            .addEventDumper(FalconEvent.ANR_EVENT, FalconMemoryDumper())
            .build()

        Falcon.initialize(this, config)
        Falcon.startMonitoring()
    }
}
```

### 3. 停止监测

```kotlin
// 在合适的时候停止监测（如应用退出）
Falcon.stopMonitoring()
```

### 4. 生命周期约定

- `Falcon.initialize(...)` 支持重复调用，适合热更新阈值、listener 和 dumper 配置
- `Falcon.startMonitoring()` 只有在完成初始化后才会真正开始监听主线程消息
- 未初始化时调用 `Falcon.startMonitoring()` / `Falcon.stopMonitoring()` 是安全的 no-op
- `Falcon.getLifecycleState()` 可直接判断当前是否未初始化、已停止或正在监控
- `Falcon.getHealthState()` 适合业务代码做结构化判断，`Falcon.getHealthStatus()` 更适合日志/调试输出
- `hprofData` 是历史命名，实际内容为 Dumper 聚合后的 JSON 字符串
- 推荐使用 `FalconDumpPayload.parse(hprofData)` 解析 Dumper 输出；对于内置 Dumper，优先使用 `stableAppData()`、`stableMemoryData()`、`stableThreadData()` 这些稳定公开模型 accessor；旧的 `appData()` / `memoryData()` / `threadData()` 仅作兼容并已标记为 deprecated；扩展 Dumper 继续使用 `deviceData()`、`batteryData()` 等 accessor
- 如果某项 Dumper 数据在业务上属于必填，可以使用 `requireStableAppData()`、`requireStableMemoryData()`、`requireDeviceData()` 等 helper，在缺失或采集失败时直接抛出异常
- 如果不想在每个回调里手动 parse，可直接继承 `FalconDumpPayloadEventAdapter`，让回调参数直接收到 `FalconDumpPayload`

---

## 高级配置

### 1. 自定义数据采集器

```kotlin
/**
 * 自定义线程信息采集器
 */
class ThreadInfoDumper : Dumper<ThreadInfoData>("ThreadInfoDumper") {

    override fun collectData(app: Application): ThreadInfoData {
        val mainThread = Looper.getMainLooper().thread
        val threadInfo = ThreadInfo(
            name = mainThread.name,
            state = mainThread.state.name,
            priority = mainThread.priority,
            isAlive = mainThread.isAlive,
            isInterrupted = mainThread.isInterrupted
        )

        val allThreads = Thread.getAllStackTraces().keys.map { thread ->
            ThreadSnapshot(
                name = thread.name,
                state = thread.state.name,
                priority = thread.priority,
                isDaemon = thread.isDaemon
            )
        }

        return ThreadInfoData(threadInfo, allThreads)
    }
}

/**
 * 线程信息数据
 */
data class ThreadInfoData(
    val mainThread: ThreadInfo,
    val allThreads: List<ThreadSnapshot>
) : DumpData {

    companion object {
        const val MAIN_THREAD = "main_thread"
        const val ALL_THREADS = "all_threads"
    }

    override fun toJson(): JSONObject {
        return JSONObject().apply {
            put(MAIN_THREAD, JSONObject().apply {
                put("name", mainThread.name)
                put("state", mainThread.state)
                put("priority", mainThread.priority)
                put("is_alive", mainThread.isAlive)
                put("is_interrupted", mainThread.isInterrupted)
            })
            put(ALL_THREADS, JSONArray().apply {
                allThreads.forEach { thread ->
                    put(JSONObject().apply {
                        put("name", thread.name)
                        put("state", thread.state)
                        put("priority", thread.priority)
                        put("is_daemon", thread.isDaemon)
                    })
                }
            })
        }
    }
}
```

### 2. 健康监控和故障恢复

```kotlin
// 定期检查健康状态（建议1小时检查一次）
val healthCheckHandler = Handler(Looper.getMainLooper())

healthCheckHandler.post(object : Runnable {
    override fun run() {
        val healthState = Falcon.getHealthState()
        val healthStatus = Falcon.getHealthStatus()

        if (healthState == FalconHealthState.DEGRADED) {
            // 上报健康问题
            reportHealthIssue(healthStatus)

            // 尝试恢复
            Falcon.resetHealthMonitor()
        }

        // 继续下一次检查
        healthCheckHandler.postDelayed(this, 3600000L) // 1小时
    }
})
```

### 3. 生产环境配置建议

```kotlin
object FalconConfigFactory {

    fun createProductionConfig(context: Context): FalconConfig {
        return FalconConfig.Builder()
            // 前台ANR阈值：5秒（接近系统ANR阈值）
            .setAnrThreshold(5000L, 10000L)
            // 慢任务阈值：500ms（平衡性能和体验）
            .setSlowRunnableThreshold(500L)
            // 消息缓存：30个（实际占用取决于采样量和 Dumper 输出）
            .setMessageSamplingMaxCacheSize(30)
            // 日志级别：仅记录错误和警告
            .setLogLevel(LogLevel.WARN)
            // 按设备能力控制 Dumper 数据采集
            .setDumpCollectionEnabled(isHighEndDevice(context))
            // 自定义日志打印器（集成到现有日志系统）
            .setLogPrinter(CustomLogPrinter())
            .build()
    }

    fun createDebugConfig(): FalconConfig {
        return FalconConfig.Builder()
            // Debug模式使用更严格的阈值
            .setAnrThreshold(3000L, 5000L)
            .setSlowRunnableThreshold(100L)
            .setMessageSamplingMaxCacheSize(50)
            .setLogLevel(LogLevel.DEBUG)
            .setDumpCollectionEnabled(true)
            .build()
    }

    private fun isHighEndDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.memoryClass >= 256
    }

    class CustomLogPrinter : LogPrinter {
        override fun print(level: LogLevel, message: String) {
            // 集成到现有的日志系统
            when (level) {
                LogLevel.ERROR -> CustomLogger.error("Falcon", message)
                LogLevel.WARN -> CustomLogger.warn("Falcon", message)
                LogLevel.DEBUG -> CustomLogger.debug("Falcon", message)
                LogLevel.NONE -> {}
            }
        }
    }
}
```

---

## 性能优化指南

### 1. 内存优化

```kotlin
// 根据设备内存调整缓存大小
val cacheSize = when {
    isLowMemoryDevice() -> 20  // 低端设备
    isMediumDevice() -> 30     // 中端设备
    else -> 50                 // 高端设备
}

config.setMessageSamplingMaxCacheSize(cacheSize)
```

### 2. CPU优化

- 低端设备可以关闭 Dumper 数据采集（`setDumpCollectionEnabled(false)`）
- 慢任务阈值不要设置过低（建议≥300ms）
- 避免在回调中执行耗时操作

### 3. 网络优化

```kotlin
class BatchUploadListener : FalconEventListener {

    private val anrEvents = mutableListOf<ANREvent>()
    private val slowEvents = mutableListOf<SlowEvent>()

    override fun onAnr(..., hprofData: String) {
        // 批量上传，减少网络请求
        anrEvents.add(ANREvent(timestamp, mainStackTrace, hprofData))

        if (anrEvents.size >= 5) {
            uploadANREvents(anrEvents)
            anrEvents.clear()
        }
    }

    override fun onSlowRunnable(..., hprofData: String) {
        // 慢任务可以选择性上传
        if (messageSamplingData.duration > 1000) {
            slowEvents.add(SlowEvent(timestamp, messageSamplingData))
        }
    }
}
```

---

## 故障排查指南

### 问题1: 监测库未生效

**症状**: 没有收到任何ANR或慢任务回调

**排查步骤**:

1. 检查初始化顺序
```kotlin
// 确保在Application.onCreate中初始化
Falcon.initialize(this, config)
Falcon.startMonitoring()  // 不要忘记调用start
```

2. 检查日志级别
```kotlin
// 设置为DEBUG查看详细日志
.setLogLevel(LogLevel.DEBUG)
```

3. 检查健康状态
```kotlin
val health = Falcon.getHealthStatus()
Log.d("Falcon", health)
```

### 问题2: 内存占用过高

**症状**: 应用内存持续增长

**解决方案**:

1. 减少缓存大小
```kotlin
.setMessageSamplingMaxCacheSize(20)
```

2. 关闭 Dumper 数据采集
```kotlin
.setDumpCollectionEnabled(false)
```

3. 定期清理数据
```kotlin
// 在合适的时机停止监测
Falcon.stopMonitoring()
```

### 问题3: 误报ANR

**症状**: 在没有ANR的情况下收到ANR回调

**可能原因**:

1. 阈值设置过低
```kotlin
// 调整阈值，前台建议≥5000ms
.setAnrThreshold(5000L, 10000L)
```

2. Debug模式调试断点导致

3. 系统ANR（查看系统ANR日志: `/data/anr/traces.txt`）

---

## 最佳实践

### 1. 分环境配置

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = if (BuildConfig.DEBUG) {
            FalconConfigFactory.createDebugConfig()
        } else {
            FalconConfigFactory.createProductionConfig(this)
        }

        Falcon.initialize(this, config)
        Falcon.startMonitoring()
    }
}
```

### 2. 灰度发布

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val isFalconEnabled = RemoteConfig.getBoolean("enable_falcon", false)

        if (isFalconEnabled) {
            Falcon.initialize(this, createConfig())
            Falcon.startMonitoring()
        }
    }
}
```

### 3. 数据上报

```kotlin
class ReportingListener : FalconEventListener {

    override fun onAnr(..., hprofData: String) {
        // 1. 本地持久化
        saveToLocalDatabase(anrRecord)

        // 2. 上报到服务器
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reportToServer(anrRecord)
            } catch (e: Exception) {
                // 上传失败，稍后重试
                scheduleRetry(anrRecord)
            }
        }
    }
}
```

### 4. 性能监控

```kotlin
// 监控Falcon自身的性能
object FalconMetrics {
    private var anrCount = 0
    private var slowTaskCount = 0

    fun recordANR() {
        anrCount++
        checkThreshold()
    }

    fun recordSlowTask() {
        slowTaskCount++
    }

    private fun checkThreshold() {
        if (anrCount > 10) {
            // ANR频率过高，可能需要优化应用
            reportHighAnrRate()
        }
    }
}
```

---

## 安全性考虑

### 1. 敏感信息脱敏

```kotlin
class SafeLogPrinter : LogPrinter {
    override fun print(level: LogLevel, message: String) {
        // 移除可能包含的敏感信息
        val safeMessage = sanitizeMessage(message)
        Log.println(level.toAndroidLogLevel(), "Falcon", safeMessage)
    }

    private fun sanitizeMessage(message: String): String {
        return message
            .replace(Regex("""token=\S+"""), "token=***")
            .replace(Regex("""password=\S+"""), "password=***")
    }
}
```

### 2. 数据加密

```kotlin
class EncryptedUploader {
    fun upload(data: String) {
        val encrypted = AESUtil.encrypt(data, getEncryptionKey())
        // 上传加密后的数据
        httpClient.post(encrypted)
    }
}
```

---

## 集成示例

### 与Firebase Performance集成

```kotlin
class FirebasePerformanceListener : FalconEventListener {

    override fun onSlowRunnable(...) {
        // 记录慢任务到Firebase
        val trace = Firebase.performance.newTrace("slow_runnable")
        trace.start()
        trace.putAttribute("duration", "${messageSamplingData.duration}ms")
        trace.putAttribute("message", messageSamplingData.message)
        trace.stop()
    }

    override fun onAnr(...) {
        // 记录ANR到Firebase Crashlytics
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("anr_timestamp", timestamp)
            setCustomKey("main_thread_stack", mainStackTrace)
            recordException(Exception("ANR detected"))
        }
    }
}
```

### 与自定义监控系统集成

```kotlin
class CustomMonitoringListener : FalconEventListener {

    override fun onAnr(...) {
        val metrics = MonitoringMetrics(
            type = "ANR",
            timestamp = timestamp,
            deviceInfo = getDeviceInfo(),
            stackTrace = mainStackTrace,
            additionalData = parseHprofData(hprofData)
        )

        MonitoringSystem.report(metrics)
    }
}
```

---

## FAQ

### Q: Falcon会影响应用性能吗？

A: Falcon的设计目标是最小化性能影响：
- CPU开销：极低，仅在消息分发时采样
- 内存占用：2-5MB（取决于配置）
- 电池影响：几乎无影响，使用LockSupport.park()让线程休眠

### Q: 能检测到所有ANR吗？

A: Falcon可以检测到大部分ANR，但无法检测：
- 系统级别的ANR（如Service ANR）
- Binder调用超时导致的ANR
- 特殊情况下的BroadcastReceiver ANR

### Q: 支持哪些Android版本？

A: 支持API 21+（Android 5.0+）

### Q: 可以在Release版本中使用吗？

A: 可以，建议：
- 使用生产环境配置
- 关闭DEBUG级别日志
- 实施健康监控
- 定期检查监控库自身状态

---

## License

Apache License 2.0

Copyright (c) 2025 xubo
