package com.xenonbyte.anr.sample;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.xenonbyte.anr.dump.ActivityData;
import com.xenonbyte.anr.dump.BatteryData;
import com.xenonbyte.anr.dump.DeviceData;
import com.xenonbyte.anr.dump.FalconAppData;
import com.xenonbyte.anr.dump.FalconBuiltInDumpAccessorsKt;
import com.xenonbyte.anr.dump.FalconDumpPayload;
import com.xenonbyte.anr.dump.FalconExtDumpAccessorsKt;
import com.xenonbyte.anr.dump.FalconMemoryData;
import com.xenonbyte.anr.dump.FdData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SamplePayloadSummary {

    private SamplePayloadSummary() {
    }

    @NonNull
    static String build(@NonNull FalconDumpPayload payload) {
        List<String> parts = new ArrayList<>();

        FalconAppData appData = FalconBuiltInDumpAccessorsKt.stableAppData(payload);
        if (appData != null && !TextUtils.isEmpty(appData.getPackageName())) {
            parts.add("app=" + appData.getPackageName());
        }

        ActivityData activityData = FalconExtDumpAccessorsKt.activityData(payload);
        if (activityData != null) {
            parts.add(activityData.getAppForeground() ? "foreground" : "background");
            if (!TextUtils.isEmpty(activityData.getActivityStack())) {
                parts.add("activity=" + trimValue(activityData.getActivityStack(), 24));
            }
        }

        DeviceData deviceData = FalconExtDumpAccessorsKt.deviceData(payload);
        if (deviceData != null && !TextUtils.isEmpty(deviceData.getModel())) {
            parts.add("device=" + deviceData.getModel());
        }

        BatteryData batteryData = FalconExtDumpAccessorsKt.batteryData(payload);
        if (batteryData != null && batteryData.getCapacity() >= 0) {
            parts.add("battery=" + batteryData.getCapacity() + "%");
        }

        FalconMemoryData memoryData = FalconBuiltInDumpAccessorsKt.stableMemoryData(payload);
        if (memoryData != null && memoryData.getAppTotalMemory() > 0L) {
            parts.add(String.format(Locale.getDefault(), "heap=%d MB", toMb(memoryData.getAppTotalMemory())));
        }

        FdData fdData = FalconExtDumpAccessorsKt.fdData(payload);
        if (fdData != null && fdData.getOpenFd() > 0) {
            parts.add("fd=" + fdData.getOpenFd());
        }

        if (parts.isEmpty()) {
            return "No parsed dumper payload";
        }
        return TextUtils.join(" · ", parts);
    }

    private static long toMb(long value) {
        return value / (1024L * 1024L);
    }

    @NonNull
    private static String trimValue(@NonNull String value, int maxLength) {
        String normalized = value.replace('\n', ' ').trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }
}
