package com.carlyrix.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private TextView sizeDisplay, previewTime;
    private Button btnLock;
    private int currentTimeSize;
    private boolean isLocked;
    private int currentThemeColor = Color.parseColor("#00FFCC");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        currentTimeSize = prefs.getInt("pref_time_size", 50);
        isLocked = prefs.getBoolean("pref_time_locked", false);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0F0F0F);
        scroll.setFillViewport(true);

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 40, 40, 40);

        // 头部标题
        TextView title = new TextView(this);
        title.setText("CAR TIME DASHBOARD");
        title.setTextSize(22);
        title.setLetterSpacing(0.2f);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        title.setTextColor(currentThemeColor);
        title.setGravity(Gravity.CENTER);
        mainLayout.addView(title);

        // 预览窗口容器
        FrameLayout previewCard = new FrameLayout(this);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF1E1E1E);
        cardBg.setCornerRadius(20);
        cardBg.setStroke(2, 0xFF333333);
        previewCard.setBackground(cardBg);
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 220);
        previewLp.setMargins(0, 30, 0, 30);
        previewCard.setLayoutParams(previewLp);

        previewTime = new TextView(this);
        previewTime.setText(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date()));
        previewTime.setTextSize(currentTimeSize);
        previewTime.setTextColor(currentThemeColor);
        previewTime.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        previewTime.setGravity(Gravity.CENTER);
        previewCard.addView(previewTime);
        mainLayout.addView(previewCard);

        // 设置区域标题
        mainLayout.addView(createSectionLabel("功能控制"));

        // 控制按钮组
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setWeightSum(2);
        
        btnLock = createStyledButton("", v -> toggleLock());
        updateLockButton();
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, 140, 1);
        btnLp.setMargins(0, 0, 10, 0);
        btnLock.setLayoutParams(btnLp);
        
        Button btnReset = createStyledButton("重置位置", v -> {
            Intent i = new Intent(this, TimeOverlayService.class);
            i.setAction("ACTION_RESET_POSITION");
            startServiceWrapper(i);
        });
        btnReset.setLayoutParams(new LinearLayout.LayoutParams(0, 140, 1));
        
        btnRow.addView(btnLock);
        btnRow.addView(btnReset);
        mainLayout.addView(btnRow);

        mainLayout.addView(createSectionLabel("样式调节"));

        // 字体大小调节
        LinearLayout sizeCtrl = new LinearLayout(this);
        sizeCtrl.setGravity(Gravity.CENTER_VERTICAL);
        sizeCtrl.setPadding(20, 20, 20, 20);
        
        Button btnMin = createCircleButton("-", v -> updateSize(-4));
        Button btnAdd = createCircleButton("+", v -> updateSize(4));
        sizeDisplay = new TextView(this);
        sizeDisplay.setText("SIZE: " + currentTimeSize);
        sizeDisplay.setTextColor(Color.WHITE);
        sizeDisplay.setTextSize(18);
        sizeDisplay.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        sizeDisplay.setGravity(Gravity.CENTER);

        sizeCtrl.addView(btnMin);
        sizeCtrl.addView(sizeDisplay);
        sizeCtrl.addView(btnAdd);
        mainLayout.addView(sizeCtrl);

        // 权限引导
        mainLayout.addView(createPermButton("授权悬浮窗权限 (首次必点)", v -> requestOverlay()));
        mainLayout.addView(createPermButton("激活 24H 悬浮时钟", v -> startClockService()));

        scroll.addView(mainLayout);
        setContentView(scroll);
    }

    private TextView createSectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF666666);
        tv.setTextSize(14);
        tv.setPadding(10, 30, 0, 15);
        return tv;
    }

    private void toggleLock() {
        isLocked = !isLocked;
        prefs.edit().putBoolean("pref_time_locked", isLocked).apply();
        updateLockButton();
        Intent i = new Intent(this, TimeOverlayService.class);
        i.setAction("ACTION_UPDATE_TIME_LOCK");
        i.putExtra("locked", isLocked);
        startServiceWrapper(i);
    }

    private void updateSize(int delta) {
        currentTimeSize = Math.max(20, Math.min(150, currentTimeSize + delta));
        sizeDisplay.setText("SIZE: " + currentTimeSize);
        previewTime.setTextSize(currentTimeSize);
        prefs.edit().putInt("pref_time_size", currentTimeSize).apply();
        Intent i = new Intent(this, TimeOverlayService.class);
        i.setAction("ACTION_UPDATE_TIME_SIZE");
        i.putExtra("size", currentTimeSize);
        startServiceWrapper(i);
    }

    private void updateLockButton() {
        btnLock.setText(isLocked ? "点击解锁拖动" : "点击锁定位置");
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(15);
        bg.setColor(isLocked ? 0xFF388E3C : 0xFF1976D2);
        btnLock.setBackground(bg);
    }

    private void startClockService() {
        Intent i = new Intent(this, TimeOverlayService.class);
        startServiceWrapper(i);
        Toast.makeText(this, "高精度时钟引擎已就绪", Toast.LENGTH_SHORT).show();
    }

    private void startServiceWrapper(Intent i) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
            else startService(i);
        } catch (Exception e) {
            Log.e("CarTime", "Service Error: " + e.getMessage());
        }
    }

    private Button createStyledButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        return b;
    }

    private Button createCircleButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(22);
        b.setOnClickListener(l);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFF333333);
        b.setBackground(bg);
        b.setTextColor(currentThemeColor);
        b.setLayoutParams(new LinearLayout.LayoutParams(110, 110));
        return b;
    }

    private Button createPermButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        b.setTextColor(0xFF0F0F0F);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(15);
        bg.setColor(currentThemeColor);
        b.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120);
        lp.setMargins(0, 30, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    private void requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } else Toast.makeText(this, "权限校验通过", Toast.LENGTH_SHORT).show();
    }
}