package com.xenonbyte.anr.sample;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.xenonbyte.anr.data.MessageSamplingData;
import com.xenonbyte.anr.dump.FalconDumpPayload;

import java.text.SimpleDateFormat;

final class SampleFalconEvent {

    @NonNull
    private final String typeLabel;
    private final long timestamp;
    @NonNull
    private final String headline;
    @NonNull
    private final String message;
    @NonNull
    private final String payloadSummary;

    private SampleFalconEvent(
            @NonNull String typeLabel,
            long timestamp,
            @NonNull String headline,
            @NonNull String message,
            @NonNull String payloadSummary
    ) {
        this.typeLabel = typeLabel;
        this.timestamp = timestamp;
        this.headline = headline;
        this.message = message;
        this.payloadSummary = payloadSummary;
    }

    @NonNull
    static SampleFalconEvent fromSlowRunnable(
            long timestamp,
            @NonNull MessageSamplingData messageSamplingData,
            @NonNull FalconDumpPayload payload
    ) {
        return new SampleFalconEvent(
                "SLOW",
                timestamp,
                "Slow runnable · " + messageSamplingData.getDuration() + " ms",
                sanitizeMessage(messageSamplingData.getMessage()),
                SamplePayloadSummary.build(payload)
        );
    }

    @NonNull
    static SampleFalconEvent fromAnr(
            long timestamp,
            @Nullable MessageSamplingData currentMessage,
            int historySize,
            @NonNull FalconDumpPayload payload
    ) {
        String message = currentMessage == null
                ? "Current main-thread message is unavailable"
                : sanitizeMessage(currentMessage.getMessage());
        return new SampleFalconEvent(
                "ANR",
                timestamp,
                "ANR captured · history " + historySize,
                message,
                SamplePayloadSummary.build(payload)
        );
    }

    @NonNull
    String getTypeLabel() {
        return typeLabel;
    }

    long getTimestamp() {
        return timestamp;
    }

    @NonNull
    String getHeadline() {
        return headline;
    }

    @NonNull
    String getMessage() {
        return message;
    }

    @NonNull
    String getPayloadSummary() {
        return payloadSummary;
    }

    @NonNull
    String toTimelineLine(@NonNull SimpleDateFormat formatter) {
        return "[" + formatter.format(timestamp) + "] "
                + typeLabel
                + " · "
                + headline
                + "\n"
                + "message: "
                + message
                + "\n"
                + "payload: "
                + payloadSummary;
    }

    @NonNull
    private static String sanitizeMessage(@Nullable String message) {
        if (message == null) {
            return "No message";
        }
        String normalized = message.replace('\n', ' ').trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 117) + "...";
    }
}
