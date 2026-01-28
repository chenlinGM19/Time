package com.example.carclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingClockService extends Service {

    public static final String ACTION_TOGGLE_VISIBILITY = "ACTION_TOGGLE_VISIBILITY";
    public static final String ACTION_TOGGLE_PASSTHROUGH = "ACTION_TOGGLE_PASSTHROUGH"; // New
    public static final String ACTION_INCREASE_SIZE = "ACTION_INCREASE_SIZE";
    public static final String ACTION_DECREASE_SIZE = "ACTION_DECREASE_SIZE";
    public static final String ACTION_CHANGE_STYLE = "ACTION_CHANGE_STYLE";
    public static final String ACTION_TOGGLE_SECONDS = "ACTION_TOGGLE_SECONDS"; // New
    public static final String ACTION_TOGGLE_BG = "ACTION_TOGGLE_BG"; // New
    public static final String ACTION_TOGGLE_WEIGHT = "ACTION_TOGGLE_WEIGHT"; // New

    private WindowManager windowManager;
    private View floatingView;
    private TextView tvTime;
    private View rootContainer;
    private WindowManager.LayoutParams params;

    private boolean isPassthrough = false; // Controls FLAG_NOT_TOUCHABLE
    private boolean showSeconds = true;
    private boolean isBgVisible = true;
    private boolean isBold = false;

    private float currentTextSize = 24f;
    private int currentStyleIndex = 0;
    
    // Handler for updating time
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            if (tvTime != null) {
                // Determine format based on showSeconds flag
                String pattern = showSeconds ? "HH:mm:ss" : "HH:mm";
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
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
                // Flags:
                // NOT_FOCUSABLE: Allows interaction with windows behind it (key events)
                // LAYOUT_NO_LIMITS: Allows window to extend outside of the screen decorations (status bar)
                // LAYOUT_IN_SCREEN: Required for some versions to truly overlay the status bar
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, 
                PixelFormat.TRANSLUCENT
        );

        // Default Position: Top Leftish
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 50;

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
                // If passthrough is enabled, the system shouldn't even send events here 
                // because of FLAG_NOT_TOUCHABLE. But as a safeguard:
                if (isPassthrough) return false; 

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
                case ACTION_TOGGLE_PASSTHROUGH:
                    togglePassthrough();
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
                case ACTION_TOGGLE_SECONDS:
                    showSeconds = !showSeconds;
                    // Force update immediately
                    handler.removeCallbacks(updateTimeRunnable);
                    handler.post(updateTimeRunnable);
                    break;
                case ACTION_TOGGLE_BG:
                    toggleBackground();
                    break;
                case ACTION_TOGGLE_WEIGHT:
                    toggleFontWeight();
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

    private void togglePassthrough() {
        isPassthrough = !isPassthrough;
        if (isPassthrough) {
            // Add NOT_TOUCHABLE flag -> touches go through to underlying app
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            // Remove NOT_TOUCHABLE flag -> app intercepts touches (draggable)
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        windowManager.updateViewLayout(floatingView, params);
    }

    private void toggleBackground() {
        isBgVisible = !isBgVisible;
        if (isBgVisible) {
            // Restore styling logic
            applyStyle(currentStyleIndex); 
        } else {
            // Remove background
            rootContainer.setBackground(null);
        }
    }

    private void toggleFontWeight() {
        isBold = !isBold;
        tvTime.setTypeface(null, isBold ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void changeSize(float delta) {
        currentTextSize += delta;
        if (currentTextSize < 12) currentTextSize = 12;
        if (currentTextSize > 96) currentTextSize = 96; 
        tvTime.setTextSize(currentTextSize);
    }

    private void cycleStyle() {
        currentStyleIndex = (currentStyleIndex + 1) % 4;
        applyStyle(currentStyleIndex);
    }

    private void applyStyle(int index) {
        // If user turned off background, we only update text color but keep BG transparent
        if (!isBgVisible) {
            rootContainer.setBackground(null);
            // Still need to update text color based on style
             switch (index) {
                case 0: // Dark
                case 2: // Blue
                    tvTime.setTextColor(Color.WHITE);
                    break;
                case 1: // Light
                    tvTime.setTextColor(Color.BLACK);
                    break;
                case 3: // Neon
                    tvTime.setTextColor(Color.GREEN);
                    break;
            }
            return;
        }

        // Reset background drawable
        Drawable bg = ContextCompat.getDrawable(this, R.drawable.bg_clock_rounded);
        // We must mutate it to not share state if we were using multiple instances (good practice)
        if (bg != null) bg = bg.mutate();
        rootContainer.setBackground(bg);

        if (bg instanceof GradientDrawable) {
            GradientDrawable gradientBg = (GradientDrawable) bg;
            switch (index) {
                case 0: // Dark Translucent
                    gradientBg.setColor(Color.parseColor("#99000000"));
                    gradientBg.setStroke(2, Color.parseColor("#33FFFFFF"));
                    tvTime.setTextColor(Color.WHITE);
                    break;
                case 1: // Light Translucent
                    gradientBg.setColor(Color.parseColor("#99FFFFFF"));
                    gradientBg.setStroke(2, Color.parseColor("#33000000"));
                    tvTime.setTextColor(Color.BLACK);
                    break;
                case 2: // High Contrast Blue
                    gradientBg.setColor(Color.parseColor("#FF2196F3"));
                    gradientBg.setStroke(2, Color.WHITE);
                    tvTime.setTextColor(Color.WHITE);
                    break;
                case 3: // Neon Green (High Visibility)
                    gradientBg.setColor(Color.parseColor("#FF000000"));
                    gradientBg.setStroke(2, Color.GREEN);
                    tvTime.setTextColor(Color.GREEN);
                    break;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null) windowManager.removeView(floatingView);
        handler.removeCallbacks(updateTimeRunnable);
    }
}