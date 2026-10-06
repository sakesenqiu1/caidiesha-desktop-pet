import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 菜单与「真实身体」的重叠检查：
 * 用精灵图的不透明像素当身体（而不是外接矩形 —— 贴纸四角是透明的，
 * 之前用矩形判会导致误报「压住身体」）。
 *
 * 单位换算：精灵图宽 448px 对应 App 里的 petW 像素，据此把按钮坐标映射回图内坐标。
 */
public class SpriteOverlap {

    /** 图片外算透明；alpha > 40 算身体 */
    static boolean isBody(BufferedImage img, int x, int y, int iw, int ih) {
        if (x < 0 || y < 0 || x >= iw || y >= ih) return false;
        return ((img.getRGB(x, y) >>> 24) & 0xff) > 40;
    }

    public static void main(String[] args) throws Exception {
        BufferedImage img = ImageIO.read(new File(args[0]));
        float petW = Float.parseFloat(args[1]);      // App 里精灵宽度（px，含贴纸透明留白）
        float petH = Float.parseFloat(args[2]);
        float btnR = Float.parseFloat(args[3]);      // 按钮半径（App px）
        float ring = Float.parseFloat(args[4]);      // 环绕半径（App px）
        float[] angles = {12.5f, 51.25f, 92f, 128.75f, 167.5f};

        int iw = img.getWidth(), ih = img.getHeight();
        double sc = iw / (double) petW;              // App px -> 图内 px

        System.out.printf("精灵图 %dx%d   App 里 %.0fx%.0f px   缩放系数 %.4f%n",
                iw, ih, petW, petH, sc);
        System.out.printf("按钮半径 %.0f px (图内 %.1f px)   环绕半径 %.0f px (图内 %.1f px)%n%n",
                btnR, btnR * sc, ring, ring * sc);

        int worst = Integer.MAX_VALUE;
        String worstName = "";
        for (int k = 0; k < angles.length; k++) {
            double a = Math.toRadians(angles[k]);
            // 图内坐标：精灵中心 = 图中心
            double icx = iw / 2.0 + Math.cos(a) * ring * sc;
            double icy = ih / 2.0 + Math.sin(a) * ring * sc;
            double ir = btnR * sc;

            // 统计圆内有多少「身体像素」（图片外一律算透明）
            int hit = 0, total = 0;
            for (int y = (int) (icy - ir); y <= (int) (icy + ir); y++) {
                for (int x = (int) (icx - ir); x <= (int) (icx + ir); x++) {
                    if (Math.hypot(x - icx, y - icy) > ir) continue;
                    total++;
                    if (isBody(img, x, y, iw, ih)) hit++;
                }
            }
            // 圆外沿到身体的最近距离：沿圆周向外采样，找第一个身体像素
            int minDist = Integer.MAX_VALUE;
            for (int d = 0; d < 600; d++) {
                boolean found = false;
                for (int s = 0; s < 720; s++) {
                    double t = Math.toRadians(s / 2.0);
                    int x = (int) Math.round(icx + Math.cos(t) * (ir + d));
                    int y = (int) Math.round(icy + Math.sin(t) * (ir + d));
                    if (isBody(img, x, y, iw, ih)) { found = true; break; }
                }
                if (found) { minDist = d; break; }
            }
            worst = Math.min(worst, minDist);
            if (minDist == worst) worstName = "按钮" + (k + 1) + "(角度" + angles[k] + "°)";
            System.out.printf("按钮%d (%.1f°)  圆心图内(%.0f,%.0f)  压住身体像素 %d/%d  圆外最近身体距离 %s%n",
                    k + 1, angles[k], icx, icy, hit, total,
                    minDist == Integer.MAX_VALUE ? ">600px(图内)" : minDist + " 图内px");
        }
        System.out.println();
        boolean clean = worst > 0;
        System.out.println(clean
                ? "结论：5 个圆点都没有压到真实身体像素（最近距离 " + worst + " px = "
                  + String.format("%.1f", worst / sc) + " App px）OK"
                : "结论：有圆点与身体像素重叠（最近距离 " + worst + " px）FAIL");
        if (!clean) System.exit(1);
    }
}
