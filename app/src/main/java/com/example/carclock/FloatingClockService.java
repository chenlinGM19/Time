package com.example.carclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingClockService extends Service {

    // Fully qualified actions
    public static final String ACTION_TOGGLE_VISIBILITY = "com.example.carclock.ACTION_TOGGLE_VISIBILITY";
    public static final String ACTION_TOGGLE_PASSTHROUGH = "com.example.carclock.ACTION_TOGGLE_PASSTHROUGH";
    public static final String ACTION_INCREASE_SIZE = "com.example.carclock.ACTION_INCREASE_SIZE";
    public static final String ACTION_DECREASE_SIZE = "com.example.carclock.ACTION_DECREASE_SIZE";
    public static final String ACTION_CHANGE_STYLE = "com.example.carclock.ACTION_CHANGE_STYLE";
    public static final String ACTION_TOGGLE_SECONDS = "com.example.carclock.ACTION_TOGGLE_SECONDS";
    public static final String ACTION_TOGGLE_BG = "com.example.carclock.ACTION_TOGGLE_BG";
    public static final String ACTION_TOGGLE_WEIGHT = "com.example.carclock.ACTION_TOGGLE_WEIGHT";
    public static final String ACTION_RESET_POSITION = "com.example.carclock.ACTION_RESET_POSITION";
    public static final String ACTION_TOGGLE_ORIENTATION = "com.example.carclock.ACTION_TOGGLE_ORIENTATION";
    public static final String ACTION_TOGGLE_TOASTS = "com.example.carclock.ACTION_TOGGLE_TOASTS";
    public static final String ACTION_SET_VISIBLE = "com.example.carclock.ACTION_SET_VISIBLE";
    public static final String ACTION_SET_BLOCKING = "com.example.carclock.ACTION_SET_BLOCKING";
    
    // Broadcast Actions for Tasker (Events)
    public static final String ACTION_BROADCAST_CLICK = "com.example.carclock.CLOCK_CLICK";
    public static final String ACTION_BROADCAST_DOUBLE_CLICK = "com.example.carclock.CLOCK_DOUBLE_CLICK";
    public static final String ACTION_BROADCAST_LONG_PRESS = "com.example.carclock.CLOCK_LONG_PRESS";

    private static final String PREFS_NAME = "CarClockPrefs";
    private static final String KEY_SHOW_TOASTS = "show_toasts";

    private WindowManager windowManager;
    private View floatingView;
    private TextView tvTime;
    private View rootContainer;
    private WindowManager.LayoutParams params;

    private boolean isPassthrough = false;
    private boolean showSeconds = true;
    private boolean isBgVisible = true;
    private boolean isBold = false;
    private boolean isVertical = false;
    private boolean showToasts = true;

    private float currentTextSize = 24f;
    private int currentStyleIndex = 0;
    
    private SharedPreferences prefs;

    // Touch Handling Variables
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int clickCount = 0;
    private boolean isLongPressTriggered = false;
    
    // Time Update Runnable
    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            if (tvTime != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                String timeText = sdf.format(new Date());
                
                if (!showSeconds) {
                    timeText = timeText.substring(0, 5); // HH:mm
                }
                
                if (isVertical) {
                    timeText = timeText.replace(":", "\n");
                }
                
                tvTime.setText(timeText);
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
        // Load preferences
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        showToasts = prefs.getBoolean(KEY_SHOW_TOASTS, true);

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
        
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        
        params.x = (screenWidth / 2) - 100;
        params.y = (screenHeight / 2) - 50;

        windowManager.addView(floatingView, params);
        
        floatingView.post(() -> resetPositionToCenter());
        
        mainHandler.post(updateTimeRunnable);
        setupTouchListener();
    }

    private void resetPositionToCenter() {
        if (floatingView == null) return;
        
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        
        params.x = (screenWidth - floatingView.getWidth()) / 2;
        params.y = (screenHeight - floatingView.getHeight()) / 2;
        
        try {
            windowManager.updateViewLayout(floatingView, params);
        } catch (Exception e) {
            // View might be detached
        }
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
                        
                        isLongPressTriggered = false;
                        mainHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float diffX = Math.abs(event.getRawX() - initialTouchX);
                        float diffY = Math.abs(event.getRawY() - initialTouchY);
                        
                        if (diffX > CLICK_THRESHOLD || diffY > CLICK_THRESHOLD) {
                            mainHandler.removeCallbacks(longPressRunnable);
                            
                            if (!isLongPressTriggered) {
                                params.x = initialX + (int) (event.getRawX() - initialTouchX);
                                params.y = initialY + (int) (event.getRawY() - initialTouchY);
                                windowManager.updateViewLayout(floatingView, params);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        mainHandler.removeCallbacks(longPressRunnable);

                        if (isLongPressTriggered) {
                            return true; 
                        }

                        float upDiffX = Math.abs(event.getRawX() - initialTouchX);
                        float upDiffY = Math.abs(event.getRawY() - initialTouchY);

                        if (upDiffX < CLICK_THRESHOLD && upDiffY < CLICK_THRESHOLD) {
                            clickCount++;
                            if (clickCount == 1) {
                                mainHandler.postDelayed(singleClickRunnable, 300);
                            } else if (clickCount == 2) {
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
        
        if (showToasts) {
            Toast.makeText(this, toastResId, Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            handleAction(intent.getAction());
        }
        // Return START_STICKY to ensure service restarts if killed
        return START_STICKY;
    }
    
    // Logic handler
    private void handleAction(String action) {
        if (floatingView == null) return;

        switch (action) {
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
                refreshTimeImmediately();
                break;
            case ACTION_TOGGLE_BG:
                toggleBackground();
                break;
            case ACTION_TOGGLE_WEIGHT:
                toggleFontWeight();
                break;
            case ACTION_RESET_POSITION:
                resetPositionToCenter();
                break;
            case ACTION_TOGGLE_ORIENTATION:
                isVertical = !isVertical;
                refreshTimeImmediately();
                break;
            case ACTION_TOGGLE_TOASTS:
                showToasts = !showToasts;
                prefs.edit().putBoolean(KEY_SHOW_TOASTS, showToasts).apply();
                Toast.makeText(this, "Tips: " + (showToasts ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
                break;
            case ACTION_SET_VISIBLE:
                if (floatingView.getVisibility() != View.VISIBLE) {
                    floatingView.setVisibility(View.VISIBLE);
                }
                break;
            case ACTION_SET_BLOCKING:
                if (isPassthrough) {
                    isPassthrough = false;
                    params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    windowManager.updateViewLayout(floatingView, params);
                }
                break;
        }
    }
    
    private void refreshTimeImmediately() {
        mainHandler.removeCallbacks(updateTimeRunnable);
        mainHandler.post(updateTimeRunnable);
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