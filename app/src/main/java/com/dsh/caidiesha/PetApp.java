package com.dsh.caidiesha;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 自己记崩溃日志：任何未捕获异常都会把完整堆栈写进 SharedPreferences，
 * 主界面里能直接看到 —— 这样即使手机不在电脑旁边，也能把真相带回来。
 */
public class PetApp extends Application {

    public static final String PREFS = "caidiesha";
    public static final String KEY_CRASH = "last_crash";
    private static final String TAG = "CaidieshaPet";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    save(PetApp.this, e, "线程 " + t.getName());
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    /** 主动记录（用于 catch 住的地方） */
    public static void save(Context c, Throwable e, String where) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            pw.println("[" + new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date()) + "] " + where);
            pw.println("设备: " + Build.MANUFACTURER + " " + Build.MODEL
                    + "  Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            e.printStackTrace(pw);
            pw.flush();
            String text = sw.toString();
            Log.e(TAG, text);
            SharedPreferences sp = c.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_CRASH, text).apply();
        } catch (Throwable ignored) {
        }
    }

    public static String last(Context c) {
        return c.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CRASH, "");
    }

    public static void clear(Context c) {
        c.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_CRASH).apply();
    }
}
