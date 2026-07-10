package com.cardkey.app;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.cardkey.app.ActivationChecker.ActivationResult;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Dialog cardDialog;
    private TextView tvStatus;
    private TextView tvDeviceId;
    private TextView tvExpiry;
    private TextView tvDebug;
    private ProgressBar progressBar;

    private String deviceId;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ActivationChecker checker = new ActivationChecker();
    private static final String QQ_CONTACT_URL = "https://qm.qq.com/q/CC8NGfmTJY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceId = generateDeviceId();
        showCardKeyDialog();
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

    private void showCardKeyDialog() {
        cardDialog = new Dialog(this);
        cardDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        cardDialog.setContentView(R.layout.dialog_card_key);
        cardDialog.setCancelable(false);
        cardDialog.setCanceledOnTouchOutside(false);

        Window window = cardDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        tvStatus = cardDialog.findViewById(R.id.tv_status);
        tvDeviceId = cardDialog.findViewById(R.id.tv_device_id);
        tvExpiry = cardDialog.findViewById(R.id.tv_expiry);
        tvDebug = cardDialog.findViewById(R.id.tv_debug);
        progressBar = cardDialog.findViewById(R.id.progress_bar);
        TextView tvQqContact = cardDialog.findViewById(R.id.tv_qq_contact);

        tvDeviceId.setText("设备标识: " + deviceId);

        tvQqContact.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(QQ_CONTACT_URL));
            startActivity(intent);
        });

        cardDialog.show();
        startVerification();
    }

    private void startVerification() {
        tvStatus.setText("正在验证卡密...");
        progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            ActivationResult result = checker.verifyActivation(deviceId);
            mainHandler.post(() -> {
                progressBar.setVisibility(View.GONE);

                SimpleDateFormat sdf = new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm", Locale.getDefault());

                if (result.expiryDate != null) {
                    tvExpiry.setText("解锁时间: " + sdf.format(result.expiryDate));
                }

                if (result.success) {
                    tvStatus.setText("已解锁，正在进入...");
                    tvStatus.setTextColor(getColor(R.color.green));
                    tvExpiry.setTextColor(getColor(R.color.green));

                    mainHandler.postDelayed(() -> {
                        if (cardDialog != null && cardDialog.isShowing()) {
                            cardDialog.dismiss();
                        }
                        finish();
                    }, 1500);
                } else {
                    tvStatus.setText(result.message);
                    tvStatus.setTextColor(getColor(R.color.red));
                    tvExpiry.setTextColor(getColor(R.color.red));
                    if (result.debugInfo != null && !result.debugInfo.isEmpty()) {
                        tvDebug.setText(result.debugInfo);
                        tvDebug.setVisibility(View.VISIBLE);
                    }
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cardDialog != null && cardDialog.isShowing()) {
            cardDialog.dismiss();
        }
        executor.shutdownNow();
    }
}
