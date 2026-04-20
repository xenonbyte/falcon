# Falcon 架构设计文档

## 目录

1. [概述](#概述)
2. [架构设计](#架构设计)
3. [核心组件](#核心组件)
4. [工作流程](#工作流程)
5. [数据流](#数据流)
6. [线程模型](#线程模型)
7. [性能考虑](#性能考虑)
8. [扩展性设计](#扩展性设计)

---

## 概述

Falcon 是一个基于 Android `Looper.setMessageLogging` 机制的 ANR 监测库，采用非侵入式设计，通过监听主线程消息分发来检测应用卡顿和 ANR。

### 设计目标

1. **零侵入**: 不需要修改业务代码
2. **低开销**: 对应用性能影响最小
3. **高准确**: 精准识别 ANR 和慢任务
4. **易扩展**: 支持自定义数据采集

### 核心算法

Falcon 采用**炸弹-扫雷算法**（Bomb-Defuse Algorithm）来实现 ANR 检测：

- **炸弹**: 在后台线程设置延时任务，如果主线程消息在阈值内未完成则"爆炸"
- **扫雷**: 当消息完成时取消炸弹任务

---

## 架构设计

### 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                         Application                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐        ┌──────────────┐                  │
│  │   Falcon     │◄───────│  Controller  │                  │
│  │   (Facade)   │        │              │                  │
│  └──────┬───────┘        └───────┬──────┘                  │
│         │                        │                          │
│         │                 ┌──────┴──────┐                  │
│         │                 │             │                  │
│         │        ┌────────▼────┐  ┌────▼─────────┐        │
│         │        │   Message   │  │    Anr        │        │
│         │        │   Sampling  │  │  Battlefield  │        │
│         │        │    Thread   │  │               │        │
│         │        └──────┬──────┘  └──────┬────────┘        │
│         │               │                │                  │
│         │        ┌──────┴──────┐  ┌─────┴───────┐         │
│         │        │   Message   │  │    Anr      │          │
│         │        │   Sampling  │  │    Bomb     │          │
│         │        │    Model    │  │    Thread   │          │
│         │        └─────────────┘  └─────────────┘         │
│         │                                                  │
└─────────┼──────────────────────────────────────────────────┘
          │
          │ setMessageLogging
          ▼
┌─────────────────────────────────────────────────────────────┐
│                    Main Looper                              │
│                  (Message Queue)                            │
└─────────────────────────────────────────────────────────────┘
```

---

## 核心组件

### 1. Falcon (Facade)

**职责**: 对外统一接口，单例模式

**关键方法**:
- `initialize(app, config)`: 初始化配置
- `startMonitoring()`: 开启监测
- `stopMonitoring()`: 停止监测
- `getHealthStatus()`: 获取健康状态

**设计模式**: Facade + Singleton

```kotlin
class Falcon private constructor() {
    private object Holder {
        val INSTANCE = Falcon()
    }

    companion object {
        @JvmStatic
        fun initialize(app: Application, config: FalconConfig) {
            getInstance().init(app, config)
        }
    }
}
```

### 2. FalconController (协调器)

**职责**: 协调各个组件，处理事件回调

**职责范围**:
- 初始化采样和检测组件
- 处理采样数据
- 触发ANR和慢任务回调
- 管理健康监控
- 线程池管理

**关键特性**:
- 使用单线程线程池处理事件
- 健康检查和熔断机制
- 异常捕获和恢复

### 3. MessageSamplingThread (消息采样器)

**职责**: 在专用线程上接收和处理主线程消息采样事件

**工作原理**:
1. 接收 `setMessageLogging` 回调
2. 对单次主线程 dispatch 做成对采样决策，避免 `START` / `END` 被随机拆散
3. 将采样消息入队，交给采样模型配对和落盘
4. 在空闲时主动让出 CPU，长时间空闲后 `park`

**实现细节**:
```kotlin
fun dispatchMessage(message: String?) {
    if (message == null || !isStarted()) return
    if (Thread.currentThread() !== Looper.getMainLooper().thread) return
    if (!shouldSampleMessage(message)) return

    addMessageData(message, FalconTimestamp.currentTimeMillis())
}
```

### 4. MessageSamplingModel (采样数据模型)

**职责**: 存储和管理采样数据

**数据结构**:
- 环形缓冲区（固定大小）
- 线程安全（使用读写锁）
- 索引追踪（支持回放）

**线程安全**:
```kotlin
class MessageSamplingModel {
    private val lock = ReentrantReadWriteLock()

    fun addData(data: MessageSamplingData) {
        lock.writeLock().lock()
        try {
            // 添加数据
        } finally {
            lock.writeLock().unlock()
        }
    }
}
```

### 5. AnrBattlefield (ANR检测器)

**职责**: 实现炸弹-扫雷算法

**工作流程**:
1. 消息开始时，布置"炸弹"（延时任务）
2. 消息结束时，"扫雷"（取消炸弹）
3. 如果炸弹爆炸，触发ANR回调

**算法实现**:
```kotlin
fun deployAnrBattle(message: String) {
    val delay = if (isAppForeground()) {
        foregroundAnrThreshold
    } else {
        backgroundAnrThreshold
    }

    // 布置炸弹
    bomber?.schedule(delay)

    // 布置扫雷（提前500ms）
    defuser?.schedule(delay - BOMB_DEFUSING_INTERVAL_DURATION)
}
```

### 6. MessageStackTraceCapturer (堆栈捕获器)

**职责**: 在慢任务时捕获主线程堆栈

**工作原理**:
- 定期检查消息执行时间
- 超过阈值时捕获堆栈
- 避免频繁捕获（性能考虑）

### 7. FalconHealthMonitor (健康监控器)

**职责**: 监控监测库自身的健康状态

**监控指标**:
- 错误计数
- 错误频率
- 健康状态（HEALTHY / DEGRADED）

**降级机制**:
- 错误数超过阈值 → 进入降级模式
- 降级模式 → 跳过部分检测逻辑
- 错误窗口过期后 → 自动恢复健康状态

---

## 工作流程

### 初始化流程

```
Application.onCreate
    │
    ├─> Falcon.initialize(app, config)
    │       │
    │       └─> Falcon.init()
    │               │
    │               ├─> ActivityWatcher.initialize()
    │               ├─> create FalconController
    │               └─> set listener / logger
    │
    └─> Falcon.startMonitoring()
            │
            └─> FalconController.start()
                    │
                    ├─> ensureRealtimeComponents()
                    │       ├─> MessageSamplingThread.startSampling()
                    │       ├─> MessageStackTraceCapturer.startCapturing()
                    │       └─> AnrBombThread.startBombSpace()
                    │
                    └─> Looper.setMessageLogging()
```

### 消息采样流程

```
Main Thread Message
    │
    ├─> MessageLogging callback
    │       │
    │       ├─> MessageSamplingThread.dispatchMessage()
    │       │       │
    │       │       └─> MessageDataQueue.offer()
    │       │
    │       └─> MessageSamplingThread.run()
    │               │
    │               ├─> poll message data
    │               ├─> MessageSamplingModel.handleMessageData()
    │               ├─> notify sampling listener
    │               │
    │               └─> FalconController.onSampling()
    │                       │
    │                       ├─> AnrBattlefield.deployAnrBattle()
    │                       │       │
    │                       │       ├─> AnrBombThread (炸弹)
    │                       │       └─> MainThread (扫雷)
    │                       │
    │                       └─> MessageStackTraceCapturer
    │
    └─> Message End
            │
            └─> main-thread defuse task tries to cancel bomb
```

### ANR检测流程

```
Message Start
    │
    ├─> AnrBattlefield.deployAnrBattle()
    │       │
    │       ├─> bomber.schedule(threshold)
    │       │       │
    │       │       └─> Handler.postDelayed(bombTask, threshold)
    │       │
    │       └─> defuser.schedule(threshold - 500)
    │               │
    │               └─> Handler.postDelayed(defuseTask, threshold - 500)
    │
    ├─> Message End (< threshold)
    │       │
    │       └─> defuseTask executed
    │               │
    │               └─> cancel bombTask
    │
    └─> Message End (> threshold) → ANR!
            │
            └─> bombTask executed
                    │
                    ├─> capture main thread stack
                    ├─> get sampling history
                    └─> FalconController.onAnrBombExplosion()
                            │
                            └─> listener.onAnr()
```

---

## 数据流

### 采样数据流

```
MainLooper
    │
    ├─> setMessageLogging callback
    │
    ├─> MessageSamplingThread
    │       │
    │       ├─> parse message (BEGIN/END)
    │       │
    │       ├─> calculate duration
    │       │
    │       └─> MessageSamplingData
    │               │
    │               ├─> id
    │               ├─> index
    │               ├─> message
    │               ├─> startTimestamp
    │               ├─> endTimestamp
    │               ├─> duration
    │               ├─> status (START/END)
    │               ├─> stackTrace
    │               └─> complete
    │
    └─> MessageSamplingModel
            │
            └─> Circular Buffer (回放数据)
```

### 事件回调流

```
Slow Runnable Event
    │
    ├─> MessageSamplingModel
    │       │
    │       └─> duration >= threshold
    │
    ├─> FalconController.onSampling()
    │       │
    │       ├─> collect dumper JSON
    │       ├─> execute on thread pool
    │       └─> listener.onSlowRunnable()
    │               │
    │               └─> User Callback

ANR Event
    │
    ├─> AnrBattlefield
    │       │
    │       └─> bomb explosion
    │
    ├─> FalconController.onAnrBombExplosion()
    │       │
    │       ├─> capture stack trace
    │       ├─> snapshot current sampling data / history deque
    │       ├─> collect dumper JSON
    │       ├─> execute on thread pool
    │       └─> listener.onAnr()
    │               │
    │               └─> User Callback
```

---

## 线程模型

### 线程列表

| 线程名 | 用途 | 类型 | 优先级 |
|--------|------|------|--------|
| Main Thread | 主线程 | UI线程 | Normal |
| MessageSamplingThread | 消息采样和数据处理 | 专用 `Thread` | Normal |
| MessageStackTraceCapturer | 慢任务堆栈捕获 | `HandlerThread` | Normal |
| AnrBombThread | ANR炸弹任务 | HandlerThread | Normal |
| FalconEventThread | 事件回调执行 | ThreadPoolExecutor | Normal |

### 线程协作

```
Main Thread
    │
    ├─> dispatch message (同步)
    │       │
    │       └─> MessageSamplingThread
    │
    ├─> receive message (同步)
    │
    └─> execute message (同步)

MessageSamplingThread
    │
    ├─> process message data (异步)
    │
    ├─> update sampling model (同步，使用锁)
    │
    ├─> notify controller (同步)
    │
    └─> LockSupport.park() (等待)

MessageStackTraceCapturer
    │
    ├─> schedule delayed capture (异步)
    │
    └─> capture main-thread stack when threshold reached

AnrBombThread
    │
    ├─> schedule bomb task (延时)
    │
    └─> execute bomb callback (异步)

FalconEventThread
    │
    └─> execute user callback (异步)
```

### 线程安全机制

1. **读写锁**: MessageSamplingModel
2. **CAS操作**: Falcon.isEnable, AnrBattlefield.inBattle
3. **同步块**: 错误记录列表
4. **Atomic变量**: 错误计数、时间戳
5. **ThreadLocal**: SimpleDateFormat缓存

---

## 性能考虑

### 内存优化

**策略**:
1. 固定大小环形缓冲区
2. 低内存设备自动减少缓存
3. 对象池复用（ObjectPoolStore）
4. 及时清理资源

**说明**:
- 当前仓库未附带按设备分层的内存压测报告
- 实际占用与消息缓存大小、对象池复用情况以及 Dumper 输出内容强相关

### CPU优化

**策略**:
1. 使用 `LockSupport.park()` 让线程休眠
2. 避免频繁堆栈捕获
3. 最小化同步操作
4. 使用CAS替代锁

**说明**:
- 当前仓库未附带可复现 benchmark 报告
- 性能表现主要受 `samplingRate`、缓存大小和 Dumper 实现影响
- 对外文档应以实测数据为准，不在架构文档中固化具体数值

### I/O优化

**策略**:
1. 堆栈捕获延迟执行
2. 数据采集按需触发
3. 避免在主线程执行I/O

---

## 扩展性设计

### Dumper 扩展机制

**接口定义**:
```kotlin
abstract class Dumper<T : DumpData>(protected val name: String) {
    abstract fun collectData(app: Application): T
}
```

**扩展示例**:
```kotlin
class CustomDumper : Dumper<CustomData>("CustomDumper") {
    override fun collectData(app: Application): CustomData {
        // 自定义采集逻辑
    }
}
```

### 事件监听扩展

**接口定义**:
```kotlin
interface FalconEventListener {
    fun onSlowRunnable(...)
    fun onAnr(...)
}
```

**扩展点**:
- 自定义日志记录
- 数据上报
- 本地持久化
- 性能分析

### 配置扩展

**Builder模式**:
```kotlin
val config = FalconConfig.Builder()
    .setAnrThreshold(...)
    .setSlowRunnableThreshold(...)
    .addEventDumper(...)
    .setEventListener(...)
    .setLogPrinter(...)
    .build()
```

---

## 设计模式应用

| 设计模式 | 应用场景 | 位置 |
|----------|----------|------|
| Singleton | Falcon单例 | Falcon.kt |
| Facade | 统一接口 | Falcon.kt |
| Builder | 配置构建 | FalconConfig.kt |
| Observer | 事件监听 | FalconEventListener.kt |
| Strategy | Dumper策略 | Dumper.kt |
| Template Method | 采集模板 | Dumper.kt |
| Object Pool | 对象复用 | ObjectPoolStore |

---
