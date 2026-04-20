package com.xenonbyte.anr.sample;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class SampleEventStore {

    interface Listener {
        void onSnapshotChanged(@NonNull Snapshot snapshot);
    }

    static final class Snapshot {
        @Nullable
        private final SampleFalconEvent latestEvent;
        @NonNull
        private final List<SampleFalconEvent> events;

        Snapshot(@Nullable SampleFalconEvent latestEvent, @NonNull List<SampleFalconEvent> events) {
            this.latestEvent = latestEvent;
            this.events = events;
        }

        @Nullable
        SampleFalconEvent getLatestEvent() {
            return latestEvent;
        }

        @NonNull
        List<SampleFalconEvent> getEvents() {
            return events;
        }
    }

    private static final int MAX_EVENTS = 20;
    private static final SampleEventStore INSTANCE = new SampleEventStore();

    private final Object lock = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ArrayDeque<SampleFalconEvent> events = new ArrayDeque<>();

    @Nullable
    private SampleFalconEvent latestEvent;

    private SampleEventStore() {
    }

    static SampleEventStore get() {
        return INSTANCE;
    }

    void addListener(@NonNull Listener listener) {
        listeners.addIfAbsent(listener);
        dispatchSnapshot(listener, snapshot());
    }

    void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    void record(@NonNull SampleFalconEvent event) {
        Snapshot snapshot;
        synchronized (lock) {
            latestEvent = event;
            events.addFirst(event);
            while (events.size() > MAX_EVENTS) {
                events.removeLast();
            }
            snapshot = snapshotLocked();
        }
        dispatchSnapshot(snapshot);
    }

    void clear() {
        Snapshot snapshot;
        synchronized (lock) {
            latestEvent = null;
            events.clear();
            snapshot = snapshotLocked();
        }
        dispatchSnapshot(snapshot);
    }

    @NonNull
    Snapshot snapshot() {
        synchronized (lock) {
            return snapshotLocked();
        }
    }

    @NonNull
    private Snapshot snapshotLocked() {
        return new Snapshot(
                latestEvent,
                Collections.unmodifiableList(new ArrayList<>(events))
        );
    }

    private void dispatchSnapshot(@NonNull Snapshot snapshot) {
        for (Listener listener : listeners) {
            dispatchSnapshot(listener, snapshot);
        }
    }

    private void dispatchSnapshot(@NonNull Listener listener, @NonNull Snapshot snapshot) {
        mainHandler.post(() -> listener.onSnapshotChanged(snapshot));
    }
}
