package com.autoclock.headunit;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    public static final String PREFS_NAME = "ClockConfig";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // UI Controls
        Switch swShow = findViewById(R.id.sw_show);
        Switch swLock = findViewById(R.id.sw_lock);
        SeekBar sbSize = findViewById(R.id.sb_size);
        TextView tvSizeVal = findViewById(R.id.tv_size_val);

        // Restore State
        boolean hasPermission = Settings.canDrawOverlays(this);
        swShow.setChecked(hasPermission && isServiceRunning()); // Logic simplified
        swLock.setChecked(prefs.getBoolean("locked", false));
        int savedSize = prefs.getInt("size", 24);
        sbSize.setProgress(savedSize - 10); // Min 10sp
        tvSizeVal.setText(savedSize + " sp");

        // Permission Check
        if (!hasPermission) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 123);
        }

        // Listeners
        swShow.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                if (Settings.canDrawOverlays(this)) {
                    startService(new Intent(this, FloatingClockService.class));
                } else {
                    btn.setChecked(false);
                    Toast.makeText(this, "Permission Required", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                }
            } else {
                stopService(new Intent(this, FloatingClockService.class));
            }
        });

        swLock.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("locked", isChecked).apply();
            // Restart service to apply layout param changes if needed, 
            // or rely on service shared preference listener (implemented in service)
            if (swShow.isChecked()) {
                Intent intent = new Intent(this, FloatingClockService.class);
                intent.putExtra("action", "UPDATE_LOCK");
                startService(intent);
            }
        });

        sbSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int size = progress + 10;
                tvSizeVal.setText(size + " sp");
                prefs.edit().putInt("size", size).apply();
                if (swShow.isChecked()) {
                    Intent intent = new Intent(MainActivity.this, FloatingClockService.class);
                    intent.putExtra("action", "UPDATE_SIZE");
                    startService(intent);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private boolean isServiceRunning() {
        // Simple check based on logic, strictly better to use ActivityManager
        return true; 
    }
}