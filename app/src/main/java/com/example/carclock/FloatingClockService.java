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
    public static final String ACTION_SET_OPACITY = "com.example.carclock.ACTION_SET_OPACITY";
    
    public static final String EXTRA_OPACITY = "extra_opacity"; // Int 0-100
    
    public static final String ACTION_BROADCAST_CLICK = "com.example.carclock.CLOCK_CLICK";
    public static final String ACTION_BROADCAST_DOUBLE_CLICK = "com.example.carclock.CLOCK_DOUBLE_CLICK";
    public static final String ACTION_BROADCAST_LONG_PRESS = "com.example.carclock.CLOCK_LONG_PRESS";

    private static final String PREFS_NAME = "CarClockPrefs";
    private static final String KEY_X = "pos_x";
    private static final String KEY_Y = "pos_y";
    private static final String KEY_PASSTHROUGH = "passthrough";
    private static final String KEY_SECONDS = "show_seconds";
    private static final String KEY_BG_VISIBLE = "bg_visible";
    private static final String KEY_BOLD = "is_bold";
    private static final String KEY_VERTICAL = "is_vertical";
    private static final String KEY_TEXT_SIZE = "text_size";
    private static final String KEY_STYLE_INDEX = "style_index";
    private static final String KEY_SHOW_TOASTS = "show_toasts";
    private static final String KEY_IS_VISIBLE = "is_visible";
    private static final String KEY_OPACITY = "bg_opacity";

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
    private boolean isVisible = true;
    private int opacity = 100; // 0-100

    private float currentTextSize = 24f;
    private int currentStyleIndex = 0;
    
    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int clickCount = 0;
    private boolean isLongPressTriggered = false;
    
    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            if (tvTime != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                String timeText = sdf.format(new Date());
                if (!showSeconds) timeText = timeText.substring(0, 5);
                if (isVertical) timeText = timeText.replace(":", "\n");
                tvTime.setText(timeText);
            }
            mainHandler.postDelayed(this, 1000);
        }
    };
    
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
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        loadPreferences();
        startForegroundService();
        initializeFloatingWindow();
    }

    private void loadPreferences() {
        showToasts = prefs.getBoolean(KEY_SHOW_TOASTS, true);
        isPassthrough = prefs.getBoolean(KEY_PASSTHROUGH, false);
        showSeconds = prefs.getBoolean(KEY_SECONDS, true);
        isBgVisible = prefs.getBoolean(KEY_BG_VISIBLE, true);
        isBold = prefs.getBoolean(KEY_BOLD, false);
        isVertical = prefs.getBoolean(KEY_VERTICAL, false);
        currentTextSize = prefs.getFloat(KEY_TEXT_SIZE, 24f);
        currentStyleIndex = prefs.getInt(KEY_STYLE_INDEX, 0);
        isVisible = prefs.getBoolean(KEY_IS_VISIBLE, true);
        opacity = prefs.getInt(KEY_OPACITY, 100);
    }

    private void savePosition(int x, int y) {
        prefs.edit().putInt(KEY_X, x).putInt(KEY_Y, y).apply();
    }

    private void saveAllSettings() {
        prefs.edit()
            .putBoolean(KEY_PASSTHROUGH, isPassthrough)
            .putBoolean(KEY_SECONDS, showSeconds)
            .putBoolean(KEY_BG_VISIBLE, isBgVisible)
            .putBoolean(KEY_BOLD, isBold)
            .putBoolean(KEY_VERTICAL, isVertical)
            .putFloat(KEY_TEXT_SIZE, currentTextSize)
            .putInt(KEY_STYLE_INDEX, currentStyleIndex)
            .putBoolean(KEY_IS_VISIBLE, isVisible)
            .putInt(KEY_OPACITY, opacity)
            .apply();
    }

    private void startForegroundService() {
        String channelId = "floating_clock_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Floating Clock", NotificationManager.IMPORTANCE_LOW);
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

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | 
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        
        if (isPassthrough) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag, flags, PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        params.x = prefs.getInt(KEY_X, (metrics.widthPixels / 2) - 100);
        params.y = prefs.getInt(KEY_Y, (metrics.heightPixels / 2) - 50);

        tvTime.setTextSize(currentTextSize);
        tvTime.setTypeface(null, isBold ? Typeface.BOLD : Typeface.NORMAL);
        applyStyle(currentStyleIndex);
        rootContainer.setAlpha(opacity / 100f);
        
        if (!isVisible) floatingView.setVisibility(View.GONE);

        windowManager.addView(floatingView, params);
        mainHandler.post(updateTimeRunnable);
        setupTouchListener();
    }

    private void resetPositionToCenter() {
        if (floatingView == null) return;
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        params.x = (metrics.widthPixels - floatingView.getWidth()) / 2;
        params.y = (metrics.heightPixels - floatingView.getHeight()) / 2;
        try {
            windowManager.updateViewLayout(floatingView, params);
            savePosition(params.x, params.y);
        } catch (Exception ignored) {}
    }

    private void setupTouchListener() {
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private static final int THRESHOLD = 15;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isPassthrough) return false; 
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        isLongPressTriggered = false;
                        mainHandler.postDelayed(longPressRunnable, 800);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = Math.abs(event.getRawX() - initialTouchX);
                        float dy = Math.abs(event.getRawY() - initialTouchY);
                        if (dx > THRESHOLD || dy > THRESHOLD) {
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
                        if (!isLongPressTriggered) {
                            float udx = Math.abs(event.getRawX() - initialTouchX);
                            float udy = Math.abs(event.getRawY() - initialTouchY);
                            if (udx < THRESHOLD && udy < THRESHOLD) {
                                clickCount++;
                                if (clickCount == 1) mainHandler.postDelayed(singleClickRunnable, 300);
                                else if (clickCount == 2) {
                                    mainHandler.removeCallbacks(singleClickRunnable);
                                    clickCount = 0;
                                    sendBroadcastAction(ACTION_BROADCAST_DOUBLE_CLICK, R.string.tasker_double_click_sent);
                                }
                            } else {
                                savePosition(params.x, params.y);
                            }
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void sendBroadcastAction(String action, int toastResId) {
        sendBroadcast(new Intent(action));
        if (showToasts) Toast.makeText(this, toastResId, Toast.LENGTH_SHORT).show();
    }
    
    private void openMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if (ACTION_SET_OPACITY.equals(intent.getAction())) {
                opacity = intent.getIntExtra(EXTRA_OPACITY, opacity);
            }
            handleAction(intent.getAction());
        }
        return START_STICKY;
    }
    
    private void handleAction(String action) {
        if (floatingView == null) return;
        switch (action) {
            case ACTION_TOGGLE_VISIBILITY: toggleVisibility(); break;
            case ACTION_TOGGLE_PASSTHROUGH: togglePassthrough(); break;
            case ACTION_INCREASE_SIZE: changeSize(5f); break;
            case ACTION_DECREASE_SIZE: changeSize(-5f); break;
            case ACTION_CHANGE_STYLE: cycleStyle(); break;
            case ACTION_TOGGLE_SECONDS: showSeconds = !showSeconds; break;
            case ACTION_TOGGLE_BG: isBgVisible = !isBgVisible; applyStyle(currentStyleIndex); break;
            case ACTION_TOGGLE_WEIGHT: isBold = !isBold; tvTime.setTypeface(null, isBold ? Typeface.BOLD : Typeface.NORMAL); break;
            case ACTION_RESET_POSITION: resetPositionToCenter(); break;
            case ACTION_TOGGLE_ORIENTATION: isVertical = !isVertical; break;
            case ACTION_TOGGLE_TOASTS:
                showToasts = !showToasts;
                prefs.edit().putBoolean(KEY_SHOW_TOASTS, showToasts).apply();
                Toast.makeText(this, "Tips: " + (showToasts ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
                break;
            case ACTION_SET_VISIBLE: isVisible = true; floatingView.setVisibility(View.VISIBLE); break;
            case ACTION_SET_BLOCKING:
                isPassthrough = false;
                params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                windowManager.updateViewLayout(floatingView, params);
                break;
            case ACTION_SET_OPACITY:
                rootContainer.setAlpha(opacity / 100f);
                break;
        }
        saveAllSettings();
        refreshTimeImmediately();
    }
    
    private void refreshTimeImmediately() {
        mainHandler.removeCallbacks(updateTimeRunnable);
        mainHandler.post(updateTimeRunnable);
    }

    private void toggleVisibility() {
        isVisible = (floatingView.getVisibility() != View.VISIBLE);
        floatingView.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void togglePassthrough() {
        isPassthrough = !isPassthrough;
        if (isPassthrough) params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        windowManager.updateViewLayout(floatingView, params);
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
            tvTime.setTextColor(index == 1 ? Color.BLACK : (index == 3 ? Color.GREEN : Color.WHITE));
            return;
        }
        Drawable bg = ContextCompat.getDrawable(this, R.drawable.bg_clock_rounded).mutate();
        rootContainer.setBackground(bg);
        if (bg instanceof GradientDrawable) {
            GradientDrawable g = (GradientDrawable) bg;
            switch (index) {
                case 0: g.setColor(Color.parseColor("#99000000")); tvTime.setTextColor(Color.WHITE); break;
                case 1: g.setColor(Color.parseColor("#99FFFFFF")); tvTime.setTextColor(Color.BLACK); break;
                case 2: g.setColor(Color.parseColor("#FF2196F3")); tvTime.setTextColor(Color.WHITE); break;
                case 3: g.setColor(Color.parseColor("#FF000000")); g.setStroke(2, Color.GREEN); tvTime.setTextColor(Color.GREEN); break;
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