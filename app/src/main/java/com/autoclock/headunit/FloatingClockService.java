package com.autoclock.headunit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingClockService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;
    private TextView tvTime;
    private Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("ClockConfig", MODE_PRIVATE);
        
        startForeground(1, createNotification());
        initWindow();
        startClock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("action")) {
            String action = intent.getStringExtra("action");
            if ("UPDATE_SIZE".equals(action)) {
                updateSize();
            } else if ("UPDATE_LOCK".equals(action)) {
                updateLockState();
            }
        }
        return START_STICKY;
    }

    private void initWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_clock, null);
        tvTime = floatingView.findViewById(R.id.tv_time);

        // Update visual size immediately
        updateSize();

        int layoutType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutType = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | 
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt("x", 100);
        params.y = prefs.getInt("y", 100);

        windowManager.addView(floatingView, params);
        setupTouch();
    }

    private void setupTouch() {
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (prefs.getBoolean("locked", false)) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        prefs.edit().putInt("x", params.x).putInt("y", params.y).apply();
                        return true;
                }
                return false;
            }
        });
    }

    private void updateSize() {
        if (tvTime != null) {
            tvTime.setTextSize(prefs.getInt("size", 24));
        }
    }

    private void updateLockState() {
        // If we needed to change flags to pass through touch events completely when locked, 
        // we would update layout params here.
        // For now, we simply ignore touch in OnTouchListener.
    }

    private void startClock() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (tvTime != null) {
                    tvTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
                }
                handler.postDelayed(this, 1000);
            }
        });
    }

    private Notification createNotification() {
        String channelId = "overlay_service";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Clock Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Floating Clock Active")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
    }
}