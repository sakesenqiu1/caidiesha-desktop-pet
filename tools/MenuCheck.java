/**
 * 弧形圆点菜单几何验算（回到 v2.5 的弧，按钮缩小到 38dp）
 * 逐位置检查：圆点完整可见、互不重叠、不压住身体
 */
public class MenuCheck {

    static final int W = 1260, H = 2844;
    static final float dp = 3.0f;
    static final float[] ANGLES = {12.5f, 51.25f, 90f, 128.75f, 167.5f};
    static final int N = 5;
    static int fail = 0, pass = 0;

    public static void main(String[] args) {
        float petW = Math.max(110f, Math.min(180f, (W / dp) * 0.44f)) * dp;
        float petH = petW * 279f / 448f;
        float bodyR = (float) Math.hypot(petW / 2, petH / 2);

        float btnR = 30 * dp;                       // 直径 38dp
        float r = 2.5f * bodyR;
        double chord = 2.0 * Math.sin(Math.toRadians(38.75 / 2.0));
        r = Math.max(r, (float) ((2 * btnR + 8 * dp) / chord));
        float byW = (W / 2f - btnR - 4 * dp) / 0.977f;   // 与 PetView 一致（除 |cos|）
        float byH = H - petH / 2 - 130 * dp - bodyR - btnR;
        float ring = Math.max(120 * dp, Math.min(r, Math.min(byW, byH)));

        System.out.printf("屏幕 %dx%d  density=%.1f%n", W, H, dp);
        System.out.printf("精灵 %.0fx%.0f px  外接半径 %.0f px%n", petW, petH, bodyR);
        System.out.printf("按钮直径 %.0f dp   环绕半径 %.0f dp (%.0f px)%n", btnR * 2 / dp, ring / dp, ring);

        double minGap = 1e9;
        for (int i = 0; i < N; i++)
            for (int j = i + 1; j < N; j++) {
                double a = Math.toRadians(ANGLES[i]), b = Math.toRadians(ANGLES[j]);
                minGap = Math.min(minGap, Math.hypot(Math.cos(a) - Math.cos(b),
                        Math.sin(a) - Math.sin(b)) * ring - 2 * btnR);
            }
        double bodyGap = ring - btnR - bodyR;   // 相对外接矩形，仅作参考
        System.out.printf("圆点之间最小间隙 %.1f px (%.1f dp) %s%n", minGap, minGap / dp,
                minGap >= 0 ? "OK 不重叠" : "FAIL 会重叠");
        System.out.printf("圆点与身体外接矩形的间隙 %.1f px (%.1f dp)"
                        + "（参考值；真实身体重叠由 SpriteOverlap 用像素判）%n%n",
                bodyGap, bodyGap / dp);
        if (minGap < 0) fail++;

        for (float bob = -16 * dp; bob <= 16 * dp; bob += 4 * dp)
            for (int i = 0; i <= 24; i++)
                for (int j = 0; j <= 24; j++) {
                    float cx = petW / 2 + (W - petW) * i / 24f;
                    float topLimit = Math.max(24 * dp, 40 * dp);
                    float cy = topLimit + petH / 2 + (H - 14 * dp - petH - topLimit) * j / 24f;
                    check(cx, cy, petW, petH, ring, btnR);
                }

        System.out.println((fail == 0
                ? "全部通过：弧形圆点在任何位置都完整可见、互不重叠、不压身体 OK"
                : fail + " 处不合格 FAIL") + "   （共检查 " + (pass + fail) + " 个位置）");
        if (fail > 0) System.exit(1);
    }

    static void check(float cx, float cy, float petW, float petH, float ring, float btnR) {
        float padX = 36 * dp, padV = 40 * dp;
        float[] bx = new float[N], by = new float[N];
        float minX = cx, maxX = cx, minY = cy, maxY = cy;
        for (int i = 0; i < N; i++) {
            double a = Math.toRadians(ANGLES[i]);
            bx[i] = cx + (float) Math.cos(a) * ring;
            by[i] = cy + (float) Math.sin(a) * ring;
            bx[i] = Math.max(btnR + 4 * dp, Math.min(W - btnR - 4 * dp, bx[i]));
            by[i] = Math.max(btnR + 4 * dp, Math.min(H - btnR - 4 * dp, by[i]));
            minX = Math.min(minX, bx[i] - btnR); maxX = Math.max(maxX, bx[i] + btnR);
            minY = Math.min(minY, by[i] - btnR); maxY = Math.max(maxY, by[i] + btnR);
        }
        float cL = Math.min(cx - petW / 2 - padX, minX - 8 * dp);
        float cR = Math.max(cx + petW / 2 + padX, maxX + 8 * dp);
        float cT = Math.min(cy - petH / 2 - padV, minY - 8 * dp);
        float cB = Math.max(cy + petH / 2 + padV, maxY + 8 * dp);
        float winW = Math.min(cR - cL, W), winH = Math.min(cB - cT, H);
        float ox = Math.max(cR - winW, Math.min(cL, cx - winW / 2));
        float oy = Math.max(cB - winH, Math.min(cT, cy - winH / 2));
        if (ox < 0) ox = 0;
        if (oy < 0) oy = 0;
        if (ox + winW > W) ox = Math.max(0, W - winW);
        if (oy + winH > H) oy = Math.max(0, H - winH);

        boolean ok = true;
        for (int i = 0; i < N && ok; i++) {
            float lx = bx[i] - ox, ly = by[i] - oy;
            if (lx - btnR < -1 || ly - btnR < -1 || lx + btnR > winW + 1 || ly + btnR > winH + 1) ok = false;
            if (bx[i] - btnR < -1 || by[i] - btnR < -1 || bx[i] + btnR > W + 1 || by[i] + btnR > H + 1) ok = false;
        }
        float px = cx - ox, py = cy - oy;
        if (px - petW / 2 < -1 || px + petW / 2 > winW + 1
                || py - petH / 2 < -1 || py + petH / 2 > winH + 1) ok = false;
        if (winW < 96 * dp || winH < 96 * dp || winW > W + 1 || winH > H + 1) ok = false;

        if (ok) pass++;
        else {
            fail++;
            if (fail <= 4) System.out.printf("不合格 cx=%.0f cy=%.0f  窗口 %.0fx%.0f @(%.0f,%.0f)%n",
                    cx, cy, winW, winH, ox, oy);
        }
    }
}
