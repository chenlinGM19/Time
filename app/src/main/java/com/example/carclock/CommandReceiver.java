package com.example.carclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class CommandReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        Log.d("CarClock", "Received Tasker Intent: " + action);

        // Forward the intent to the Service
        Intent serviceIntent = new Intent(context, FloatingClockService.class);
        serviceIntent.setAction(action);

        // Pass any extras if necessary
        if (intent.getExtras() != null) {
            serviceIntent.putExtras(intent.getExtras());
        }

        // Properly start the service from the background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}