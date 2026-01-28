package com.carlyrix.app;

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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimeOverlayService extends Service {

    private WindowManager windowManager;
    private TextView timeView;
    private WindowManager.LayoutParams params;
    
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat fullFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    private final SimpleDateFormat shortFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    
    private boolean showSeconds = true;
    private int currentFontSize = 50;
    private int currentTextColor = Color.parseColor("#00FFCC");
    private boolean isLocked = false;
    private boolean isVisible = true;

    private float startX, startY;
    private float initialTouchX, initialTouchY;
    private long lastClickTime = 0;

    private static final String CHANNEL_ID = "carlyrix_time_channel";

    private final Runnable timeUpdater = new Runnable() {
        @Override
        public void run() {
            if (timeView != null && isVisible) {
                timeView.setText(showSeconds ? fullFormat.format(new Date()) : shortFormat.format(new Date()));
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        currentFontSize = prefs.getInt("pref_time_size", 50);
        isLocked = prefs.getBoolean("pref_time_locked", false);
        showSeconds = prefs.getBoolean("pref_show_seconds", true);

        startForegroundNotification();
        createTimeOverlay();
        handler.post(timeUpdater);
    }

    private void startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Time Engine", NotificationManager.IMPORTANCE_MIN);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("时钟服务运行中")
                    .setSmallIcon(android.R.drawable.ic_menu_recent_history).build();
            startForeground(2, notification);
        }
    }

    private void createTimeOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        timeView = new TextView(this);
        
        updateStyle();
        
        int type = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (isLocked) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                type, flags, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 100;

        setupTouchListener();
        windowManager.addView(timeView, params);
    }

    private void setupTouchListener() {
        timeView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isLocked) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x; startY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = (int) (startX + (event.getRawX() - initialTouchX));
                        params.y = (int) (startY + (event.getRawY() - initialTouchY));
                        windowManager.updateViewLayout(timeView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        float moved = Math.abs(event.getRawX() - initialTouchX) + Math.abs(event.getRawY() - initialTouchY);
                        if (moved < 10) {
                            long now = System.currentTimeMillis();
                            if (now - lastClickTime < 300) cycleColors();
                            else showSeconds = !showSeconds;
                            lastClickTime = now;
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void cycleColors() {
        int[] colors = {Color.parseColor("#00FFCC"), Color.GREEN, Color.WHITE, Color.YELLOW, Color.RED};
        for(int i=0; i<colors.length; i++) {
            if (currentTextColor == colors[i]) {
                currentTextColor = colors[(i + 1) % colors.length];
                break;
            }
        }
        updateStyle();
    }

    private void updateStyle() {
        if (timeView != null) {
            timeView.setTextColor(currentTextColor);
            timeView.setTextSize(currentFontSize);
            timeView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            timeView.setShadowLayer(15, 0, 0, Color.BLACK);
            timeView.setPadding(30, 15, 30, 15);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("ACTION_UPDATE_TIME_SIZE".equals(action)) {
                currentFontSize = intent.getIntExtra("size", currentFontSize);
                updateStyle();
            } else if ("ACTION_UPDATE_TIME_LOCK".equals(action)) {
                isLocked = intent.getBooleanExtra("locked", false);
                if (isLocked) params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                else params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                windowManager.updateViewLayout(timeView, params);
            } else if ("ACTION_RESET_POSITION".equals(action)) {
                params.x = 100; params.y = 100;
                windowManager.updateViewLayout(timeView, params);
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timeUpdater);
        if (windowManager != null && timeView != null) windowManager.removeView(timeView);
    }
}