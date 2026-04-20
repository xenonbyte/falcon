# Falcon

<div align="center">

<img src="artwork/falcon_logo.svg" alt="Falcon Logo" width="200" height="200">

**一款轻量级、无侵入性的 Android ANR 监测库**

*Like a Falcon, capturing the smallest issues*

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE.txt)
[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=21)
[![Build Status](https://github.com/xenonbyte/falcon/workflows/CI%2FCD%20Pipeline/badge.svg)](https://github.com/xenonbyte/falcon/actions)
[![codecov](https://codecov.io/gh/xenonbyte/falcon/branch/main/graph/badge.svg)](https://codecov.io/gh/xenonbyte/falcon)

[Features](#-核心特性) •
[Documentation](#-文档) •

</div>

---

## 📖 关于 Falcon

**Falcon**（猎鹰）是一款轻量的 Android ANR（Application Not Responding）监测库，基于 `Looper.setMessageLogging` 机制实现，采用**炸弹-扫雷算法**捕获 ANR 和主线程耗时任务。

### ✨ 核心特性

- 🎯 **精准监测**: 炸弹-扫雷算法，精准识别 ANR
- 📊 **消息回放**: 完整的主线程消息历史记录
- 🔧 **灵活扩展**: 支持自定义数据采集器（Dumper）
- 🛡️ **可降级**: 内置健康监控和降级保护机制
- ⚡ **低开销**: 最小化对应用性能的影响
- 📉 **采样率控制**: 支持配置采样率，平衡性能和精度
- 🧪 **测试覆盖**: 已覆盖核心单元测试与集成测试场景

### 🏆 为什么选择 Falcon？

| 特性 | Falcon | BlockCanary | ANR-WatchDog |
|------|--------|-------------|--------------|
| ANR检测 | ✅ 精准 | ❌ 仅慢任务 | ⚠️ 不够精准 |
| 消息回放 | ✅ 支持 | ✅ 支持 | ❌ 不支持 |
| 采样率控制 | ✅ 支持 | ❌ 不支持 | ❌ 不支持 |
| 性能开销 | 极低 | 中等 | 极低 |
| 扩展性 | ✅ 灵活 | ⚠️ 有限 | ⚠️ 有限 |

## 🚀 快速开始

---

### 1. 引入依赖
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.xenonbyte:falcon:2.0.0'
}
```

### 2. 初始化配置

```java
// 创建监控配置
FalconConfig config = new FalconConfig.Builder()
    // 设置ANR触发阈值
    .setAnrThreshold(3000L, 5000L)
    // 设置慢任务触发阈值
    .setSlowRunnableThreshold(500L)
    // 设置日志级别
    .setLogLevel(LogLevel.WARN)
    // 设置事件监听
    .setEventListener(new FalconEventListener() {

        @Override
        public void onSlowRunnable(long currentTimestamp,
                                    @NonNull String mainStackTrace,
                                    @NonNull MessageSamplingData messageSamplingData,
                                    @NonNull String hprofData) {
            // 慢任务回调(非主线程)

        }

        @Override
        public void onAnr(long currentTimestamp,
                          @NonNull String mainStackTrace,
                          @Nullable MessageSamplingData messageSamplingData,
                          @NonNull Deque<MessageSamplingData> messageSamplingHistory,
                          @NonNull String hprofData) {
            // ANR回调(非主线程)

        }
    })
    // 添加ANR事件的应用数据dumper
    .addEventDumper(FalconEvent.ANR_EVENT, new FalconAppDumper())
    // 添加ANR事件的内存数据dumper
    .addEventDumper(FalconEvent.ANR_EVENT, new FalconMemoryDumper())
    // 添加慢任务事件的线程数据dumper
    .addEventDumper(FalconEvent.SLOW_RUNNABLE_EVENT, new FalconThreadDumper())
    .build();

// 初始化并开启监测
Falcon.initialize(application, config);
Falcon.startMonitoring();
```

> [!TIP]
>
> ANR日志级别为 `LogLevel.ERROR`，慢任务日志级别为 `LogLevel.WARN`，不输出日志`LogLevel.NONE`

> [!NOTE]
>
> - `Falcon.initialize(...)` 支持重复调用，后一次配置会替换前一次；如果当前已经在监控，会平滑重建内部组件并继续监控
> - `Falcon.startMonitoring()` / `Falcon.stopMonitoring()` 在未初始化时是 no-op，不会抛异常，也不会偷偷改变后续初始化行为


## 数据采集（Dumpers）

当应用发生 **ANR** 或 **慢任务** 时，**falcon** 会通过配置的 `Dumper` 收集设备数据，并将结果以 JSON 字符串传入事件回调中的 `hprofData` 参数。
这里的 `hprofData` 是历史命名，实际内容并不是 HPROF 文件，而是所有 Dumper 输出聚合后的 JSON 字符串。

推荐使用 `FalconDumpPayload.parse(hprofData)` 做结构化解析。对于内置 dumper，优先使用稳定公开模型 accessors：

```kotlin
override fun onAnr(
    currentTimestamp: Long,
    mainStackTrace: String,
    messageSamplingData: MessageSamplingData?,
    messageSamplingDataDeque: Deque<MessageSamplingData>,
    hprofData: String
) {
    val payload = FalconDumpPayload.parse(hprofData)
    val packageName = payload.stableAppData()?.packageName
    val memoryInfo = payload.stableMemoryData()
}
```

`FalconDumpPayload` 的几个核心行为约定：

- `FalconDumpPayload.parse(hprofData)` 本身不会抛异常；如果原始 JSON 非法，会返回一个带 `__payload_parse_error__` 条目的 payload，可通过 `hasErrors()` / `entries` 检查
- `find(name)` / `require(name)` 适合直接读取原始 Dumper 条目；`decode(name) { ... }` 适合做宽松解析，缺失或失败时返回 `null`
- `requireDecoded(name) { ... }` 以及 `requireStableAppData()` / `requireDeviceData()` 这类 helper 属于 fail-fast 读取：不仅要求条目存在且采集成功，还会在字段缺失或类型不匹配时直接抛异常，用于尽早暴露 schema 漂移或 payload 截断
- `stableAppData()` / `stableMemoryData()` / `stableThreadData()` 和扩展库的 `deviceData()` / `batteryData()` 等 nullable accessor 更适合常规业务读取链路

`appData()` / `memoryData()` / `threadData()` 仍然保留兼容，但它们返回的是 `com.xenonbyte.anr.dump.internal` 命名空间下的历史模型，现已标记为 deprecated，新接入不建议继续依赖。

如果某个 Dumper 结果在你的链路里属于必填，可以直接使用 `requireStableAppData()`、`requireStableMemoryData()`、`requireDeviceData()` 这类 helper，在缺失或采集失败时尽早显式暴露问题。

如果希望回调直接收到结构化 payload，而不是每次手动解析，可以直接继承 `FalconDumpPayloadEventAdapter`：

```kotlin
FalconConfig.Builder()
    .setEventListener(object : FalconDumpPayloadEventAdapter() {
        override fun onAnr(
            currentTimestamp: Long,
            mainStackTrace: String,
            messageSamplingData: MessageSamplingData?,
            messageSamplingDataDeque: Deque<MessageSamplingData>,
            dumpPayload: FalconDumpPayload
        ) {
            val packageName = dumpPayload.stableAppData()?.packageName
            val memoryInfo = dumpPayload.stableMemoryData()
        }
    })
```

如果同时引入了 `dumper-ext`，还可以直接读取扩展数据：

```kotlin
val deviceModel = payload.deviceData()?.model
val batteryCapacity = payload.batteryData()?.capacity
val requiredDevice = payload.requireDeviceData()
```

### 1. 内置Dumper

`falcon` 主库已经通过传递依赖包含基础 Dumper，无需额外引入 `dumper` 组件。

| Dumper | 描述 |
|--------|------|
| `FalconAppDumper` | 应用数据转储器，返回稳定公开模型 |
| `FalconMemoryDumper` | 内存数据转储器，返回稳定公开模型 |
| `FalconThreadDumper` | 线程数据转储器，返回稳定公开模型 |

历史的 `AppDumper` / `MemoryDumper` / `ThreadDumper` 仍然可用，但位于 `com.xenonbyte.anr.dump.internal` 命名空间，新接入不建议继续依赖。

### 2. 扩展Dumper

引入Dumper扩展库依赖

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.xenonbyte:dumper-ext:1.0.1'
}
```

扩展库提供更多高级`Dumper`：

| Dumper | 描述 |
|--------|------|
| `ActivityDumper` | Activity堆栈数据转储器 |
| `BatteryDumper` | 电池数据转储器 |
| `DeviceDumper` | 设备数据转储器 |
| `FdDumper` | FD数据转储器 |

### 3. 自定义Dumper

实现自定义数据采集器：

```kotlin
/**
 * Fd数据dumper
 */
class FdDumper : Dumper<FdData>("FdDumper") {
    override fun collectData(app: Application): FdData {
        val fdFile = File("/proc/self/fd")
        val openFd = fdFile.list()?.size ?: 0
        return FdData(openFd)
    }
}

/**
 * 文件描述符数据
 */
class FdData(
    val openFd: Int
) : DumpData {
    companion object {
        //打开的文件描述符数
        const val OPEN_FD = "open_fd"
    }

    override fun toJson(): JSONObject {
        return JSONObject().apply {
            put(OPEN_FD, openFd)
        }
    }
}
```


## 消息回放

当发生 **ANR** 时，**falcon** 会回放 **ANR** 之前若干个消息采样数据 `MessageSamplingData`，帮助重建问题场景。

### 消息采样数据：

| 属性                 | 描述     | 说明                   |
|--------------------|--------|----------------------|
| **id**             | 采样记录Id | 主要用于序列化输出和排查追踪 |
| **index**          | 消息索引   | 自增索引                 |
| **message**        | 消息日志文本 | `Looper.setMessageLogging` 提供的消息字符串 |
| **startTimestamp** | 消息开始时间 | 时间戳                  |
| **endTimestamp**   | 消息结束时间 | 时间戳                  |
| **duration**       | 消息执行时长 | 毫秒单位                  |
| **status**         | 消息执行状态 | `START` / `END`        |
| **complete**       | 是否完成执行 | 发生ANR时为false         |
| **stackTrace**     | 主线程堆栈  | 仅在达到堆栈采集条件时出现        |

> [!TIP]
>
> - `stackTrace` 仅在消息执行时间超过 `slowRunnableThreshold * 0.8` 时才会尝试采集
> - 当发生 ANR 时，当前消息采样数据处于未完成状态 (complete = false, duration = -1, endTimestamp = -1), 可通过后续的慢任务事件获取完整的采样数据
> - 消息最大回放数量由 `FalconConfig.Builder.setMessageSamplingMaxCacheSize` 配置决定


## 配置参数详解

| 参数 | 类型 | 默认值 | 说明               |
|------|------|--------|------------------|
| **setAnrThreshold** | Long,  Long | 4000, 8000 | ANR触发阈值 (前台, 后台) |
| **setSlowRunnableThreshold** | Long | 300 | 慢任务触发阈值          |
| **setSamplingRate** | Float | 1.0f (100%) | 消息采样率，平衡性能和精度 |
| **setLogLevel** | LogLevel | WARN | 日志输出级别           |
| **setEventListener** | FalconEventListener | 无 | 事件回调监听器          |
| **setLogPrinter** | LogPrinter | DefaultLogPrinter (`android.util.Log`) | 日志打印器 |
| **setMessageSamplingMaxCacheSize** | Int | 30 | 采样消息最大缓存量        |
| **addEventDumper** | FalconEvent, Dumper | 无 | 添加指定事件的数据转储器     |
| **setHprofDumpEnabled** | Boolean | true | 控制是否执行 Dumper 数据采集；历史命名，保留兼容 |
| **setDumpCollectionEnabled** | Boolean | true | `setHprofDumpEnabled` 的语义化别名，推荐优先使用 |

### 采样率说明

`setSamplingRate` 允许你控制监控多少比例的主线程消息：

```java
FalconConfig config = new FalconConfig.Builder()
    .setSamplingRate(0.8f)  // 80% 采样率
    .build();
```

| 采样率 | 适用场景 | 说明 |
|--------|----------|------|
| 1.0f (100%) | 调试/测试 | 捕获所有消息，最精确 |
| 0.8f (80%) | 生产环境 | 平衡精度和性能 |
| 0.5f (50%) | 低端设备 | 减少性能开销 |
| 0.1f (10%) | 极端场景 | 最小开销，可能漏检 |


## 🔧 高级用法

### 健康监控

Falcon 内置健康监控机制，可以自动检测和恢复：

```kotlin
// 获取健康状态
val healthStatus = Falcon.getHealthStatus()
Log.d("Falcon", healthStatus)

// 从错误中恢复
Falcon.resetHealthMonitor()
```

### 生命周期约定

- `initialize()` 可以重复调用，适合热更新阈值、listener 和 dumper 配置
- `startMonitoring()` 只有在完成 `initialize()` 后才会真正开启监控
- `stopMonitoring()` 会断开 `setMessageLogging`、停止后台线程并清理采样缓存
- 未初始化时调用 `startMonitoring()` / `stopMonitoring()` 是安全的 no-op
- `getLifecycleState()` 可直接判断 `NOT_INITIALIZED / STOPPED / MONITORING`
- `getHealthState()` 提供结构化健康状态，避免业务侧解析 `getHealthStatus()` 的文本

### 分环境配置

```kotlin
val config = if (BuildConfig.DEBUG) {
    FalconConfig.Builder()
        .setAnrThreshold(3000L, 5000L)
        .setSlowRunnableThreshold(100L)
        .setLogLevel(LogLevel.DEBUG)
        .build()
} else {
    FalconConfig.Builder()
        .setAnrThreshold(5000L, 10000L)
        .setSlowRunnableThreshold(500L)
        .setLogLevel(LogLevel.WARN)
        .build()
}
```

详见：[工程化指南](ENGINEERING_GUIDE.md)


## 📊 性能说明

当前仓库没有附带可复现的 benchmark 报告，因此不在 README 中给出固定性能数字。

实际开销主要受以下因素影响：

- `samplingRate` 越高，主线程消息覆盖率越高，后台处理开销也越高
- `setMessageSamplingMaxCacheSize` 越大，采样历史占用越高
- `ThreadDumper`、`DeviceDumper`、`BatteryDumper` 等 Dumper 在事件触发时会带来额外 CPU / I/O 开销
- 慢任务与 ANR 回调在线程池异步执行，不阻塞主线程，但回调内部的上报/持久化仍需自行限流


## 🛡️ 生产环境建议

### 1. 配置建议

```kotlin
val config = FalconConfig.Builder()
    .setAnrThreshold(5000L, 10000L)      // 接近系统ANR阈值
    .setSlowRunnableThreshold(500L)     // 平衡性能
    .setMessageSamplingMaxCacheSize(30) // 控制内存
    .setLogLevel(LogLevel.WARN)         // 仅重要日志
    .setDumpCollectionEnabled(isHighEndDevice()) // 按设备能力控制 Dumper 采集
    .build()
```

### 2. 灰度发布

```kotlin
val isFalconEnabled = RemoteConfig.getBoolean("enable_falcon", false)

if (isFalconEnabled) {
    Falcon.initialize(this, createConfig())
    Falcon.startMonitoring()
}
```

### 3. 数据上报

```kotlin
class UploadListener : FalconEventListener {
    override fun onAnr(..., hprofData: String) {
        // 本地持久化
        saveToLocalDatabase(anrRecord)

        // 上报到服务器（异步）
        CoroutineScope(Dispatchers.IO).launch {
            reportToServer(anrRecord)
        }
    }
}
```

详见：[工程化指南 - 最佳实践](ENGINEERING_GUIDE.md#最佳实践)


## 🧪 测试和构建

### 运行测试

```bash
# 单元测试
./gradlew :falcon:testDebugUnitTest :falcon-dump-ext:testDebugUnitTest

# 代码质量检查
./gradlew :falcon:detekt :falcon-dump:detekt :falcon-dump-ext:detekt
```

### 构建项目

```bash
# Debug 构建
./gradlew :falcon-dump:assembleDebug :falcon-dump-ext:assembleDebug

# Release 构建
./gradlew :falcon:assembleRelease
```


### 开发规范

- 所有 PR 需要通过 CI 检查
- 添加必要的测试和文档

## 📄 License

Copyright [2025] [xubo]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.


## 🌟 Star History

[![Star History Chart](https://api.star-history.com/svg?repos=xenonbyte/falcon&type=Date)](https://star-history.com/#xenonbyte/falcon&Date)

## 📚 文档

### 用户文档
- 📖 [工程化指南](ENGINEERING_GUIDE.md) - 生产环境配置和最佳实践
- 🏗️ [架构设计](ARCHITECTURE.md) - 架构设计和技术细节
