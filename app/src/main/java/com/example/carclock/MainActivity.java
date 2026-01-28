package com.example.carclock;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQ_CODE = 1234;
    private static final String PREFS_NAME = "CarClockPrefs";
    private static final String KEY_SHOW_TOASTS = "show_toasts";
    
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
        setupClipboardButtons();
    }

    private void setupButtons() {
        // Appearance Group
        Button btnToggleShow = findViewById(R.id.btnToggleShow);
        Button btnResetPos = findViewById(R.id.btnResetPos);
        Button btnSizeUp = findViewById(R.id.btnSizeUp);
        Button btnSizeDown = findViewById(R.id.btnSizeDown);
        Button btnStyle = findViewById(R.id.btnStyle);
        Button btnToggleOrient = findViewById(R.id.btnToggleOrient);
        Button btnToggleBg = findViewById(R.id.btnToggleBg);
        Button btnToggleWeight = findViewById(R.id.btnToggleWeight);
        
        // Behavior Group
        Button btnTogglePassthrough = findViewById(R.id.btnTogglePassthrough);
        Button btnToggleSeconds = findViewById(R.id.btnToggleSeconds);
        Button btnToggleTips = findViewById(R.id.btnToggleTips);

        btnToggleShow.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_TOGGLE_VISIBILITY));
        btnResetPos.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_RESET_POSITION));
        btnSizeUp.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_INCREASE_SIZE));
        btnSizeDown.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_DECREASE_SIZE));
        btnStyle.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_CHANGE_STYLE));
        btnToggleOrient.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_TOGGLE_ORIENTATION));
        btnToggleBg.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_TOGGLE_BG));
        btnToggleWeight.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_TOGGLE_WEIGHT));
        
        btnTogglePassthrough.setOnClickListener(v -> {
            isPassthrough = !isPassthrough;
            tvStatus.setText(isPassthrough ? R.string.status_passthrough : R.string.status_blocking);
            sendCommand(FloatingClockService.ACTION_TOGGLE_PASSTHROUGH);
        });
        
        btnToggleSeconds.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_TOGGLE_SECONDS));
        btnToggleTips.setOnClickListener(v -> sendCommand(FloatingClockService.ACTION_TOGGLE_TOASTS));
    }
    
    private void setupClipboardButtons() {
        // Event Intents (Sent by App)
        Button btnCopyClick = findViewById(R.id.btnCopyClick);
        Button btnCopyDouble = findViewById(R.id.btnCopyDouble);
        Button btnCopyLong = findViewById(R.id.btnCopyLong);

        btnCopyClick.setOnClickListener(v -> 
            copyToClipboard("Click Intent", FloatingClockService.ACTION_BROADCAST_CLICK));
            
        btnCopyDouble.setOnClickListener(v -> 
            copyToClipboard("Double Click Intent", FloatingClockService.ACTION_BROADCAST_DOUBLE_CLICK));
            
        btnCopyLong.setOnClickListener(v -> 
            copyToClipboard("Long Press Intent", FloatingClockService.ACTION_BROADCAST_LONG_PRESS));
            
        // Control Intents (Received by App)
        Button btnCopySetVisible = findViewById(R.id.btnCopySetVisible);
        Button btnCopySetBlocking = findViewById(R.id.btnCopySetBlocking);

        btnCopySetVisible.setOnClickListener(v -> 
            copyToClipboard("Force Visible Intent", FloatingClockService.ACTION_SET_VISIBLE));
            
        btnCopySetBlocking.setOnClickListener(v -> 
            copyToClipboard("Force Blocking Intent", FloatingClockService.ACTION_SET_BLOCKING));
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            
            // Respect global toast settings
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean showToasts = prefs.getBoolean(KEY_SHOW_TOASTS, true);
            
            if (showToasts) {
                Toast.makeText(this, getString(R.string.toast_copied) + " " + text, Toast.LENGTH_SHORT).show();
            }
        }
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
        
        // REMOVED: Toast.makeText(this, R.string.toast_cmd_sent, Toast.LENGTH_SHORT).show();
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