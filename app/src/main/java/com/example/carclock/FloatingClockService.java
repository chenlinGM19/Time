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
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingClockService extends Service {

    public static final String ACTION_TOGGLE_VISIBILITY = "ACTION_TOGGLE_VISIBILITY";
    public static final String ACTION_TOGGLE_PASSTHROUGH = "ACTION_TOGGLE_PASSTHROUGH";
    public static final String ACTION_INCREASE_SIZE = "ACTION_INCREASE_SIZE";
    public static final String ACTION_DECREASE_SIZE = "ACTION_DECREASE_SIZE";
    public static final String ACTION_CHANGE_STYLE = "ACTION_CHANGE_STYLE";
    public static final String ACTION_TOGGLE_SECONDS = "ACTION_TOGGLE_SECONDS";
    public static final String ACTION_TOGGLE_BG = "ACTION_TOGGLE_BG";
    public static final String ACTION_TOGGLE_WEIGHT = "ACTION_TOGGLE_WEIGHT";
    
    // Broadcast Actions for Tasker
    public static final String ACTION_BROADCAST_CLICK = "com.example.carclock.CLOCK_CLICK";
    public static final String ACTION_BROADCAST_DOUBLE_CLICK = "com.example.carclock.CLOCK_DOUBLE_CLICK";
    public static final String ACTION_BROADCAST_LONG_PRESS = "com.example.carclock.CLOCK_LONG_PRESS";

    private WindowManager windowManager;
    private View floatingView;
    private TextView tvTime;
    private View rootContainer;
    private WindowManager.LayoutParams params;

    private boolean isPassthrough = false;
    private boolean showSeconds = true;
    private boolean isBgVisible = true;
    private boolean isBold = false;

    private float currentTextSize = 24f;
    private int currentStyleIndex = 0;
    
    // Touch Handling Variables
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int clickCount = 0;
    private boolean isLongPressTriggered = false;
    
    // Time Update Runnable
    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            if (tvTime != null) {
                String pattern = showSeconds ? "HH:mm:ss" : "HH:mm";
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
                tvTime.setText(sdf.format(new Date()));
            }
            mainHandler.postDelayed(this, 1000);
        }
    };
    
    // Click Handling Runnables
    private final Runnable singleClickRunnable = () -> {
        clickCount = 0;
        sendBroadcastAction(ACTION_BROADCAST_CLICK, R.string.tasker_click_sent);
    };
    
    private final Runnable longPressRunnable = () -> {
        isLongPressTriggered = true;
        sendBroadcastAction(ACTION_BROADCAST_LONG_PRESS, R.string.tasker_long_press_sent);
        openMainActivity();
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundService();
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

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_clock, null);
        tvTime = floatingView.findViewById(R.id.tv_clock_time);
        rootContainer = floatingView.findViewById(R.id.root_container);

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
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, 
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 50;
        params.y = 50;

        windowManager.addView(floatingView, params);
        mainHandler.post(updateTimeRunnable);
        setupTouchListener();
    }

    private void setupTouchListener() {
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private static final int CLICK_THRESHOLD = 15; 
            private static final int LONG_PRESS_TIMEOUT = 800; // ms

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isPassthrough) return false; 

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        
                        // Reset states
                        isLongPressTriggered = false;
                        mainHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float diffX = Math.abs(event.getRawX() - initialTouchX);
                        float diffY = Math.abs(event.getRawY() - initialTouchY);
                        
                        // If moved significantly, cancel long press
                        if (diffX > CLICK_THRESHOLD || diffY > CLICK_THRESHOLD) {
                            mainHandler.removeCallbacks(longPressRunnable);
                            
                            // Only update position (drag) if not long pressed yet
                            if (!isLongPressTriggered) {
                                params.x = initialX + (int) (event.getRawX() - initialTouchX);
                                params.y = initialY + (int) (event.getRawY() - initialTouchY);
                                windowManager.updateViewLayout(floatingView, params);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        mainHandler.removeCallbacks(longPressRunnable);

                        // If long press triggered, consume the event, do not process as click
                        if (isLongPressTriggered) {
                            return true; 
                        }

                        float upDiffX = Math.abs(event.getRawX() - initialTouchX);
                        float upDiffY = Math.abs(event.getRawY() - initialTouchY);

                        // It is a click if movement is small
                        if (upDiffX < CLICK_THRESHOLD && upDiffY < CLICK_THRESHOLD) {
                            clickCount++;
                            if (clickCount == 1) {
                                // Wait 300ms to see if it's a double click
                                mainHandler.postDelayed(singleClickRunnable, 300);
                            } else if (clickCount == 2) {
                                // It is a double click
                                mainHandler.removeCallbacks(singleClickRunnable);
                                clickCount = 0;
                                sendBroadcastAction(ACTION_BROADCAST_DOUBLE_CLICK, R.string.tasker_double_click_sent);
                            }
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void sendBroadcastAction(String action, int toastResId) {
        Intent intent = new Intent(action);
        sendBroadcast(intent);
        Toast.makeText(this, toastResId, Toast.LENGTH_SHORT).show();
    }
    
    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
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
                    changeSize(5f); 
                    break;
                case ACTION_DECREASE_SIZE:
                    changeSize(-5f);
                    break;
                case ACTION_CHANGE_STYLE:
                    cycleStyle();
                    break;
                case ACTION_TOGGLE_SECONDS:
                    showSeconds = !showSeconds;
                    mainHandler.removeCallbacks(updateTimeRunnable);
                    mainHandler.post(updateTimeRunnable);
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
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        windowManager.updateViewLayout(floatingView, params);
    }

    private void toggleBackground() {
        isBgVisible = !isBgVisible;
        if (isBgVisible) {
            applyStyle(currentStyleIndex); 
        } else {
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
        tvTime.setTextSize(currentTextSize);
    }

    private void cycleStyle() {
        currentStyleIndex = (currentStyleIndex + 1) % 4;
        applyStyle(currentStyleIndex);
    }

    private void applyStyle(int index) {
        if (!isBgVisible) {
            rootContainer.setBackground(null);
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

        Drawable bg = ContextCompat.getDrawable(this, R.drawable.bg_clock_rounded);
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
        mainHandler.removeCallbacks(updateTimeRunnable);
    }
}