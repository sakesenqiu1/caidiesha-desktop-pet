package com.dsh.caidiesha;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * 彩蝶鲨本体。
 *
 * 重要：这个 View **只有彩蝶鲨那么大**（一个跟着它移动的小窗口），不是铺满屏幕。
 * 之前用满屏窗口，等于「窗口外部」不存在，FLAG_NOT_TOUCH_MODAL 形同失效，
 * 整个桌面都被挡住点不了 —— 现在窗口之外完全不覆盖，点别的应用照常。
 *
 * 除了长按菜单的四个功能名，界面上不打任何文字。
 * 互动反馈只有三样：身体的 Q 弹 / 转圈、水花粒子、合成的「啵」声。
 *
 * 飘动物理在 {@link Physics}（不依赖 Android，可在电脑上单独验证）。
 */
public class PetView extends View {

    /** 界面配色取自原图里彩蝶鲨的真实用色（见 work/FullCheck 审计） */
    private static final int C_BODY = 0xFF54BAD1;    // 身体青
    private static final int C_FIN = 0xFFF49599;     // 鱼鳍粉
    private static final int C_GLOW = 0xFF95EEFC;    // 高光青
    private static final int C_INK = 0xFF214B57;     // 深色描边/文字

    public interface Host {
        /**
         * 请求把窗口挪到指定位置（屏幕坐标）。
         *
         * @param fullWidth 菜单展开时需要接近全屏的宽度
         */
        void onWindowGeometry(int x, int y, int w, int h, boolean fullWidth);

        void onRequestStop();

        void onOpenInfo();
    }

    // ---- 尺寸 ----
    private final float dp;
    private float petW;              // 精灵宽度（像素），随屏宽调整
    private float petH;
    private final Physics phys;      // 飘动物理

    // ---- 窗口几何 ----
    private int screenW = 1080, screenH = 2340;
    private float originX, originY;  // 窗口左上角在屏幕上的位置
    private float winW, winH;
    private float statusTopPx;
    private float lastPushX = -1e9f, lastPushY = -1e9f;
    private boolean lastPushFull;

    // ---- 状态机 ----
    private static final int IDLE = 0, DRAG = 1, SPIN = 2;
    private int state = IDLE;

    // ---- 姿态 ----
    private float dragTilt;
    private float spin;
    private float spinV;

    // ---- 触摸 ----
    private float downX, downY, lastX, lastY;      // 全部是「手指在屏幕上的绝对坐标」
    private float grabOffX, grabOffY;              // 按下时精灵中心相对手指的偏移（拖动时保持不变）
    private boolean moved, longFired, multiTouch;
    private int tapCount;
    private long lastTapTime;
    private Runnable longPressRunnable;

    // ---- 长按菜单：精灵正下方的一簇带字小圆点 ----
    private boolean menuOpen;
    private static final int MENU_N = 5;
    /** 每个按钮在弧上的角度（0°=右侧，90°=正下方，180°=左侧）；躲起来在正中 */
    private static final float[] MENU_ANGLES = {12.5f, 51.25f, 90f, 128.75f, 167.5f};
    // 顺序必须与 handleMenu 的 case 下标一致：0喂食 1摸摸 2锁定 3资料 4躲起来
    private static final String[] MENU_LABELS = {"喂食", "摸摸", "锁定", "资料", "躲起来"};
    private static final String[] MENU_LABELS_LOCKED = {"喂食", "摸摸", "解锁", "资料", "躲起来"};
    private final float[] menuScreenCx = new float[MENU_N];   // 按钮圆心的屏幕坐标
    private final float[] menuScreenCy = new float[MENU_N];
    private final float[] menuCx = new float[MENU_N];         // 换算到窗口内
    private final float[] menuCy = new float[MENU_N];
    private float menuBtnR;                              // 按钮半径
    private boolean locked;                  // 锁定：停止一切移动、角度归零

    // ---- 绘制 ----
    private final Bitmap sprite;
    private final Paint petPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmpRect = new RectF();
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final Random rnd = new Random();

    private final Host host;
    private boolean running = true;
    private long lastFrame;

    private final Physics.Bounds bounds = new Physics.Bounds() {
        @Override
        public float width() {
            return screenW;
        }

        @Override
        public float height() {
            return screenH;
        }

        @Override
        public float petW() {
            return petW;
        }

        @Override
        public float petH() {
            return petH;
        }

        @Override
        public float topLimit() {
            // 必须写 PetView.this.topLimit()，否则调的是自己 —— 无限递归导致 StackOverflowError
            return PetView.this.topLimit();
        }
    };

    public PetView(Context c, Host host) {
        super(c);
        this.host = host;
        this.dp = c.getResources().getDisplayMetrics().density;
        this.phys = new Physics(dp);
        this.sprite = BitmapFactory.decodeResource(c.getResources(), R.drawable.pet_caidiesha);
        petPaint.setFilterBitmap(false);   // 不插值，颜色保持原图那一组
        shadowPaint.setColor(0x33214B57);
        dotPaint.setColor(C_BODY);
        panelFill.setColor(0xF2FFFFFF);
        panelFill.setStyle(Paint.Style.FILL);
        panelStroke.setColor(C_BODY);
        panelStroke.setStyle(Paint.Style.STROKE);
        panelStroke.setStrokeWidth(1.6f * dp);
        labelPaint.setColor(C_INK);
        labelPaint.setTextSize(13.5f * dp);   // 菜单按钮文字：drawMenu 里会按圆点大小再调
        labelPaint.setTextAlign(Paint.Align.CENTER);
        setFocusable(false);
    }

    // ==================== 初始化 / 窗口几何 ====================

    /** 由服务在挂上窗口前调用 */
    public void setup(int screenW, int screenH, float statusTopPx, float initScreenX, float initScreenY) {
        this.screenW = screenW;
        this.screenH = screenH;
        this.statusTopPx = statusTopPx;
        float dpw = screenW / dp;
        petW = Math.max(110f, Math.min(180f, dpw * 0.44f)) * dp;
        petH = sprite.getHeight() * petW / sprite.getWidth();
        phys.seed(initScreenX, initScreenY);
        reseedMotion();          // 给一份初始速度，否则刚出现时几乎不动
        clampToScreen();
        pushGeometry();
    }

    /** 精灵「视觉顶边」的最小 y（避开状态栏与挖孔） */
    private float topLimit() {
        return Math.max(statusTopPx, 40f * dp);
    }

    private void clampToScreen() {
        petW = Math.min(petW, screenW * 0.9f);
        petH = sprite.getHeight() * petW / sprite.getWidth();
        phys.x = Math.max(petW / 2f, Math.min(screenW - petW / 2f, phys.x));
        phys.y = Math.max(topLimit() + petH / 2f, Math.min(screenH - petH / 2f - 14f * dp, phys.y));
    }

    /**
     * 自检：彩蝶鲨必须真的落在窗口范围内，否则立刻拉回屏幕中央。
     * 任何尺寸/坐标算错的情况都会在这里被纠正，保证它不会「消失」。
     */
    private void selfHeal() {
        if (screenW <= 0 || screenH <= 0) return;
        boolean bad = Float.isNaN(phys.x) || Float.isNaN(phys.y)
                || Float.isInfinite(phys.x) || Float.isInfinite(phys.y)
                || Float.isNaN(phys.vx) || Float.isInfinite(phys.vx)
                || Float.isNaN(phys.vy) || Float.isInfinite(phys.vy)
                || petW <= 0 || petH <= 0;
        if (!bad) {
            // 精灵在窗口内的位置必须和它的大小相称，否则说明换算出了偏差
            float cx = vx(), cy = vy();
            bad = cx < -petW || cx > winW + petW || cy < -petH || cy > winH + petH;
        }
        if (bad) {
            resetToCenter();
        }
    }

    /** 把彩蝶鲨放回屏幕中央（同时也重置速度，避免它继续朝屏幕外飞） */
    public void resetToCenter() {
        phys.seed(screenW * 0.5f, screenH * 0.42f);
        clampToScreen();
        if (locked) {                 // 锁定时只挪位置，不重新给速度、也不起伏
            phys.vx = 0f;
            phys.vy = 0f;
            phys.bob = 0f;
        }
        lastPushX = -1e9f;
        pushGeometry();
    }

    /** 菜单展开时是否放在精灵上方（屏幕下半部分就放上方）—— 展开期间固定，避免来回翻 */
    private float menuH;
    private float menuTotalW;

    /** 按钮半径：直径 38dp */
    private float menuBtnRSized() {
        return 30f * dp;      // 直径 60dp（这块屏上唯一能塞进单弧的尺寸）
    }

    /**
     * 环绕半径。约束取最小：
     *   1 至少 2.5 倍身体半径 —— 5 个圆点（间隔 38.75°）互不重叠的下限
     *   2 横向塞得下屏幕
     *   3 纵向余量够
     */
    private float ringRadius() {
        float bodyR = (float) Math.hypot(petW / 2f, petH / 2f);
        float btnR = menuBtnRSized();
        float r = 2.5f * bodyR;
        double chord = 2.0 * Math.sin(Math.toRadians(38.75 / 2.0));   // 相邻按钮角差 38.75°
        r = Math.max(r, (float) ((2 * btnR + 8f * dp) / chord));
        // 正确的横向约束：最外侧按钮（12.5°/167.5°，|cos|≈0.976）的外沿要留在屏幕内
        float byWidth = (screenW / 2f - btnR - 4f * dp) / 0.977f;
        float byHeight = screenH - petH / 2f - 130f * dp - bodyR - btnR;
        return Math.max(120f * dp, Math.min(r, Math.min(byWidth, byHeight)));
    }

    /**
     * 重算窗口矩形并通知服务。
     *
     * 算法是「先算内容带，再放下窗口」：
     *   内容带 = 精灵矩形 ∪ 菜单矩形（菜单展开时），再向外留 6~10dp；
     *   窗口尺寸 = 内容带尺寸；窗口位置尽量居中于精灵，但会夹取到「能把内容带整个装下」的范围。
     * 这样菜单永远不会被窗口边界裁掉 —— 之前只显示上半部分、横向还缺一块，都是这里算错。
     */
    private void pushGeometry() {
        float padX = 36f * dp;
        float padV = 40f * dp;

        if (menuOpen) {
            menuBtnR = menuBtnRSized();
            menuTotalW = menuBtnR * 2f;
            menuH = menuBtnR * 2f;

            // 以精灵为圆心的下半圈弧
            float rad = ringRadius();
            float minX = phys.x, maxX = phys.x, minY = phys.y, maxY = phys.y;
            for (int i = 0; i < MENU_N; i++) {
                float a = (float) Math.toRadians(MENU_ANGLES[i]);
                menuScreenCx[i] = phys.x + (float) Math.cos(a) * rad;
                menuScreenCy[i] = phys.y + (float) Math.sin(a) * rad;
                // 圆点不许跑出屏幕
                menuScreenCx[i] = Math.max(menuBtnR + 4f * dp,
                        Math.min(screenW - menuBtnR - 4f * dp, menuScreenCx[i]));
                menuScreenCy[i] = Math.max(menuBtnR + 4f * dp,
                        Math.min(screenH - menuBtnR - 4f * dp, menuScreenCy[i]));
                minX = Math.min(minX, menuScreenCx[i] - menuBtnR);
                maxX = Math.max(maxX, menuScreenCx[i] + menuBtnR);
                minY = Math.min(minY, menuScreenCy[i] - menuBtnR);
                maxY = Math.max(maxY, menuScreenCy[i] + menuBtnR);
            }

            // 内容带 = 精灵矩形 ∪ 所有圆点按钮
            float cL = Math.min(phys.x - petW / 2f - padX, minX - 10f * dp);
            float cR = Math.max(phys.x + petW / 2f + padX, maxX + 10f * dp);
            float cT = Math.min(phys.y - petH / 2f - padV, minY - 10f * dp);
            float cB = Math.max(phys.y + petH / 2f + padV, maxY + 10f * dp);
            float winW = Math.min(cR - cL, screenW);
            float winH = Math.min(cB - cT, screenH);

            float originX = Math.max(cR - winW, Math.min(cL, phys.x - winW / 2f));
            float originY = Math.max(cB - winH, Math.min(cT, phys.y - winH / 2f));
            if (originX < 0) originX = 0;
            if (originY < 0) originY = 0;
            if (originX + winW > screenW) originX = Math.max(0, screenW - winW);
            if (originY + winH > screenH) originY = Math.max(0, screenH - winH);

            this.originX = originX;
            this.originY = originY;
            this.winW = winW;
            this.winH = winH;
            layoutMenu();

            boolean changed = Math.abs(originX - lastPushX) > 0.5f
                    || Math.abs(originY - lastPushY) > 0.5f
                    || menuOpen != lastPushFull;
            if (changed) {
                lastPushX = originX;
                lastPushY = originY;
                lastPushFull = menuOpen;
                host.onWindowGeometry(Math.round(originX), Math.round(originY),
                        Math.round(winW), Math.round(winH), true);
            }
            return;
        }

        // 平时：窗口只包住精灵
        float winW = Math.min(petW + padX * 2f, screenW);
        float winH = Math.min(petH + padV * 2f, screenH);
        float originX = phys.x - winW / 2f;
        float originY = phys.y - winH / 2f;

        this.originX = originX;
        this.originY = originY;
        this.winW = winW;
        this.winH = winH;

        boolean changed = Math.abs(originX - lastPushX) > 0.5f
                || Math.abs(originY - lastPushY) > 0.5f
                || menuOpen != lastPushFull;
        if (changed) {
            lastPushX = originX;
            lastPushY = originY;
            lastPushFull = menuOpen;
            host.onWindowGeometry(Math.round(originX), Math.round(originY),
                    Math.round(winW), Math.round(winH), false);
        }
    }

    public void pause() {
        running = false;
    }

    public void resume() {
        running = true;
        lastFrame = 0;
        postInvalidateOnAnimation();
    }

    public boolean isLocked() {
        return locked;
    }

    public float getPetWidth() {
        return petW;
    }

    public float getPetHeight() {
        return petH;
    }

    /** 服务挂上窗口后调用一次，确保窗口尺寸和精灵对上 */
    public void forcePushGeometry() {
        lastPushX = -1e9f;
        pushGeometry();
    }

    // ==================== 主循环 ====================

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            onDrawInner(canvas);
        } catch (Throwable t) {
            // 万一绘制抛异常，界面会整个冻结（这本身就能解释「什么都不出现」）
            drawError = "onDraw 异常: " + t;
            PetApp.save(getContext(), t, "PetView.onDraw");
            try {
                postInvalidateOnAnimation();
            } catch (Throwable ignored) {
            }
        }
    }

    private void onDrawInner(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        drawCount++;

        if (running && !locked) step(dt);          // 锁定后不再推进任何移动/角度
        updateParticles(dt);
        if (running) {
            pushGeometry();
            selfHeal();
            if (menuOpen) layoutMenu();   // 窗口位置/尺寸变了，菜单跟着重排
        }

        drawParticles(canvas);
        drawPet(canvas);
        if (menuOpen) drawMenu(canvas);
        drawDebugOverlay(canvas);

        if ((running && !locked) || !particles.isEmpty() || menuOpen || debugOn) {
            postInvalidateOnAnimation();
        }
    }

    // ==================== 屏上诊断（排查用） ====================

    private boolean debugOn;          // 打开后会把内部状态画在屏幕上
    private int drawCount;
    private String lastTouch = "-";
    private String drawError = "";

    public void setDebug(boolean on) {
        debugOn = on;
        invalidate();
    }

    /** 锁定标记（粉色圆 + 居中的锁体 + 上方锁梁）+ 可选的诊断文字 */
    private void drawDebugOverlay(Canvas c) {
        if (locked) {
            float cx = 15f * dp, cy = 15f * dp, r = 8f * dp;
            // 圆底
            dotPaint.setStyle(Paint.Style.FILL);
            dotPaint.setColor(0xE6F49599);
            c.drawCircle(cx, cy, r, dotPaint);
            // 锁体：正方形，中心与圆心严格对齐（略下移一点点，视觉上更像锁）
            float bw = 9f * dp, bh = 8f * dp;
            float l = cx - bw / 2f, t = cy - bh / 2f + 0.8f * dp;
            dotPaint.setColor(0xFF214B57);
            c.drawRect(l, t, l + bw, t + bh, dotPaint);
            // 锁梁：锁体上方的半圆
            dotPaint.setStyle(Paint.Style.STROKE);
            dotPaint.setStrokeWidth(1.6f * dp);
            tmpRect.set(cx - 3.2f * dp, cy - bh / 2f - 5.2f * dp,
                    cx + 3.2f * dp, cy - bh / 2f + 1.2f * dp);
            c.drawArc(tmpRect, 180f, 180f, false, dotPaint);
            dotPaint.setStyle(Paint.Style.FILL);
        }
        if (!debugOn) return;
        labelPaint.setTextSize(9f * dp);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        labelPaint.setColor(0xFF214B57);
        float y = 30f * dp;
        String[] lines = {
                "draw#" + drawCount + " menu=" + menuOpen + " locked=" + locked,
                "touch: " + lastTouch,
                "win " + Math.round(winW) + "x" + Math.round(winH)
                        + " org " + Math.round(originX) + "," + Math.round(originY),
                "btnR " + Math.round(menuBtnR) + " c0 " + Math.round(menuCx[0]) + "," + Math.round(menuCy[0]),
                drawError.isEmpty() ? "-" : drawError,
        };
        for (String s : lines) {
            c.drawText(s, 6f * dp, y, labelPaint);
            y += 11f * dp;
        }
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    private void step(float dt) {
        boolean drag = state == DRAG;
        float dragDx = drag ? (phys.x - lastX) : 0f;

        phys.step(dt, bounds, drag, dragDx);

        if (drag) {
            dragTilt += (Math.max(-14f, Math.min(14f, dragDx * 1.2f)) - dragTilt) * Math.min(1f, dt * 10f);
        } else {
            dragTilt += (0 - dragTilt) * Math.min(1f, dt * 6f);
        }

        if (state == SPIN) {
            spinV *= (float) Math.pow(0.12f, dt);
            spin += spinV * dt;
            if (Math.abs(spinV) < 6f) spinV = 0;
        }
    }

    // ==================== 触摸 ====================

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float px = e.getX(), py = e.getY();          // View 内坐标（用于命中判定）
        // 拖动必须用「手指在屏幕上的绝对坐标」：窗口会跟着手指移动，
        // 用 View 内坐标或两次事件的增量都会因为窗口位移而算错，误差累积后
        // 彩蝶鲨就会窜出去、卡几下、再弹回来。
        float rawX = e.getRawX(), rawY = e.getRawY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = rawX;
                downY = rawY;
                lastX = rawX;
                lastY = rawY;
                grabOffX = phys.x - rawX;              // 抓住时手指相对精灵中心的偏移，全程不变
                grabOffY = phys.y - rawY;
                moved = longFired = multiTouch = false;

                if (menuOpen) {
                    int hit = hitMenu(px, py);
                    if (hit >= 0) {
                        handleMenu(hit);
                        return true;
                    }
                    menuOpen = false;
                    lastPushX = -1e9f;
                    pushGeometry();
                    invalidate();
                    return true;
                }
                if (!hitPet(px, py)) return false;

                // 长按必须照常安排（锁定时也要能呼出菜单去解锁）——
                // 之前这里直接 return false，导致 scheduleLongPress() 永远执行不到
                if (!locked) {
                    state = DRAG;
                    phys.vx = 0f;
                    phys.vy = 0f;
                }
                phys.squashV = -1.6f;
                scheduleLongPress();
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                multiTouch = true;
                cancelPendingLongPress();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (locked) return false;      // 锁定时不跟随手指
                if (state != DRAG) return false;
                if (Math.hypot(rawX - downX, rawY - downY) > 9f * dp) {
                    moved = true;
                    cancelPendingLongPress();
                }
                if (moved) {
                    // 绝对定位：精灵中心 = 手指位置 + 抓住时的偏移（不累积误差）
                    float nx = rawX + grabOffX;
                    float ny = rawY + grabOffY;
                    float dx = nx - phys.x, dy = ny - phys.y;
                    phys.x = nx;
                    phys.y = ny;
                    clampToScreen();
                    // 松手后的惯性速度：按真实位移算，并且限幅，避免甩飞
                    phys.vx = Math.max(-300f * dp, Math.min(300f * dp, dx * 20f));
                    phys.vy = Math.max(-300f * dp, Math.min(300f * dp, dy * 20f));
                    spawnTrail();
                    pushGeometry();   // 窗口边界立即跟上，精灵不会被窗口边缘裁掉
                }
                lastX = rawX;
                lastY = rawY;
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelPendingLongPress();
                if (state != DRAG) return false;
                state = IDLE;
                phys.squashV = 2.2f;
                if (!moved && !multiTouch && !longFired) {
                    handleTap();
                } else if (moved) {
                    phys.vx = Math.max(-420f * dp, Math.min(420f * dp, phys.vx));
                    phys.vy = Math.max(-300f * dp, Math.min(300f * dp, phys.vy));
                    if (Math.hypot(phys.vx, phys.vy) < 24f * dp) {
                        phys.vx = 40f * dp;
                        phys.vy = 12f * dp;
                    }
                }
                invalidate();
                return true;
        }
        return false;
    }

    private void scheduleLongPress() {
        cancelPendingLongPress();
        longPressRunnable = new Runnable() {
            @Override
            public void run() {
                // 只要求手指没移动过 —— 不能再要求 state == DRAG：
                // 锁定时为了不拖动，故意没有进入 DRAG 状态，那样长按也会被一起挡掉。
                lastTouch = "LONGPRESS moved=" + moved + " state=" + state + " locked=" + locked;
                if (!moved) {
                    longFired = true;
                    openMenu();
                }
            }
        };
        postDelayed(longPressRunnable, 420);
    }

    private void cancelPendingLongPress() {
        if (longPressRunnable != null) {
            removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }

    private boolean hitPet(float px, float py) {
        float hw = petW / 2f * 1.04f, hh = petH / 2f * 1.06f;
        float dx = (px - vx()) / hw, dy = (py - (vy() + phys.bob)) / hh;
        return dx * dx + dy * dy <= 1f;
    }

    /** 精灵中心在 View 内的坐标 */
    private float vx() {
        return phys.x - originX;
    }

    private float vy() {
        return phys.y - originY;
    }

    private void handleTap() {
        long now = SystemClock.uptimeMillis();
        if (now - lastTapTime < 320) {
            tapCount = 0;
            lastTapTime = 0;
            state = SPIN;
            spinV = phys.facingRight ? 720f : -720f;
            spawnBurst(10, P_DROP);
            Sfx.play(Sfx.SPIN);
            CaidieshaDex.addAffection(1);
            invalidate();
            return;
        }
        lastTapTime = now;
        tapCount++;
        phys.squashV = -2.6f;
        CaidieshaDex.addAffection(1);
        boolean milestone = tapCount % 10 == 0;
        spawnBurst(milestone ? 9 : 7, milestone ? P_HEART : P_DROP);
        Sfx.play(Sfx.CHIRP);
        invalidate();
    }

    // ==================== 长按菜单 ====================

    private void openMenu() {
        menuOpen = true;
        lastPushX = -1e9f;          // 菜单更大，强制重算窗口
        lastPushY = -1e9f;
        pushGeometry();             // 内部会调用 layoutMenu()
        spawnBurst(6, P_BUBBLE);
        invalidate();
    }

    /** 把「屏幕坐标」的圆点换算成窗口内坐标 */
    private void layoutMenu() {
        for (int i = 0; i < MENU_N; i++) {
            menuCx[i] = menuScreenCx[i] - originX;
            menuCy[i] = menuScreenCy[i] - originY;
        }
    }

    /** 命中测试：点到哪个圆点（-1 不在弧内，-2 弧内但没有按钮） */
    private int hitMenu(float px, float py) {
        float rr = (menuBtnR + 6f * dp);
        boolean any = false;
        for (int i = 0; i < MENU_N; i++) {
            float dx = px - menuCx[i], dy = py - menuCy[i];
            if (dx * dx + dy * dy <= rr * rr) return i;
            if (dx * dx + dy * dy <= (rr * 2.2f) * (rr * 2.2f)) any = true;
        }
        return any ? -2 : -1;
    }

    private void handleMenu(int i) {
        menuOpen = false;
        lastPushX = -1e9f;          // 收起菜单后窗口缩回精灵大小
        switch (i) {
            case 0:
                CaidieshaDex.addAffection(3);
                spawnBurst(12, P_HEART);
                Sfx.play(Sfx.FEED);
                break;
            case 1:
                CaidieshaDex.addAffection(2);
                spawnBurst(8, P_BUBBLE);
                phys.squashV = -2.4f;
                Sfx.play(Sfx.PAT);
                break;
            case 2:
                toggleLock();
                break;
            case 3:
                host.onOpenInfo();
                break;
            default:
                postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        host.onRequestStop();
                    }
                }, 260);
                break;
        }
        pushGeometry();
        invalidate();
    }

    /**
     * 锁定 / 解锁。
     * 锁定：停止一切移动（不再飘），并把角度归零 —— 也就是回到「初始角度」。
     * 解锁：给一点初始速度，重新开始飘。
     */
    private void toggleLock() {
        locked = !locked;
        if (locked) {
            phys.vx = 0f;
            phys.vy = 0f;
            phys.bob = 0f;
            phys.squash = 0f;
            phys.squashV = 0f;
            spin = 0f;
            spinV = 0f;
            dragTilt = 0f;
            state = IDLE;
            spawnBurst(5, P_BUBBLE);
        } else {
            // 解锁：回到屏幕中间，并重新给一份初始速度（否则只会微微抖动、看不出在飘）
            resetToCenter();
            reseedMotion();
            spawnBurst(8, P_DROP);
        }
        Sfx.play(Sfx.PAT);
    }

    /** 重新给「借气流而动」的初始速度与相位 */
    private void reseedMotion() {
        float a = (rnd.nextFloat() - 0.5f) * 1.4f;
        phys.vx = (float) Math.cos(a) * 56f * dp;
        phys.vy = (float) Math.sin(a) * 30f * dp;
        phys.bobT = rnd.nextFloat() * 6.28f;
        phys.facingRight = phys.vx > 0;
    }

    // ==================== 粒子（坐标是相对精灵中心的偏移，窗口移动不会错位） ====================

    private static final int P_DROP = 0, P_BUBBLE = 1, P_HEART = 2;

    private static class Particle {
        float ox, oy, vx, vy, life, maxLife, r;
        int type;
    }

    private void spawnBurst(int n, int type) {
        for (int i = 0; i < n; i++) {
            Particle p = new Particle();
            p.type = type;
            p.ox = (rnd.nextFloat() - 0.5f) * petW * 0.6f;
            p.oy = (rnd.nextFloat() - 0.5f) * petH * 0.45f;
            double a = rnd.nextDouble() * Math.PI * 2;
            float sp = (18f + rnd.nextFloat() * 46f) * dp;
            p.vx = (float) Math.cos(a) * sp;
            p.vy = (float) Math.sin(a) * sp - 22f * dp;
            p.maxLife = p.life = 0.7f + rnd.nextFloat() * 0.7f;
            p.r = (type == P_HEART ? 4.2f : type == P_BUBBLE ? 3.4f : 2.8f) * dp * (0.7f + rnd.nextFloat() * 0.6f);
            particles.add(p);
        }
    }

    private void spawnTrail() {
        if (rnd.nextInt(3) != 0) return;
        Particle p = new Particle();
        p.type = P_DROP;
        p.ox = -Math.signum(phys.vx) * petW * 0.26f;
        p.oy = (rnd.nextFloat() - 0.5f) * petH * 0.3f;
        p.vx = -phys.vx * 0.12f;
        p.vy = 10f * dp;
        p.maxLife = p.life = 0.45f + rnd.nextFloat() * 0.35f;
        p.r = 2.2f * dp * (0.6f + rnd.nextFloat() * 0.7f);
        particles.add(p);
    }

    private void updateParticles(float dt) {
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0) {
                it.remove();
                continue;
            }
            p.ox += p.vx * dt;
            p.oy += p.vy * dt;
            if (p.type == P_HEART) {
                p.vy -= 26f * dp * dt;
                p.vx *= (float) Math.pow(0.35f, dt);
            } else if (p.type == P_BUBBLE) {
                p.vy -= 34f * dp * dt;
                p.vx *= (float) Math.pow(0.5f, dt);
            } else {
                p.vy += 42f * dp * dt;
            }
        }
    }

    private void drawParticles(Canvas c) {
        for (Particle p : particles) {
            float x = vx() + p.ox, y = vy() + phys.bob + p.oy;
            int alpha = (int) (255 * Math.min(1f, p.life / (p.maxLife * 0.55f)));
            if (alpha <= 2) continue;
            switch (p.type) {
                case P_HEART:
                    dotPaint.setStyle(Paint.Style.FILL);
                    dotPaint.setColor(Color.argb(alpha, 0xF4, 0x95, 0x99));
                    c.drawCircle(x - p.r * 0.5f, y, p.r * 0.62f, dotPaint);
                    c.drawCircle(x + p.r * 0.5f, y, p.r * 0.62f, dotPaint);
                    c.drawCircle(x, y + p.r * 0.35f, p.r * 0.72f, dotPaint);
                    break;
                case P_BUBBLE:
                    dotPaint.setStyle(Paint.Style.STROKE);
                    dotPaint.setStrokeWidth(1.2f * dp);
                    dotPaint.setColor(Color.argb(alpha, 0x95, 0xEE, 0xFC));
                    c.drawCircle(x, y, p.r, dotPaint);
                    break;
                default:
                    dotPaint.setStyle(Paint.Style.FILL);
                    dotPaint.setColor(Color.argb(alpha, 0x54, 0xBA, 0xD1));
                    tmpRect.set(x - p.r * 0.7f, y - p.r, x + p.r * 0.7f, y + p.r);
                    c.drawOval(tmpRect, dotPaint);
                    break;
            }
        }
    }

    // ==================== 绘制 ====================

    private void drawPet(Canvas c) {
        float pulse = (float) Math.sin(phys.bobT * 0.9f);
        float w = petW * (1f + phys.squash * 0.10f) * (1f + pulse * 0.012f);
        float h = petH * (1f - phys.squash * 0.10f) * (1f - pulse * 0.012f);
        float cx = vx(), cy = vy() + phys.bob;

        float lift = phys.bob / (9f * dp);
        float sw = w * (0.62f - lift * 0.05f);
        float sh = h * (0.16f - lift * 0.015f);
        shadowPaint.setAlpha((int) Math.max(12, 58 - lift * 16));
        tmpRect.set(cx - sw / 2f, cy + h * 0.42f, cx + sw / 2f, cy + h * 0.42f + sh);
        c.drawOval(tmpRect, shadowPaint);

        c.save();
        c.translate(cx, cy);
        c.rotate(spin);
        if (!phys.facingRight) c.scale(-1f, 1f);
        c.rotate(dragTilt * 0.35f);
        tmpRect.set(-w / 2f, -h / 2f, w / 2f, h / 2f);
        c.drawBitmap(sprite, null, tmpRect, petPaint);
        c.restore();
    }

    /** 画环绕精灵的圆点按钮：白底（锁定/躲起来为淡粉）+ 官方青描边 + 居中文字 */
    private void drawMenu(Canvas c) {
        String[] labels = locked ? MENU_LABELS_LOCKED : MENU_LABELS;
        for (int i = 0; i < MENU_N; i++) {
            boolean pink = (i == 2 && locked) || i == 4;
            panelFill.setColor(pink ? 0xF2FCEDEE : 0xF2FFFFFF);
            c.drawCircle(menuCx[i], menuCy[i], menuBtnR, panelFill);
            panelStroke.setColor(0xFF3E9CB5);          // 官方青（原图中真实存在）
            panelStroke.setStrokeWidth(2.2f * dp);
            c.drawCircle(menuCx[i], menuCy[i], menuBtnR, panelStroke);
            float ty = menuCy[i] - (labelPaint.descent() + labelPaint.ascent()) / 2f;
            c.drawText(labels[i], menuCx[i], ty, labelPaint);
        }
    }
}
