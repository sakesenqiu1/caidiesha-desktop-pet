package com.dsh.caidiesha;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;

/**
 * 让彩蝶鲨漂在屏幕最上层的前台服务。
 *
 * 窗口策略：**窗口只有彩蝶鲨那么大，并且跟着它移动**。
 * 早先版本用铺满全屏的窗口，等于「窗口外部」不存在，FLAG_NOT_TOUCH_MODAL 形同失效，
 * 结果整个桌面都点不了 —— 已修正为跟随式小窗口。
 *
 * 窗口尺寸/位置做了三重兜底，任何一步算不出来都不会变成 0 大小：
 *   1) 屏幕尺寸拿不到就用 1080x2340 兜底
 *   2) 窗口宽高至少 96dp（绝对不会出现 1×1 的隐形窗口）
 *   3) 算出来的矩形必须和屏幕有交集，否则强制夹回屏幕内
 */
@SuppressWarnings("deprecation")
public class FloatingPetService extends Service implements PetView.Host {

    public static final String ACTION_STOP = "com.dsh.caidiesha.STOP";
    public static final String ACTION_DEBUG = "com.dsh.caidiesha.DEBUG";
    public static final String PREFS = "caidiesha";
    public static final String KEY_RUNNING = "pet_running";
    public static final String KEY_STATUS = "pet_status";

    private static final String CH_ID = "caidiesha_pet";
    private static final String TAG = "CaidieshaPet";

    private WindowManager wm;
    private PetView petView;
    private WindowManager.LayoutParams lp;
    private boolean receiverOn;
    private boolean lastAttached;
    private boolean notified;
    private int attachFails;
    private boolean debugOn;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int screenW = 1080, screenH = 2340;
    private int statusTop;
    private float density = 2.0f;

    public static boolean canDraw(Context c) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(c);
    }

    public static void start(Context c) {
        Intent i = new Intent(c, FloatingPetService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable t) {
            Log.w(TAG, "startForegroundService failed", t);
        }
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, FloatingPetService.class));
    }

    public static String status(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STATUS, "");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        CaidieshaDex.init(this);
        try {
            onCreateInner();
        } catch (Throwable t) {
            setStatus("服务初始化崩溃：" + t);
            PetApp.save(this, t, "FloatingPetService.onCreate");
        }
    }

    private void onCreateInner() {
        density = getResources().getDisplayMetrics().density;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        readScreenMetrics();
        startForeground(1, buildNotification());
        boolean ok = addPetWindow();
        if (!ok) postDelayed(retryAttach, 1200);
        postDelayed(watchdog, 12000);
        registerScreenReceiver();
        prefs().edit().putBoolean(KEY_RUNNING, true).apply();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            if (petView == null && !addPetWindow()) postDelayed(retryAttach, 1500);
        } catch (Throwable t) {
            setStatus("挂载崩溃：" + t);
            PetApp.save(this, t, "onStartCommand/addPetWindow");
        }
        return START_STICKY;
    }

    private final Runnable retryAttach = new Runnable() {
        @Override
        public void run() {
            if (petView == null && !addPetWindow()) postDelayed(this, 4000);
        }
    };

    /**
     * 最后兜底：如果小窗口怎么都显示不出来（个别 ROM 对 TYPE_APPLICATION_OVERLAY 的可变尺寸
     * 支持有问题），就退回「铺满屏幕的窗口 + 视图内自己判断命中」。
     * 这条路径的代价是：点屏幕别处时代码仍然会穿透（返回 false 交给下层），
     * 只是窗口本身覆盖全屏 —— 属于能用优先的降级方案。
     */
    private void ensureVisibleOrFallback() {
        if (petView == null || lp == null || wm == null) return;
        if (lp.width >= Math.round(96f * density)) return;   // 尺寸正常，不用兜底
        setStatus("窗口尺寸异常，启用全屏兜底");
        fallbackFullScreen = true;
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        lp.x = 0;
        lp.y = 0;
        try {
            wm.updateViewLayout(petView, lp);
        } catch (Throwable ignored) {
        }
    }

    /** 进入全屏兜底后，不要再让 PetView 缩窗口，否则会来回打架 */
    private boolean fallbackFullScreen;

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            boolean attached = petView != null;
            if (!attached) attached = addPetWindow();
            else {
                refreshMetricsIfChanged();
                ensureVisibleOrFallback();
            }
            updateNotification(attached);
            postDelayed(this, 12000);
        }
    };

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void setStatus(String s) {
        try {
            prefs().edit().putString(KEY_STATUS, s).apply();
        } catch (Throwable ignored) {
        }
        Log.i(TAG, s);
    }

    // ---------------- 屏幕尺寸（多路兜底） ----------------

    private void readScreenMetrics() {
        int w = 0, h = 0;
        // 注意：绝对不要在这里用 wm.getCurrentWindowMetrics() / getMaximumWindowMetrics()，
        // 它们要求「可视化 Context」（Activity / WindowContext），用 Service 的 Context 调会抛异常。
        // 这里只用 Display 和 Resources 两条最稳的路。
        if (w <= 0) {
            try {
                Display d = wm.getDefaultDisplay();
                DisplayMetrics dm = new DisplayMetrics();
                d.getRealMetrics(dm);
                if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                    w = dm.widthPixels;
                    h = dm.heightPixels;
                }
            } catch (Throwable t) {
                Log.w(TAG, "getRealMetrics failed", t);
            }
        }
        // 4) 资源里的显示尺寸
        if (w <= 0) {
            try {
                DisplayMetrics dm = getResources().getDisplayMetrics();
                if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                    w = dm.widthPixels;
                    h = dm.heightPixels;
                }
            } catch (Throwable ignored) {
            }
        }
        // 5) 硬兜底：绝不允许 0
        if (w <= 0 || h <= 0) {
            w = 1080;
            h = 2340;
            setStatus("屏幕尺寸取不到，用默认 1080x2340");
        }
        screenW = w;
        screenH = h;
        statusTop = Math.round(24f * density);
    }

    private void refreshMetricsIfChanged() {
        int oldW = screenW, oldH = screenH;
        readScreenMetrics();
        if ((oldW != screenW || oldH != screenH) && petView != null) {
            petView.setup(screenW, screenH, statusTop, screenW * 0.5f, screenH * 0.42f);
        }
    }

    // ---------------- 悬浮窗 ----------------

    /** 诊断模式：用同一个窗口画「不可能看不见」的东西，用来定位是哪一环坏了 */
    /** @return 是否成功挂上悬浮窗 */
    private boolean addPetWindow() {
        if (petView != null) return true;
        if (!canDraw(this)) {
            setStatus("缺少「显示在其他应用上层」权限");
            return false;
        }
        readScreenMetrics();

        petView = new PetView(this, this);
        petView.setup(screenW, screenH, statusTop, screenW * 0.5f, screenH * 0.42f);

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        lp = new WindowManager.LayoutParams(
                0, 0,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                flags,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;

        // 初次挂载：窗口必然覆盖屏幕中央的那块区域，尺寸有下限
        int minW = Math.round(96f * density);
        int w = Math.max(minW, Math.round(petView.getPetWidth() + 72f * density));
        int h = Math.max(minW, Math.round(petView.getPetHeight() + 80f * density));
        w = Math.min(w, screenW);
        h = Math.min(h, screenH);
        lp.width = w;
        lp.height = h;
        lp.x = clampInt(Math.round(screenW * 0.5f - w / 2f), 0, Math.max(0, screenW - w));
        lp.y = clampInt(Math.round(screenH * 0.42f - h / 2f), 0, Math.max(0, screenH - h));
        lp.setTitle("彩蝶鲨");

        try {
            wm.addView(petView, lp);
            attachFails = 0;
            setStatus("已挂载悬浮窗 " + lp.width + "x" + lp.height + " @" + lp.x + "," + lp.y
                    + "（屏幕 " + screenW + "x" + screenH + "）");
            if (!lastAttached) {
                lastAttached = true;
                updateNotification(true);
            }
            final PetView v = petView;
            v.post(new Runnable() {
                @Override
                public void run() {
                    v.forcePushGeometry();
                }
            });
            return true;
        } catch (Throwable t) {
            petView = null;
            attachFails++;
            setStatus("挂载失败（第 " + attachFails + " 次）：" + t.getClass().getSimpleName()
                    + " " + t.getMessage());
            Log.e(TAG, "addView failed", t);
            return false;
        }
    }

    private void removePetWindow() {
        if (petView != null && wm != null) {
            try {
                wm.removeView(petView);
            } catch (Throwable ignored) {
            }
            petView = null;
        }
    }

    // ---------------- PetView.Host ----------------

    @Override
    public void onWindowGeometry(int x, int y, int w, int h, boolean fullWidth) {
        if (petView == null || lp == null || wm == null) return;
        if (fallbackFullScreen) return;   // 已进入全屏兜底，保持满屏窗口

        // 三重兜底：尺寸下限 + 屏幕范围 + 边界夹取，绝不出现 1x1 或者跑到屏幕外
        int minW = Math.round(96f * density);
        int minH = Math.round(96f * density);
        if (w < minW) w = minW;
        if (h < minH) h = minH;
        if (w > screenW) w = screenW;
        if (h > screenH) h = screenH;

        Rect bounds = new Rect(0, 0, screenW, screenH);
        x = clampInt(x, 0, Math.max(0, bounds.width() - w));
        y = clampInt(y, 0, Math.max(0, bounds.height() - h));

        if (lp.x == x && lp.y == y && lp.width == w && lp.height == h) return;
        lp.x = x;
        lp.y = y;
        lp.width = w;
        lp.height = h;
        try {
            wm.updateViewLayout(petView, lp);
        } catch (Throwable t) {
            Log.w(TAG, "updateViewLayout failed", t);
        }
    }

    @Override
    public void onRequestStop() {
        stopSelf();
    }

    @Override
    public void onOpenInfo() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(MainActivity.EXTRA_SHOW_INFO, true);
        try {
            startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    private static int clampInt(int v, int lo, int hi) {
        if (hi < lo) return lo;
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ---------------- 息屏 / 亮屏 ----------------

    private void registerScreenReceiver() {
        if (receiverOn) return;
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_USER_PRESENT);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(screenReceiver, f);
            }
            receiverOn = true;
        } catch (Throwable t) {
            Log.w(TAG, "registerReceiver failed", t);
        }
    }

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a == null) return;
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                if (petView != null) petView.pause();
            } else {
                refreshMetricsIfChanged();
                if (petView != null) {
                    petView.resume();
                } else {
                    addPetWindow();
                }
            }
        }
    };

    // ---------------- 通知 ----------------

    private Notification buildNotification() {
        return buildNotification(true);
    }

    private Notification buildNotification(boolean attached) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "彩蝶鲨桌宠", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("彩蝶鲨正在屏幕上飘着");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, piFlags);

        Intent stopI = new Intent(this, FloatingPetService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopI, piFlags);

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CH_ID)
                : new Notification.Builder(this);
        Intent dbgI = new Intent(this, FloatingPetService.class).setAction(ACTION_DEBUG);
        PendingIntent dbgPi = PendingIntent.getService(this, 2, dbgI, piFlags);
        if (attached) {
            b.setContentTitle("彩蝶鲨在外面飘着呢")
                    .setContentText("点一下通知可以回到彩蝶鲨的小屋");
        } else {
            b.setContentTitle("彩蝶鲨还没能出来")
                    .setContentText("需要「显示在其他应用上层」权限，点这里去开启");
        }
        b.setSmallIcon(R.drawable.ic_stat_pet)
                .setContentIntent(pi)
                .addAction(0, "让它回家", stopPi)
                .addAction(0, debugOn ? "关掉诊断显示" : "打开诊断显示", dbgPi)
                .setOngoing(true);
        return b.build();
    }

    private void updateNotification(boolean attached) {
        if (lastAttached == attached && notified) return;
        lastAttached = attached;
        notified = true;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(1, buildNotification(attached));
        } catch (Throwable ignored) {
        }
    }

    private void postDelayed(Runnable r, long ms) {
        handler.postDelayed(r, ms);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        removePetWindow();
        if (receiverOn) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Throwable ignored) {
            }
            receiverOn = false;
        }
        prefs().edit().putBoolean(KEY_RUNNING, false).apply();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
