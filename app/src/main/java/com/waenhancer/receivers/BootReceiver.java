package com.waenhancer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.waenhancer.utils.LogManager;
import com.waenhancer.services.LogService;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            

        }
    }
}
