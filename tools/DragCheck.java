/**
 * 拖动仿真 v2：忠实复刻两种写法，并模拟「窗口更新滞后于手指」。
 *
 * 旧写法：onTouchEvent 里把 e.getX() + originX 当屏幕坐标，再取两次事件的增量，
 *         position += (raw(t) - origin(t)) - (raw(t-1) - origin(t-1))
 *         —— origin 每帧都在变，增量就错了，误差会一直累积。
 * 新写法：position = raw(t) + grabOffset（绝对定位），与 origin 无关。
 *
 * 指标：精灵中心与「理想位置（手指 + 按下偏移）」的最大偏差 / 平均偏差。
 */
public class DragCheck {

    static final int W = 1260, H = 2844;
    static final float dp = 3.0f;
    static final float petW = 180f * dp, petH = petW * 279f / 448f;
    static final float winW = petW + 72f * dp, winH = petH + 80f * dp;

    public static void main(String[] args) {
        System.out.printf("屏幕 %dx%d  精灵 %.0fx%.0f  窗口 %.0fx%.0f%n%n", W, H, petW, petH, winW, winH);
        System.out.println("手指每帧移动 (3.5, -5) px，共 90 帧（全程不触边）；窗口每 lag 帧真正更新一次");
        System.out.println("（lag 越大表示窗口跟得越慢，正是你拖动时遇到的「卡几下」）\n");
        System.out.printf("%-6s %-28s %-28s%n", "lag", "旧写法 最大/平均偏差", "新写法 最大/平均偏差");
        for (int lag : new int[]{1, 2, 4, 8, 16}) {
            double[] a = run(false, lag);
            double[] b = run(true, lag);
            System.out.printf("%-6d %-28s %-28s%n", lag,
                    String.format("%8.1f / %8.1f px", a[0], a[1]),
                    String.format("%8.3f / %8.3f px", b[0], b[1]));
        }
        System.out.println();
        double worstNew = 0;
        for (int lag : new int[]{1, 2, 4, 8, 16}) worstNew = Math.max(worstNew, run(true, lag)[0]);
        if (worstNew < 0.01) {
            System.out.println("新写法：无论窗口滞后多少，精灵落点始终等于手指位置（最大偏差 "
                    + String.format("%.4f", worstNew) + " px）✓");
        } else {
            System.out.println("新写法仍有最大 " + worstNew + " px 偏差 ✗");
            System.exit(1);
        }
    }

    /** @return {最大偏差, 平均偏差} */
    static double[] run(boolean absolute, int lag) {
        float petX = W * 0.5f, petY = H * 0.6f;
        float raw0X = W * 0.5f, raw0Y = H * 0.6f;   // 轨迹全程留在可达范围内
        float grabX = petX - raw0X, grabY = petY - raw0Y;

        float originX = petX - winW / 2f, originY = petY - winH / 2f;
        float prevRawX = raw0X, prevRawY = raw0Y;
        float prevOriginX = originX, prevOriginY = originY;

        double maxErr = 0, sumErr = 0, minY = 1e9, maxY = -1e9, minX = 1e9, maxX = -1e9;
        int n = 0;
        for (int i = 1; i <= 90; i++) {
            float rawX = raw0X + 3.5f * i;
            float rawY = raw0Y - 5f * i;

            if (absolute) {
                petX = rawX + grabX;
                petY = rawY + grabY;
            } else {
                // 旧写法：屏幕坐标 = View内坐标 + origin，再取增量
                float curSx = rawX - originX;      // 这就是 e.getX()
                float curSy = rawY - originY;
                float prevSx = prevRawX - prevOriginX;
                float prevSy = prevRawY - prevOriginY;
                petX += (curSx - prevSx);
                petY += (curSy - prevSy);
            }
            // clampToScreen
            petX = Math.max(petW / 2, Math.min(W - petW / 2, petX));
            petY = Math.max(50 * dp + petH / 2, Math.min(H - petH / 2 - 14 * dp, petY));

            prevRawX = rawX;
            prevRawY = rawY;
            prevOriginX = originX;
            prevOriginY = originY;

            // 窗口几何（每帧算，但每 lag 帧才真正生效）
            float wantX = Math.max(0, Math.min(W - winW, petX - winW / 2f));
            float wantY = Math.max(0, Math.min(H - winH, petY - winH / 2f));
            if (i % lag == 0) {
                originX = wantX;
                originY = wantY;
            }

            double ex = petX - (rawX + grabX);
            double ey = petY - (rawY + grabY);
            double e = Math.hypot(ex, ey);
            maxErr = Math.max(maxErr, e);
            sumErr += e;
            n++;
            minX = Math.min(minX, petX); maxX = Math.max(maxX, petX);
            minY = Math.min(minY, petY); maxY = Math.max(maxY, petY);
        }
        return new double[]{maxErr, sumErr / n};
    }
}
