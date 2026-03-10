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
    .addEventDumper(FalconEvent.ANR_EVENT, new AppDumper())
    // 添加ANR事件的内存数据dumper
    .addEventDumper(FalconEvent.ANR_EVENT, new MemoryDumper())
    // 添加慢任务事件的线程数据dumper
    .addEventDumper(FalconEvent.SLOW_RUNNABLE_EVENT, new ThreadDumper())
    .build();

// 初始化并开启监测
Falcon.initialize(context, config);
Falcon.startMonitoring();
```

> [!TIP]
>
> ANR日志级别为 `LogLevel.ERROR`，慢任务日志级别为 `LogLevel.WARN`，不输出日志`LogLevel.NONE`


## 数据采集（Dumpers）

当应用发生 **ANR** 或 **慢任务** 时，**falcon** 会通过配置的 `Dumper` 收集设备数据，并将结果以 JSON 字符串传入事件回调中的 `hprofData` 参数。
这里的 `hprofData` 是历史命名，实际内容并不是 HPROF 文件。

### 1. 内置Dumper

| Dumper | 描述 |
|--------|------|
| `AppDumper` | 应用数据转储器 |
| `MemoryDumper` | 内存数据转储器 |
| `ThreadDumper` | 线程数据转储器 |

### 2. 扩展Dumper

引入Dumper扩展库依赖

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.xenonbyte:dumper-ext:2.0.0'
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

当发生 **ANR** 时，**falcon** 会回放 **ANR** 之前若干个消息采样数据 `MessageSamplingData`，帮助重建问题场景

### 消息采样数据：

| 属性                 | 描述     | 说明                   |
|--------------------|--------|----------------------|
| **id**             | 消息Id   | 唯一标识                 |
| **index**          | 消息索引   | 自增索引                 |
| **message**        | 消息原始对象 | Android 中的 `Message` |
| **startTimestamp** | 消息开始时间 | 时间戳                  |
| **endTimestamp**   | 消息结束时间 | 时间戳                  |
| **duration**       | 消息执行时长 | 秒单位                  |
| **status**         | 消息执行状态 | 成功/失败                |
| **complete**       | 是否完成执行 | 发生ANR时为false         |
| **stackTrace**     | 主线程堆栈  | 仅在耗时消息中出现            |

> [!TIP]
>
> - `stacSkTrace`堆栈追踪仅在耗时消息（执行时间 > 慢任务阈值 × 采集因子）中收集
> - 当发生 ANR 时，当前消息采样数据处于未完成状态 (complete = false, duration = -1, endTimestamp = -1), 可通过后续的慢任务事件获取完整的采样数据
> - 消息最大回访数量由`FalconConfig.Builder.setMessageSamplingMaxCacheSize`配置决定


## 配置参数详解

| 参数 | 类型 | 默认值 | 说明               |
|------|------|--------|------------------|
| **setAnrThreshold** | Long,  Long | 4000, 8000 | ANR触发阈值 (前台, 后台) |
| **setSlowRunnableThreshold** | Long | 300 | 慢任务触发阈值          |
| **setSamplingRate** | Float | 1.0f (100%) | 消息采样率，平衡性能和精度 |
| **setLogLevel** | LogLevel | WARN | 日志输出级别           |
| **setEventListener** | FalconEventListener | 无 | 事件回调监听器          |
| **setLogPrinter** | LogPrinter | android.android.Log | 日志打印器            |
| **setMessageSamplingMaxCacheSize** | Int | 30 | 采样消息最大缓存量        |
| **addEventDumper** | FalconEvent, Dumper | 无 | 添加指定事件的数据转储器     |
| **setHprofDumpEnabled** | Boolean | true | 开启 Dumper 数据采集（低端机型可关闭） |

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


## 📊 性能指标

### 资源占用

| 设备类型 | 内存占用 | CPU占用 | 电池消耗 |
|----------|----------|---------|----------|
| 低端设备 | ~2MB | <1% | 可忽略 |
| 中端设备 | ~3MB | <0.5% | 可忽略 |
| 高端设备 | ~5MB | <0.3% | 可忽略 |

### 性能开销

- 消息采样: <0.1ms
- 堆栈捕获: 1-5ms
- 数据采集: 5-20ms
- 回调执行: 异步，不阻塞主线程


## 🛡️ 生产环境建议

### 1. 配置建议

```kotlin
val config = FalconConfig.Builder()
    .setAnrThreshold(5000L, 10000L)      // 接近系统ANR阈值
    .setSlowRunnableThreshold(500L)     // 平衡性能
    .setMessageSamplingMaxCacheSize(30) // 控制内存
    .setLogLevel(LogLevel.WARN)         // 仅重要日志
    .setHprofDumpEnabled(isHighEndDevice()) // 按设备配置
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
./gradlew test

# 集成测试
./gradlew connectedAndroidTest

# 代码质量检查
./gradlew detekt
```

### 构建项目

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease
```


### 开发规范

- 遵循 [Kotlin 编码规范](https://developer.android.com/kotlin/style-guide)
- 使用 [Conventional Commits](https://www.conventionalcommits.org/) 格式
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


---

<div align="center">

**如果 Falcon 对你有帮助，请给个 ⭐️ Star 支持一下！**

Made with ❤️ by [xubo](https://github.com/xenonbyte)

</div>

## 📚 文档

### 用户文档
- 📖 [工程化指南](ENGINEERING_GUIDE.md) - 生产环境配置和最佳实践
- 🏗️ [架构设计](ARCHITECTURE.md) - 架构设计和技术细节
