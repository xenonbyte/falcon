package com.xenonbyte.anr.sample;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.xenonbyte.anr.Falcon;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Falcon Demo 主页面
 *
 * 展示各种 ANR 和慢任务测试场景，用于验证 Falcon 监控功能。
 */
public class MainActivity extends AppCompatActivity {

    // 测试按钮
    private Button mSlowTaskBtn;
    private Button mAnrTaskBtn;
    private Button mCustomBlockBtn;
    private Button mRefreshHealthBtn;

    // 计算器按钮
    private Button mAddBtn;
    private Button mSubtractBtn;
    private Button mMultiplyBtn;
    private Button mDivideBtn;

    // 输入框
    private EditText mBlockDurationInput;
    private EditText mNum1Input;
    private EditText mNum2Input;

    // 文本显示
    private TextView mHealthStatusText;
    private TextView mResultText;
    private TextView mLogText;

    // 日志格式化
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    private final StringBuilder mLogBuilder = new StringBuilder();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_activity_main);

        initViews();
        setupListeners();
        updateHealthStatus();
    }

    private void initViews() {
        // 测试按钮
        mSlowTaskBtn = findViewById(R.id.slow_task_btn);
        mAnrTaskBtn = findViewById(R.id.anr_task_btn);
        mCustomBlockBtn = findViewById(R.id.custom_block_btn);
        mRefreshHealthBtn = findViewById(R.id.refresh_health_btn);

        // 计算器按钮
        mAddBtn = findViewById(R.id.add_btn);
        mSubtractBtn = findViewById(R.id.subtract_btn);
        mMultiplyBtn = findViewById(R.id.multiply_btn);
        mDivideBtn = findViewById(R.id.divide_btn);

        // 输入框
        mBlockDurationInput = findViewById(R.id.block_duration_input);
        mNum1Input = findViewById(R.id.num1_input);
        mNum2Input = findViewById(R.id.num2_input);

        // 文本显示
        mHealthStatusText = findViewById(R.id.health_status_text);
        mResultText = findViewById(R.id.result_text);
        mLogText = findViewById(R.id.log_text);
    }

    private void setupListeners() {
        // 慢任务测试 (500ms)
        mSlowTaskBtn.setOnClickListener(v -> {
            appendLog("Starting slow task test (500ms)...");
            blockMainThread(500);
            appendLog("Slow task completed");
        });

        // ANR 测试 (4s)
        mAnrTaskBtn.setOnClickListener(v -> {
            appendLog("Starting ANR test (4s)...");
            blockMainThread(4000);
            appendLog("ANR test completed");
        });

        // 自定义阻塞时间
        mCustomBlockBtn.setOnClickListener(v -> {
            String durationStr = mBlockDurationInput.getText().toString();
            if (TextUtils.isEmpty(durationStr)) {
                Toast.makeText(this, "Please enter duration", Toast.LENGTH_SHORT).show();
                return;
            }
            int duration = Integer.parseInt(durationStr);
            appendLog("Starting custom block test (" + duration + "ms)...");
            blockMainThread(duration);
            appendLog("Custom block completed");
        });

        // 刷新健康状态
        mRefreshHealthBtn.setOnClickListener(v -> updateHealthStatus());

        // 计算器按钮
        mAddBtn.setOnClickListener(v -> calculate(Operation.ADD));
        mSubtractBtn.setOnClickListener(v -> calculate(Operation.SUBTRACT));
        mMultiplyBtn.setOnClickListener(v -> calculate(Operation.MULTIPLY));
        mDivideBtn.setOnClickListener(v -> calculate(Operation.DIVIDE));
    }

    /**
     * 阻塞主线程指定时间
     *
     * @param durationMs 阻塞时长（毫秒）
     */
    private void blockMainThread(int durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            appendLog("Block interrupted: " + e.getMessage());
        }
    }

    /**
     * 更新健康状态
     */
    private void updateHealthStatus() {
        String status = Falcon.getHealthStatus();
        mHealthStatusText.setText(status);
        appendLog("Health status refreshed");
    }

    /**
     * 执行计算
     */
    private void calculate(Operation operation) {
        String num1Str = mNum1Input.getText().toString();
        String num2Str = mNum2Input.getText().toString();

        if (TextUtils.isEmpty(num1Str) || TextUtils.isEmpty(num2Str)) {
            Toast.makeText(this, "Please enter both numbers", Toast.LENGTH_SHORT).show();
            return;
        }

        double num1 = Double.parseDouble(num1Str);
        double num2 = Double.parseDouble(num2Str);
        double result;

        switch (operation) {
            case ADD:
                result = num1 + num2;
                appendLog(String.format(Locale.getDefault(), "Calculate: %.2f + %.2f = %.2f", num1, num2, result));
                break;
            case SUBTRACT:
                result = num1 - num2;
                appendLog(String.format(Locale.getDefault(), "Calculate: %.2f - %.2f = %.2f", num1, num2, result));
                break;
            case MULTIPLY:
                result = num1 * num2;
                appendLog(String.format(Locale.getDefault(), "Calculate: %.2f × %.2f = %.2f", num1, num2, result));
                break;
            case DIVIDE:
                if (num2 == 0) {
                    mResultText.setText("Result: Error (division by zero)");
                    appendLog("Calculate: Division by zero error");
                    return;
                }
                result = num1 / num2;
                appendLog(String.format(Locale.getDefault(), "Calculate: %.2f ÷ %.2f = %.2f", num1, num2, result));
                break;
            default:
                return;
        }

        mResultText.setText(String.format(Locale.getDefault(), "Result: %.2f", result));
    }

    /**
     * 追加日志
     */
    private void appendLog(String message) {
        String timestamp = mDateFormat.format(new Date());
        String logLine = "[" + timestamp + "] " + message + "\n";

        // 限制日志长度
        if (mLogBuilder.length() > 5000) {
            mLogBuilder.delete(0, 1000);
        }

        mLogBuilder.append(logLine);
        mLogText.setText(mLogBuilder.toString());

        // 滚动到底部
        final TextView logTextView = mLogText;
        logTextView.post(() -> {
            // 获取父 ScrollView 并滚动
            View parent = (View) logTextView.getParent();
            if (parent != null) {
                parent.scrollTo(0, logTextView.getHeight());
            }
        });
    }

    /**
     * 计算操作类型
     */
    private enum Operation {
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE
    }
}
