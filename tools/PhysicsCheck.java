import com.dsh.caidiesha.Physics;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在电脑上直接跑手机上那份 Physics 类，做长时间稳定性验证：
 * 不出屏幕、不出 NaN、速度有界、不卡死、挤压弹簧不发散，
 * 并且在卡帧/掉帧/极端屏幕尺寸下依然成立。
 *
 * 它读的是 App 里真正的源码文件（com/dsh/caidiesha/Physics.java），
 * 不是复制一份改过的逻辑。
 */
public class PhysicsCheck {

    static int checks = 0, fails = 0;

    public static void main(String[] args) throws Exception {
        File src = new File(args.length > 0 ? args[0]
                : "D:\\dsd-pet\\app\\src\\main\\java\\com\\dsh\\caidiesha\\Physics.java");
        String code = new String(Files.readAllBytes(src.toPath()), StandardCharsets.UTF_8);
        double kDp = constant(code, "untilRetarget = ([0-9.]+)f \\+ rnd", 4.5);
        double kBase = constant(code, "float base = ([0-9.]+)f \\* dp", 70.0);
        double kVx = constant(code, "^\\s*vx = ([0-9.]+)f \\* dp;", 46.0, true);
        double kVmax = constant(code, "vmax = ([0-9.]+)f \\* base", 1.6);
        System.out.println("Physics.java: 基速 " + kBase + "*dp, 限速 " + kVmax + "*base, 起步 "
                + kVx + "*dp, 换落点间隔 >=" + kDp + "s, 理论上限 " + (kBase * kVmax) + " dp/s");

        // 三种屏幕：常见 1080p、小屏、平板
        run("1080p (1080x2340, d=2.75)", 1080, 2340, 2.75f, kVx, kBase, kVmax);
        run("small (720x1280, d=2.0)", 720, 1280, 2.0f, kVx, kBase, kVmax);
        run("tablet (1600x2560, d=2.0)", 1600, 2560, 2.0f, kVx, kBase, kVmax);

        System.out.println("\n" + (fails == 0 ? "全部通过" : (fails + " 项失败"))
                + "  （共 " + checks + " 项检查）");
        if (fails > 0) System.exit(1);
    }

    static void run(String label, int w, int h, float dp, double kVx, double kBase, double kVmax) {
        final float petW = Math.max(112f, Math.min(178f, (w / dp) * 0.42f)) * dp;
        final float petH = petW * 126f / 200f;          // 精灵宽高比 200:126
        final float topLimit = 44f * dp;   // 与 PetView.topLimit() 一致：这是「视觉顶边」的下限

        Physics.Bounds b = new Physics.Bounds() {
            public float width() { return w; }
            public float height() { return h; }
            public float petW() { return petW; }
            public float petH() { return petH; }
            public float topLimit() { return topLimit; }
        };

        Physics p = new Physics(dp);
        p.seed(w * 0.5f, h * 0.42f);

        // 速度上界：限速 kBase*kVmax，弹墙瞬时增量另算
        double vLimit = (kBase * kVmax + 30) * dp;   // 限速 + 弹墙增量
        Random rnd = new Random(20261006);

        long frames = 0;
        double maxSpeed = 0, maxSquash = 0, minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        double sumSpeed = 0;
        int flips = 0, stuckHigh = 0, stuckLow = 0;
        boolean prevRight = p.facingRight;
        boolean bad = false;
        // 前 60 秒单独统计覆盖范围：桌宠必须在头一分钟内就游遍屏幕
        double fMinX = 1e9, fMaxX = -1e9, fMinY = 1e9, fMaxY = -1e9;
        long firstFrames = 60 * 60;

        // 模拟 60 分钟：多数 60fps，偶尔卡帧、掉帧、长卡顿
        for (int i = 0; i < 60 * 60 * 60; i++) {
            float dt;
            int r = rnd.nextInt(1000);
            if (r < 5) dt = 0.25f;            // 严重卡顿（比 clamp 上限还大）
            else if (r < 60) dt = 0.05f;      // 掉帧
            else if (r < 90) dt = 0.033f;
            else dt = 0.0167f;
            p.step(dt, b, false, 0f);
            frames++;

            if (!finite(p.x) || !finite(p.y) || !finite(p.vx) || !finite(p.vy)
                    || !finite(p.bob) || !finite(p.squash) || !finite(p.squashV)) {
                fail(label + ": 出现 NaN/Inf（第 " + i + " 帧）");
                bad = true;
                break;
            }

            double sp = Math.hypot(p.vx, p.vy);
            maxSpeed = Math.max(maxSpeed, sp);
            sumSpeed += sp;
            maxSquash = Math.max(maxSquash, Math.abs(p.squash));
            minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
            if (frames <= firstFrames) {
                fMinX = Math.min(fMinX, p.x); fMaxX = Math.max(fMaxX, p.x);
                fMinY = Math.min(fMinY, p.y); fMaxY = Math.max(fMaxY, p.y);
            }

            // 边界：精灵整体必须留在屏幕内（含浮力起伏的余量）
            double bobAmp = 5.5 * dp + Math.min(9 * dp, sp * 0.06);
            if (p.x - petW / 2 < -1 || p.x + petW / 2 > w + 1) {
                fail(label + ": 横向出界 x=" + p.x + " (允许 0.." + w + ")");
                bad = true;
                break;
            }
            if (p.y + p.bob - petH / 2 < topLimit - 0.5) {
                fail(label + ": 撞到顶部限制 y=" + p.y + " top=" + topLimit + " frame=" + i + " bob=" + p.bob + " bobAmp=" + bobAmp + " vx=" + p.vx + " vy=" + p.vy + " petH=" + petH + " h=" + h);
                bad = true;
                break;
            }
            if (p.y + p.bob + petH / 2 > h + 0.5) {
                fail(label + ": 底部出界 y=" + p.y);
                bad = true;
                break;
            }

            // 卡死检测：连续 600 帧位置几乎不变
            if (sp < 1.0) stuckLow++; else stuckLow = 0;
            if (stuckLow > 600) {
                fail(label + ": 卡死不动 600 帧");
                bad = true;
                break;
            }
            if (sp > vLimit) stuckHigh++;
            if (p.facingRight != prevRight) { flips++; prevRight = p.facingRight; }
        }

        if (bad) return;

        check(label + ": 速度有界", maxSpeed <= vLimit,
                String.format("max=%.1f 限=%.1f px/s", maxSpeed, vLimit));
        check(label + ": 平均速度在 10~200 px/s", avg(sumSpeed, frames) >= 10 && avg(sumSpeed, frames) <= 200,
                String.format("avg=%.1f px/s", avg(sumSpeed, frames)));
        check(label + ": 挤压弹簧不发散", maxSquash < 1.0, String.format("max|squash|=%.4f", maxSquash));
        check(label + ": 会来回改变朝向（不是单方向漂走）", flips > 20, "flips=" + flips);
        // 精灵占屏宽 42%，能走多远由「落点选择范围」决定：
        //   落点 ∈ [petW/2 + 10dp, w - petW/2 - 10dp]
        // 硬夹取允许再往外一点（最多到 petW/2），那是兜底，不算漫游范围。
        double padX = 10 * dp;
        double tMinX = petW / 2.0 + padX;
        double tMaxX = w - petW / 2.0 - padX;
        double reach = tMaxX - tMinX;
        double hardMinX = petW / 2.0 - 2, hardMaxX = w - petW / 2.0 + 2;
        if (fMinX < hardMinX || fMaxX > hardMaxX) {
            fail(label + ": 横向超出硬边界 x∈[" + Math.round(fMinX) + "," + Math.round(fMaxX)
                    + "] 硬边界 [" + Math.round(hardMinX) + "," + Math.round(hardMaxX) + "]");
            return;
        }
        // 屏幕越宽，落点范围越大，60 秒内走遍的比例自然越低 —— 判据按可达范围缩放
        double needFrac = Math.max(0.55, 0.80 - (reach / w) * 0.25);
        check(label + ": 60 秒内走遍落点范围（阈值随屏宽缩放）",
                (fMaxX - fMinX) > reach * needFrac,
                String.format("走过 %.0f / 落点范围 %.0f px (%.0f%%，需 %.0f%%)",
                        fMaxX - fMinX, reach, (fMaxX - fMinX) / reach * 100, needFrac * 100));
        check(label + ": 60 秒内覆盖屏幕纵向 45% 以上",
                (fMaxY - fMinY) > h * 0.45,
                String.format("y∈[%.0f,%.0f] 跨度=%.0f (需 %.0f)", fMinY, fMaxY, fMaxY - fMinY, h * 0.45));
        check(label + ": 一小时都待在硬边界内（不会卡在角落）",
                minX >= hardMinX && maxX <= hardMaxX,
                String.format("x∈[%.0f,%.0f] 硬边界 [%.0f,%.0f]", minX, maxX, hardMinX, hardMaxX));
        check(label + ": 速度超界帧数占比 <1%", stuckHigh < frames / 100,
                stuckHigh + "/" + frames);
    }

    static double avg(double sum, long n) {
        return n == 0 ? 0 : sum / n;
    }

    static boolean finite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    static double constant(String code, String regex, double dflt) {
        return constant(code, regex, dflt, false);
    }

    static double constant(String code, String regex, double dflt, boolean multiline) {
        Matcher m = Pattern.compile(regex, multiline ? Pattern.MULTILINE : 0).matcher(code);
        return m.find() ? Double.parseDouble(m.group(1)) : dflt;
    }

    static void check(String name, boolean ok, String detail) {
        checks++;
        if (!ok) fails++;
        System.out.println("  " + (ok ? "OK  " : "FAIL") + "  " + name + "   [" + detail + "]");
    }

    static void fail(String msg) {
        checks++;
        fails++;
        System.out.println("  FAIL  " + msg);
    }
}
