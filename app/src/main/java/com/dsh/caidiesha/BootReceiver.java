package com.dsh.caidiesha;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机自动把彩蝶鲨放出来（仅当上次是开着的）。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(a)) return;

        boolean wasRunning = context.getSharedPreferences(FloatingPetService.PREFS, Context.MODE_PRIVATE)
                .getBoolean(FloatingPetService.KEY_RUNNING, false);
        if (wasRunning && FloatingPetService.canDraw(context)) {
            FloatingPetService.start(context);
        }
    }
}
