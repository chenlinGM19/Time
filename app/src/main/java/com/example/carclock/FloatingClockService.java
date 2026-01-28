package com.example.carclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
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

    public static final String ACTION_TOGGLE_VISIBILITY = "ACTION_TOGGLE_VISIBILITY";
    public static final String ACTION_TOGGLE_LOCK = "ACTION_TOGGLE_LOCK";
    public static final String ACTION_INCREASE_SIZE = "ACTION_INCREASE_SIZE";
    public static final String ACTION_DECREASE_SIZE = "ACTION_DECREASE_SIZE";
    public static final String ACTION_CHANGE_STYLE = "ACTION_CHANGE_STYLE";

    private WindowManager windowManager;
    private View floatingView;
    private TextView tvTime;
    private View rootContainer;
    private WindowManager.LayoutParams params;

    private boolean isLocked = false;
    private float currentTextSize = 24f;
    private int currentStyleIndex = 0;
    
    // Handler for updating time
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            if (tvTime != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                tvTime.setText(sdf.format(new Date()));
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundService(); // Keep alive in car units
        initializeFloatingWindow();
    }

    private void startForegroundService() {
        String channelId = "floating_clock_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Floating Clock Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Car Floating Clock")
                .setContentText("Clock is running")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build();

        startForeground(1, notification);
    }

    private void initializeFloatingWindow() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Inflate Layout
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_clock, null);
        tvTime = floatingView.findViewById(R.id.tv_clock_time);
        rootContainer = floatingView.findViewById(R.id.root_container);

        // Window Params
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, // Allow going over status bar
                PixelFormat.TRANSLUCENT
        );

        // Default Position: Top Leftish
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        // Add View
        windowManager.addView(floatingView, params);

        // Start Clock
        handler.post(updateTimeRunnable);

        // Drag Listener
        setupTouchListener();
    }

    private void setupTouchListener() {
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isLocked) return false; // If locked, pass touch through or do nothing

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return false;
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_TOGGLE_VISIBILITY:
                    toggleVisibility();
                    break;
                case ACTION_TOGGLE_LOCK:
                    isLocked = !isLocked;
                    break;
                case ACTION_INCREASE_SIZE:
                    changeSize(2f);
                    break;
                case ACTION_DECREASE_SIZE:
                    changeSize(-2f);
                    break;
                case ACTION_CHANGE_STYLE:
                    cycleStyle();
                    break;
            }
        }
        return START_STICKY;
    }

    private void toggleVisibility() {
        if (floatingView.getVisibility() == View.VISIBLE) {
            floatingView.setVisibility(View.GONE);
        } else {
            floatingView.setVisibility(View.VISIBLE);
        }
    }

    private void changeSize(float delta) {
        currentTextSize += delta;
        if (currentTextSize < 12) currentTextSize = 12;
        if (currentTextSize > 72) currentTextSize = 72;
        tvTime.setTextSize(currentTextSize);
    }

    private void cycleStyle() {
        currentStyleIndex = (currentStyleIndex + 1) % 3;
        GradientDrawable bg = (GradientDrawable) rootContainer.getBackground();
        
        switch (currentStyleIndex) {
            case 0: // Dark Translucent
                bg.setColor(Color.parseColor("#99000000"));
                tvTime.setTextColor(Color.WHITE);
                break;
            case 1: // Light Translucent
                bg.setColor(Color.parseColor("#99FFFFFF"));
                tvTime.setTextColor(Color.BLACK);
                break;
            case 2: // High Contrast Blue
                bg.setColor(Color.parseColor("#FF2196F3"));
                tvTime.setTextColor(Color.WHITE);
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
        handler.removeCallbacks(updateTimeRunnable);
    }
}