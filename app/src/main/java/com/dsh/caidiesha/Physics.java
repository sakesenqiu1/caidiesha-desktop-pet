package com.dsh.caidiesha;

/**
 * 彩蝶鲨的飘动物理，零 Android 依赖。
 *
 * 这样它既能在手机上跑，也能在电脑上跑长时间稳定性验证
 * （见 D:\dsd-pet\work\PhysicsCheck.java）：不会飘出屏幕、不会出 NaN、速度有界、真的会游遍全屏。
 *
 * 模型：图鉴说它「借气流而动」——每 4.5~9 秒换一个随机的落点，
 * 朝落点施加转向加速度（所以转向是圆滑的），越接近落点越收力；
 * 纵向再叠一层浮力起伏，飘得越快摆幅越大。
 */
public class Physics {

    // 位置（精灵中心）
    public float x, y;
    // 速度
    public float vx, vy;
    // 起伏
    public float bob, bobT;
    // 挤压弹簧（点击时的 Q 弹）
    public float squash, squashV;
    // 朝向与当前速度
    public boolean facingRight = true;
    public float speed;

    // 当前落点
    public float tx, ty;
    private float untilRetarget;

    private final float dp;
    private final java.util.Random rnd = new java.util.Random();

    /** 每帧的边界，由 View 提供 */
    public interface Bounds {
        float width();

        float height();

        float petW();

        float petH();

        float topLimit();
    }

    public Physics(float density) {
        this.dp = density;
    }

    /** 放到屏幕中间偏上，并给一点初始速度 */
    public void seed(float cx, float cy) {
        x = cx;
        y = cy;
        tx = cx;
        ty = cy;
        vx = 46f * dp;
        vy = 18f * dp;
        bobT = 0f;
        untilRetarget = 0f;
    }

    /** 一次物理步进。dragging 为真时位置由手指控制，只推进姿态动画。 */    public void step(float dt, Bounds b, boolean dragging, float dragDx) {
        if (dt <= 0f) return;
        if (dt > 0.05f) dt = 0.05f;   // 卡顿/切前台后不要让时间跳变

        if (!dragging) {
            float w = b.width(), h = b.height();
            float hw = b.petW() / 2f, hh = b.petH() / 2f;
            // bounds.topLimit() 已经含了起伏余量（见 PetView.topLimit），这里只管横向留白
            float padX = 10f * dp, padY = 10f * dp;
            float minX = hw + padX, maxX = w - hw - padX;
            float minY = b.topLimit() + padY, maxY = h - hh - padY - 14f * dp;
            if (maxX < minX) { float m = (minX + maxX) / 2f; minX = maxX = m; }
            if (maxY < minY) { float m = (minY + maxY) / 2f; minY = maxY = m; }

            // 换一个落点
            untilRetarget -= dt;
            if (untilRetarget <= 0f) {
                tx = minX + rnd.nextFloat() * (maxX - minX);
                ty = minY + rnd.nextFloat() * (maxY - minY);
                untilRetarget = 3.5f + rnd.nextFloat() * 3.5f;
            }

            // 朝落点转向：加速度正比于距离（像被气流推着滑过去），
            // 再用速度阻尼把接近速度收敛到 2.2/s —— 于是「远则赶路、近则滑翔」。
            float dx = tx - x, dy = ty - y;
            float dist = (float) Math.hypot(dx, dy);
            final float base = 70f * dp;
            float ax = 0f, ay = 0f;
            if (dist > 1f) {
                float acc = Math.min(3.2f * base, 2.6f * dist);
                ax = dx / dist * acc;
                ay = dy / dist * acc;
            }
            // 单帧速度增量也限一下，掉帧时不会突然窜出去
            float maxDv = 160f * dp * dt;
            vx += clamp(ax * dt, -maxDv, maxDv);
            vy += clamp(ay * dt, -maxDv, maxDv);
            vx -= vx * Math.min(1f, 1.6f * dt);
            vy -= vy * Math.min(1f, 1.6f * dt);

            // 限速：滑翔感来自这里，太快就不像「飘」了
            float sp = (float) Math.hypot(vx, vy);
            float vmax = 1.3f * base;
            if (sp > vmax) {
                vx = vx / sp * vmax;
                vy = vy / sp * vmax;
            }
            // 保底速度：即使落点恰好就在脚下，也会顺着气流慢慢滑走
            float vmin = 0.3f * base;
            if (sp > 0.01f && sp < vmin) {
                vx = vx / sp * vmin;
                vy = vy / sp * vmin;
            } else if (sp <= 0.01f) {
                vx = vmin;
                vy = 0f;
            }

            // 先算当前速度与起伏，再做边界夹取——
            // 夹取必须用「当帧」的起伏量，否则精灵会在起伏峰值时探出屏幕。
            float spNow = (float) Math.hypot(vx, vy);
            float amp = 5.5f * dp + Math.min(9f * dp, spNow * 0.06f);
            bobT += dt * (1.35f + Math.min(1.1f, spNow * 0.004f));
            bob = (float) Math.sin(bobT) * amp;

            x += vx * dt;
            y += vy * dt;

            // 直接夹「视觉上下边」（含起伏），这样保证是精确的：
            //   视觉顶边 = y + bob - petH/2 >= topLimit，视觉底边 <= h - 14dp
            // 于是任意时刻（含起伏峰值）精灵整体都在安全区内。
            float visualTop = y + bob - hh;
            float visualBottom = y + bob + hh;
            float minCenterY = b.topLimit() - bob + hh;
            float maxCenterY = h - 14f * dp - bob - hh;
            if (minCenterY > maxCenterY) {
                float m = (minCenterY + maxCenterY) / 2f;
                minCenterY = maxCenterY = m;
            }
            if (x < hw) {
                x = hw;
                vx = Math.abs(vx) + 12f * dp;
            }
            if (x > w - hw) {
                x = w - hw;
                vx = -Math.abs(vx) - 12f * dp;
            }
            if (y < minCenterY) {
                y = minCenterY;
                vy = Math.abs(vy) + 8f * dp;
            } else if (y > maxCenterY) {
                y = maxCenterY;
                vy = -Math.abs(vy) - 8f * dp;
            }
            if (visualTop < b.topLimit() - 1f || visualBottom > h - 13f * dp) {
                // 理论上到不了这里；留个兜底方便以后调参时不至于出屏
                y = Math.max(minCenterY, Math.min(maxCenterY, y));
            }
        } else {
            // 被拖着走时只推进起伏和挤压动画
            speed = (float) Math.hypot(vx, vy);
            float amp = 5.5f * dp + Math.min(9f * dp, speed * 0.06f);
            bobT += dt * (1.35f + Math.min(1.1f, speed * 0.004f));
            bob = (float) Math.sin(bobT) * amp;
        }

        speed = (float) Math.hypot(vx, vy);
        if (!dragging && Math.abs(vx) > 4f) facingRight = vx > 0;

        // 挤压弹簧
        squashV += (-squash * 150f - squashV * 12f) * dt;
        squash += squashV * dt;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
