package com.dsh.caidiesha;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 彩蝶鲨的小屋：开权限、放它出去、看资料。
 */
public class MainActivity extends Activity {

    public static final String EXTRA_SHOW_INFO = "show_info";

    private TextView status;
    private Button grantBtn;
    private Button toggleBtn;
    private ScrollView scroll;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CaidieshaDex.init(this);
        setContentView(R.layout.activity_main);

        status = findViewById(R.id.status);
        grantBtn = findViewById(R.id.btn_grant);
        toggleBtn = findViewById(R.id.btn_toggle);
        scroll = findViewById(R.id.scroll);

        grantBtn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                Toast.makeText(this, "请到「设置 → 应用 → 显示在其他应用上层」里手动开启",
                        Toast.LENGTH_LONG).show();
            }
        });

        toggleBtn.setOnClickListener(v -> {
            if (FloatingPetService.canDraw(this)) {
                if (isPetRunning()) FloatingPetService.stop(this);
                else FloatingPetService.start(this);
                toggleBtn.postDelayed(this::refresh, 400);
            } else {
                Toast.makeText(this, "先给它「显示在其他应用上层」的权限～", Toast.LENGTH_SHORT).show();
            }
        });

        ((TextView) findViewById(R.id.dex)).setText(CaidieshaDex.INFO);        requestNotificationPermission();

        boolean showInfo = getIntent() != null && getIntent().getBooleanExtra(EXTRA_SHOW_INFO, false);
        if (showInfo) scroll.post(() -> scroll.smoothScrollTo(0, findViewById(R.id.dex).getTop()));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra(EXTRA_SHOW_INFO, false)) {
            scroll.post(() -> scroll.smoothScrollTo(0, findViewById(R.id.dex).getTop()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private boolean isPetRunning() {
        return getSharedPreferences(FloatingPetService.PREFS, MODE_PRIVATE)
                .getBoolean(FloatingPetService.KEY_RUNNING, false);
    }

    private void refresh() {
        boolean can = FloatingPetService.canDraw(this);
        boolean running = isPetRunning();

        grantBtn.setVisibility(can ? View.GONE : View.VISIBLE);
        toggleBtn.setText(running ? "让它先回家休息" : "放彩蝶鲨出去飘");

        String s = "状态：" + (running ? "正在屏幕上飘着" : "在家里待着")
                + "\n亲密度：" + CaidieshaDex.affection()
                + "\n悬浮窗权限：" + (can ? "已开启" : "未开启")
                + (can ? "" : "\n\n第一次使用需要点上面的按钮，把「显示在其他应用上层」打开，"
                + "彩蝶鲨才能漂到桌面和别的 App 上面。");
        status.setText(s);
    }

    /** 部分机型（Android 13+）需要通知权限才能常驻前台服务 */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            } catch (Throwable ignored) {
            }
        }
    }
}
