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
        Log.d("CarClock", "Received Broadcast: " + action);

        Intent serviceIntent = new Intent(context, FloatingClockService.class);
        
        // If it's boot completed, we just start the service normally
        // Otherwise, forward the specific action command
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && 
            !Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            serviceIntent.setAction(action);
        }

        if (intent.getExtras() != null) {
            serviceIntent.putExtras(intent.getExtras());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}