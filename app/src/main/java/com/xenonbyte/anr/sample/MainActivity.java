package com.xenonbyte.anr.sample;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.xenonbyte.anr.Falcon;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private View mSlowTaskBtn;
    private View mAnrTaskBtn;
    private View mCustomBlockBtn;
    private View mRefreshHealthBtn;
    private EditText mBlockDurationInput;
    private TextView mLifecycleStateText;
    private TextView mHealthStateText;
    private TextView mHealthStatusText;
    private TextView mLatestEventTypeText;
    private TextView mLatestEventTitleText;
    private TextView mLatestEventMessageText;
    private TextView mLatestEventPayloadText;
    private TextView mLogText;

    private final SimpleDateFormat mTimeFormatter = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private final SampleEventStore.Listener mStoreListener = this::renderSnapshot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_activity_main);
        initViews();
        setupListeners();
        refreshStatus();
        renderSnapshot(SampleEventStore.get().snapshot());
    }

    @Override
    protected void onStart() {
        super.onStart();
        SampleEventStore.get().addListener(mStoreListener);
    }

    @Override
    protected void onStop() {
        SampleEventStore.get().removeListener(mStoreListener);
        super.onStop();
    }

    private void initViews() {
        mSlowTaskBtn = findViewById(R.id.slow_task_btn);
        mAnrTaskBtn = findViewById(R.id.anr_task_btn);
        mCustomBlockBtn = findViewById(R.id.custom_block_btn);
        mRefreshHealthBtn = findViewById(R.id.refresh_health_btn);
        mBlockDurationInput = findViewById(R.id.block_duration_input);
        mLifecycleStateText = findViewById(R.id.lifecycle_state_text);
        mHealthStateText = findViewById(R.id.health_state_text);
        mHealthStatusText = findViewById(R.id.health_status_text);
        mLatestEventTypeText = findViewById(R.id.latest_event_type_text);
        mLatestEventTitleText = findViewById(R.id.latest_event_title_text);
        mLatestEventMessageText = findViewById(R.id.latest_event_message_text);
        mLatestEventPayloadText = findViewById(R.id.latest_event_payload_text);
        mLogText = findViewById(R.id.log_text);
    }

    private void setupListeners() {
        mRefreshHealthBtn.setOnClickListener(v -> refreshStatus());
        mSlowTaskBtn.setOnClickListener(v -> triggerBlock(600));
        mAnrTaskBtn.setOnClickListener(v -> triggerBlock(4000));
        mCustomBlockBtn.setOnClickListener(v -> triggerCustomBlock());
    }

    private void triggerCustomBlock() {
        String durationText = mBlockDurationInput.getText().toString().trim();
        if (TextUtils.isEmpty(durationText)) {
            Toast.makeText(this, getString(R.string.toast_enter_duration), Toast.LENGTH_SHORT).show();
            return;
        }

        long durationMs;
        try {
            durationMs = Long.parseLong(durationText);
        } catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.toast_invalid_duration), Toast.LENGTH_SHORT).show();
            return;
        }

        if (durationMs <= 0L) {
            Toast.makeText(this, getString(R.string.toast_invalid_duration), Toast.LENGTH_SHORT).show();
            return;
        }

        triggerBlock(durationMs);
    }

    private void triggerBlock(long durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Toast.makeText(this, getString(R.string.toast_block_interrupted), Toast.LENGTH_SHORT).show();
        } finally {
            refreshStatus();
        }
    }

    private void refreshStatus() {
        mLifecycleStateText.setText(Falcon.getLifecycleState().name());
        mHealthStateText.setText(Falcon.getHealthState().name());
        mHealthStatusText.setText(Falcon.getHealthStatus());
    }

    private void renderSnapshot(SampleEventStore.Snapshot snapshot) {
        SampleFalconEvent latestEvent = snapshot.getLatestEvent();
        if (latestEvent == null) {
            mLatestEventTypeText.setText(getString(R.string.event_idle_label));
            mLatestEventTitleText.setText(getString(R.string.event_idle_title));
            mLatestEventMessageText.setText(getString(R.string.event_idle_message));
            mLatestEventPayloadText.setText(getString(R.string.event_idle_payload));
        } else {
            mLatestEventTypeText.setText(latestEvent.getTypeLabel());
            mLatestEventTitleText.setText(latestEvent.getHeadline());
            mLatestEventMessageText.setText(latestEvent.getMessage());
            mLatestEventPayloadText.setText(latestEvent.getPayloadSummary());
        }
        mLogText.setText(buildTimeline(snapshot.getEvents()));
    }

    private String buildTimeline(List<SampleFalconEvent> events) {
        if (events.isEmpty()) {
            return getString(R.string.timeline_empty);
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < events.size(); index++) {
            if (index > 0) {
                builder.append("\n\n");
            }
            builder.append(events.get(index).toTimelineLine(mTimeFormatter));
        }
        return builder.toString();
    }
}
