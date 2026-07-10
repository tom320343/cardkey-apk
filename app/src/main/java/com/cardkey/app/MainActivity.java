package com.cardkey.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.cardkey.app.ActivationChecker.ActivationResult;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvDeviceId;
    private TextView tvExpiry;
    private ProgressBar progressBar;
    private View overlayCard;
    private View contentMain;

    private String deviceId;
    private String expiryTimeStr;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ActivationChecker checker = new ActivationChecker();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);
        tvDeviceId = findViewById(R.id.tv_device_id);
        tvExpiry = findViewById(R.id.tv_expiry);
        progressBar = findViewById(R.id.progress_bar);

        deviceId = generateDeviceId();
        tvDeviceId.setText("设备标识: " + deviceId);

        startVerification();
    }

    private String generateDeviceId() {
        String androidId = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null) {
            androidId = "0000000000000000";
        }
        long hash = 0;
        for (char c : androidId.toCharArray()) {
            hash = hash * 31 + c;
        }
        long code = Math.abs(hash) % 100000000L;
        return String.format(Locale.US, "%08d", code);
    }

    private void startVerification() {
        tvStatus.setText("正在验证卡密...");
        progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            ActivationResult result = checker.verifyActivation(deviceId);
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);

                if (result.success) {
                    tvStatus.setText("验证通过");
                    tvStatus.setTextColor(getColor(R.color.green));
                    if (result.expiryDate != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat(
                                "yyyy-MM-dd HH:mm", Locale.getDefault());
                        tvExpiry.setText("过期时间: " + sdf.format(result.expiryDate));
                        tvExpiry.setTextColor(getColor(R.color.green));
                    }
                    tvExpiry.append("\n\n正在进入应用...");

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        finish();
                    }, 1500);
                } else {
                    tvStatus.setText("验证失败: " + result.message);
                    tvStatus.setTextColor(getColor(R.color.red));
                    tvExpiry.setText("请检查卡密状态后重新打开应用");
                    tvExpiry.setTextColor(getColor(R.color.red));
                }
            });
        });
    }
}
