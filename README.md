# falcon

**falcon** 是一款轻量级、无侵入性的性能监测库，基于 `Looper.setMessageLogging` 实现，可精准捕获 **ANR** 和 **主线程耗时任务**。提供 自定义数据 **Dumper** 和 **消息回放** 功能，帮助快速定位性能问题。

## 🚀 快速开始

### 1. 引入依赖
```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.xenonbyte:falcon:1.0.0'
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

当应用发生 **ANR** 或 **慢任务** 时，**falcon** 会通过配置的 `Dumper` 收集设备数据，生成`hprofData`数据后触发事件回调


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
    implementation 'com.github.xenonbyte:falcon-dumper-ext:1.0.0'
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

| 属性                 | 描述     | 说明                 |
|--------------------|--------|--------------------|
| **id**             | 消息Id   | 唯一标识               |
| **index**          | 消息索引   | 循环索引               |
| **message**        | 消息原始对象 | Android 中的 `Message` |
| **startTimestamp** | 消息开始时间 | 时间戳                |
| **endTimestamp**   | 消息结束时间 | 时间戳                |
| **duration**       | 消息执行时长 | 秒单位                |
| **status**         | 消息执行状态 | 成功/失败              |
| **complete**       | 是否完成执行 | 发生ANR时为false       |
| **stackTrace**     | 主线程堆栈  | 仅在耗时消息中出现          |

> [!TIP]
>
> - `stacSkTrace`堆栈追踪仅在耗时消息（执行时间 > 慢任务阈值 × 采集因子）中收集
> - 当发生 ANR 时，当前消息采样数据处于未完成状态 (complete = false, duration = -1, endTimestamp = -1), 可通过后续的慢任务事件获取完整的采样数据
> - 消息最大回访数量由`FalconConfig.Builder.setMessageSamplingMaxCacheSize`配置决定



## 配置参数详解

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| **setAnrThreshold** | Long,  Long | 4000, 8000 | ANR触发阈值 (前台, 后台) |
| **setSlowRunnableThreshold** | Long | 300 | 慢任务触发阈值 |
| **setLogLevel** | LogLevel | WARN | 日志输出级别 |
| **setEventListener** | FalconEventListener | 无 | 事件回调监听器 |
| **setLogPrinter** | LogPrinter | android.android.Log | 日志打印器 |
| **setMessageSamplingMaxCacheSize** | Int | 30 | 采样消息最大缓存量 |
| **addEventDumper** | FalconEvent, Dumper | 无 | 添加指定事件的数据转储器 |
| **setHprofDumpEnabled** | Boolean | true | 开启数据分析 |




## License

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

