package com.example.carclock;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;
    private TextView tvStatus;
    private boolean isPassthrough = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);

        // Check Permissions
        checkOverlayPermission();

        setupButtons();
    }

    private void setupButtons() {
        Button btnToggleShow = findViewById(R.id.btnToggleShow);
        Button btnTogglePassthrough = findViewById(R.id.btnTogglePassthrough);
        Button btnStyle = findViewById(R.id.btnStyle);
        Button btnSizeUp = findViewById(R.id.btnSizeUp);
        Button btnSizeDown = findViewById(R.id.btnSizeDown);
        
        // New Buttons
        Button btnToggleSeconds = findViewById(R.id.btnToggleSeconds);
        Button btnToggleBg = findViewById(R.id.btnToggleBg);
        Button btnToggleWeight = findViewById(R.id.btnToggleWeight);

        btnToggleShow.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_TOGGLE_VISIBILITY));
        
        btnTogglePassthrough.setOnClickListener(v -> {
            isPassthrough = !isPassthrough;
            tvStatus.setText(isPassthrough ? R.string.status_passthrough : R.string.status_blocking);
            sendCommand(FloatingClockService.ACTION_TOGGLE_PASSTHROUGH);
        });
        
        btnStyle.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_CHANGE_STYLE));
        btnSizeUp.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_INCREASE_SIZE));
        btnSizeDown.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_DECREASE_SIZE));
        
        btnToggleSeconds.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_TOGGLE_SECONDS));
        btnToggleBg.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_TOGGLE_BG));
        btnToggleWeight.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_TOGGLE_WEIGHT));
    }

    private void sendCommand(String action) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.perm_required, Toast.LENGTH_SHORT).show();
            checkOverlayPermission();
            return;
        }
        Intent intent = new Intent(this, FloatingClockService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.perm_required, Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
            } else {
                // Auto start service if permission granted
                startFloatingService();
            }
        } else {
            startFloatingService();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    startFloatingService();
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void startFloatingService() {
        Intent intent = new Intent(this, FloatingClockService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }
}