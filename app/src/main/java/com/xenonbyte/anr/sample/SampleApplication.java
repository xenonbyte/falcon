package com.xenonbyte.anr.sample;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xenonbyte.anr.Falcon;
import com.xenonbyte.anr.FalconConfig;
import com.xenonbyte.anr.FalconDumpPayloadEventAdapter;
import com.xenonbyte.anr.FalconEvent;
import com.xenonbyte.anr.LogLevel;
import com.xenonbyte.anr.data.MessageSamplingData;
import com.xenonbyte.anr.dump.ActivityDumper;
import com.xenonbyte.anr.dump.BatteryDumper;
import com.xenonbyte.anr.dump.DeviceDumper;
import com.xenonbyte.anr.dump.FalconAppDumper;
import com.xenonbyte.anr.dump.FalconDumpPayload;
import com.xenonbyte.anr.dump.FalconMemoryDumper;
import com.xenonbyte.anr.dump.FalconThreadDumper;
import com.xenonbyte.anr.dump.FdDumper;

import java.util.Deque;

public class SampleApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SampleEventStore.get().clear();

        FalconConfig config = new FalconConfig.Builder()
                .setAnrThreshold(3000L, 5000L)
                .setSlowRunnableThreshold(500L)
                .setSamplingRate(1.0f)
                .setLogLevel(LogLevel.WARN)
                .setDumpCollectionEnabled(true)
                .setEventListener(new FalconDumpPayloadEventAdapter() {
                    @Override
                    public void onSlowRunnable(
                            long currentTimestamp,
                            @NonNull String mainStackTrace,
                            @NonNull MessageSamplingData messageSamplingData,
                            @NonNull FalconDumpPayload dumpPayload
                    ) {
                        SampleEventStore.get().record(
                                SampleFalconEvent.fromSlowRunnable(
                                        currentTimestamp,
                                        messageSamplingData,
                                        dumpPayload
                                )
                        );
                    }

                    @Override
                    public void onAnr(
                            long currentTimestamp,
                            @NonNull String mainStackTrace,
                            @Nullable MessageSamplingData messageSamplingData,
                            @NonNull Deque<MessageSamplingData> messageSamplingDataDeque,
                            @NonNull FalconDumpPayload dumpPayload
                    ) {
                        SampleEventStore.get().record(
                                SampleFalconEvent.fromAnr(
                                        currentTimestamp,
                                        messageSamplingData,
                                        messageSamplingDataDeque.size(),
                                        dumpPayload
                                )
                        );
                    }
                })
                .addEventDumper(FalconEvent.ANR_EVENT, new FalconAppDumper())
                .addEventDumper(FalconEvent.ANR_EVENT, new ActivityDumper())
                .addEventDumper(FalconEvent.ANR_EVENT, new BatteryDumper())
                .addEventDumper(FalconEvent.ANR_EVENT, new DeviceDumper())
                .addEventDumper(FalconEvent.ANR_EVENT, new FdDumper())
                .addEventDumper(FalconEvent.ANR_EVENT, new FalconMemoryDumper())
                .addEventDumper(FalconEvent.ANR_EVENT, new FalconThreadDumper())
                .addEventDumper(FalconEvent.SLOW_RUNNABLE_EVENT, new FalconAppDumper())
                .addEventDumper(FalconEvent.SLOW_RUNNABLE_EVENT, new FalconMemoryDumper())
                .addEventDumper(FalconEvent.SLOW_RUNNABLE_EVENT, new DeviceDumper())
                .addEventDumper(FalconEvent.SLOW_RUNNABLE_EVENT, new BatteryDumper())
                .addEventDumper(FalconEvent.SLOW_RUNNABLE_EVENT, new FalconThreadDumper())
                .build();

        Falcon.initialize(this, config);
        Falcon.startMonitoring();
    }
}
