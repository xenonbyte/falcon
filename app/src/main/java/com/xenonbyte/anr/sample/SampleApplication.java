package com.xenonbyte.anr.sample;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xenonbyte.anr.Falcon;
import com.xenonbyte.anr.FalconConfig;
import com.xenonbyte.anr.FalconEvent;
import com.xenonbyte.anr.FalconEventListener;
import com.xenonbyte.anr.LogLevel;
import com.xenonbyte.anr.data.MessageSamplingData;
import com.xenonbyte.anr.dump.ActivityDumper;
import com.xenonbyte.anr.dump.BatteryDumper;
import com.xenonbyte.anr.dump.DeviceDumper;
import com.xenonbyte.anr.dump.FdDumper;
import com.xenonbyte.anr.dump.internal.AppDumper;
import com.xenonbyte.anr.dump.internal.MemoryDumper;
import com.xenonbyte.anr.dump.internal.ThreadDumper;

import java.util.Deque;


public class SampleApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        //创建监控配置
        FalconConfig config = new FalconConfig.Builder()
                //设置Anr触发阈值
                .setAnrThreshold(3000L, 5000L)
                //设置慢任务触发阈值
                .setSlowRunnableThreshold(500L)
                //设置日志输出级别
                .setLogLevel(LogLevel.WARN)
                //开启数据分析
                .setHprofDumpEnabled(true)
                //设置事件监听
                .setEventListener(new FalconEventListener() {

                    @Override
                    public void onSlowRunnable(long currentTimestamp, @NonNull String mainStackTrace, @NonNull MessageSamplingData messageSamplingData, @NonNull String hprofData) {
                        //慢任务回调(非主线程)
                    }

                    @Override
                    public void onAnr(long currentTimestamp, @NonNull String mainStackTrace, @Nullable MessageSamplingData messageSamplingData, @NonNull Deque<MessageSamplingData> messageSamplingDataDeque, @NonNull String hprofData) {
                        //ANR回调(非主线程)
                    }

                })
                //Anr事件添加应用数据dumper
                .addEventDumper(FalconEvent.ANR_EVENT, new AppDumper())
                //Anr事件添加Activity堆栈dumper
                .addEventDumper(FalconEvent.ANR_EVENT, new ActivityDumper())
                //Anr事件添加电池数据dumper
                .addEventDumper(FalconEvent.ANR_EVENT, new BatteryDumper())
                //Anr事件添加设备数据dumper
                .addEventDumper(FalconEvent.ANR_EVENT, new DeviceDumper())
                //Anr事件添加Fd数据dumper
                .addEventDumper(FalconEvent.ANR_EVENT, new FdDumper())
                //Anr事件添加内存数据dumper
                .addEventDumper(FalconEvent.ANR_EVENT, new MemoryDumper())
                //Anr事件添加线程数据dumper
                .addEventDumper(FalconEvent.ANR_EVENT, new ThreadDumper())
                //慢任务事件添加内存数据dumper
                .addEventDumper(FalconEvent.SLOW_RUNNABLE_EVENT, new MemoryDumper())
                //慢任务事件添加设备数据dumper
                .addEventDumper(FalconEvent.SLOW_RUNNABLE_EVENT, new DeviceDumper())
                //慢任务事件添加电池数据dumper
                .addEventDumper(FalconEvent.SLOW_RUNNABLE_EVENT, new BatteryDumper())
                //慢任务事件添加线程数据dumper
                .addEventDumper(FalconEvent.SLOW_RUNNABLE_EVENT, new ThreadDumper())
                //构建配置
                .build();
        //初始化
        Falcon.initialize(this, config);
        //开启监测
        Falcon.startMonitoring();
    }
}
