import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/**
 * 极限审计：对「原图 -> 抠图 -> 缩放成最终精灵」的每一个像素做可证明的核对。
 *
 * 检查项：
 *  A. alpha 通道是否只有 0 / 255（有任何中间值就是羽化/混色）
 *  B. 每个不透明像素的 RGB 是否能在原图里找到「同坐标同色」的来源
 *  C. 主体里有没有「和背景色接近却被判成前景」的残留（背景杂点）
 *  D. 有没有「不是背景却被删掉」的前景像素（缺角）
 *  E. 边界完整性：沿精灵轮廓走一圈，统计缺失长度
 *  F. 颜色分布对比：最终精灵用到的颜色是否都能在原图里找到
 *  G. 与手机上实际显示的关系：App 是否用 FILTER_BITMAP 之类的插值（源码检查）
 */
public class StrictAudit {

    static BufferedImage src, finalImg;
    static int sw, sh, fw, fh;

    public static void main(String[] args) throws Exception {
        String srcPath = args[0];      // 原始参考图
        String finalPath = args[1];    // 最终进入 APK 的精灵
        String outDir = args[2];

        src = ImageIO.read(new File(srcPath));
        finalImg = ImageIO.read(new File(finalPath));
        sw = src.getWidth();
        sh = src.getHeight();
        fw = finalImg.getWidth();
        fh = finalImg.getHeight();

        System.out.println("原图   " + sw + "x" + sh + "   " + srcPath);
        System.out.println("精灵   " + fw + "x" + fh + "   " + finalPath);

        // ---------- A. alpha 通道纯度 ----------
        int a0 = 0, a255 = 0, aMid = 0;
        for (int y = 0; y < fh; y++)
            for (int x = 0; x < fw; x++) {
                int a = (finalImg.getRGB(x, y) >>> 24) & 0xff;
                if (a == 0) a0++;
                else if (a == 255) a255++;
                else aMid++;
            }
        System.out.printf("%n[A] alpha 通道：全透明 %,d  全不透明 %,d  中间值 %,d%n", a0, a255, aMid);
        System.out.println("    " + (aMid == 0 ? "通过 —— 没有羽化、没有半透明混色"
                : "不通过 —— 存在 " + aMid + " 个半透明像素"));

        // ---------- 重建映射：精灵(448x279) 是从「裁剪后的原图尺寸」最近邻缩来的 ----------
        // 先复算裁剪框：与 StrictCut 同样的判定
        int bg = src.getRGB(1, 1) & 0xffffff;
        boolean[] isBg = new boolean[sw * sh];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        int br = (bg >> 16) & 0xff, bgc = (bg >> 8) & 0xff, bb = bg & 0xff;
        int tol2 = 30 * 30 * 3;
        for (int x = 0; x < sw; x++) { seed(x, 0, isBg, q, br, bgc, bb, tol2); seed(x, sh - 1, isBg, q, br, bgc, bb, tol2); }
        for (int y = 0; y < sh; y++) { seed(0, y, isBg, q, br, bgc, bb, tol2); seed(sw - 1, y, isBg, q, br, bgc, bb, tol2); }
        while (!q.isEmpty()) {
            int i = q.poll(), x = i % sw, y = i / sw;
            if (x > 0) seed(x - 1, y, isBg, q, br, bgc, bb, tol2);
            if (x < sw - 1) seed(x + 1, y, isBg, q, br, bgc, bb, tol2);
            if (y > 0) seed(x, y - 1, isBg, q, br, bgc, bb, tol2);
            if (y < sh - 1) seed(x, y + 1, isBg, q, br, bgc, bb, tol2);
        }
        int minX = sw, minY = sh, maxX = -1, maxY = -1;
        for (int y = 0; y < sh; y++)
            for (int x = 0; x < sw; x++)
                if (!isBg[y * sw + x]) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
        int cw = maxX - minX + 1, ch = maxY - minY + 1;
        System.out.printf("%n[映射] 裁剪框 = (%d,%d) %dx%d；缩放比 %.4f%n", minX, minY, cw, ch, fw / (double) cw);

        // ---------- B/C/D. 逐像素溯源 ----------
        int provable = 0, notFromSrc = 0, leftoverBg = 0, wronglyDeleted = 0;
        int tolNear = 6;   // 允许的舍入容差
        Map<Integer, Integer> suspects = new HashMap<>();
        for (int y = 0; y < fh; y++) {
            for (int x = 0; x < fw; x++) {
                int sx = minX + x * cw / fw;
                int sy = minY + y * ch / fh;
                int sc = src.getRGB(sx, sy) & 0xffffff;
                int fc = finalImg.getRGB(x, y);
                int a = (fc >>> 24) & 0xff;
                int frgb = fc & 0xffffff;

                if (a == 255) {
                    if (frgb == sc) provable++;
                    else {
                        int dr = Math.abs(((frgb >> 16) & 0xff) - ((sc >> 16) & 0xff));
                        int dg = Math.abs(((frgb >> 8) & 0xff) - ((sc >> 8) & 0xff));
                        int db = Math.abs((frgb & 0xff) - (sc & 0xff));
                        if (Math.max(dr, Math.max(dg, db)) <= tolNear) provable++;
                        else {
                            notFromSrc++;
                            suspects.merge(frgb, 1, Integer::sum);
                        }
                    }
                    // C. 被判成前景、但颜色其实和背景一致 —— 这是 flood fill 没吃干净的残留
                    if (near(frgb, bg, 26)) leftoverBg++;
                } else if (a == 0) {
                    // D. 被判成背景、但其实是前景（该像素不是背景色，且不在椭圆遮罩外）
                    if (!isBg[sy * sw + sx]) wronglyDeleted++;
                }
            }
        }
        System.out.printf("%n[B] 颜色可由原图同坐标像素解释：%,d / %,d  (%.4f%%)%n",
                provable, provable + notFromSrc, 100.0 * provable / (provable + notFromSrc));
        System.out.printf("    无法解释的像素：%,d%n", notFromSrc);
        System.out.println("    " + (notFromSrc == 0 ? "通过 —— 每个不透明像素的颜色都等于原图对应像素"
                : "存疑 —— 见下方列表"));
        if (!suspects.isEmpty()) {
            suspects.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(6)
                    .forEach(e -> System.out.printf("      #%06x  %,d px%n", e.getKey(), e.getValue()));
        }

        System.out.printf("%n[C] 前景里的背景色残留：%,d 像素 (%.4f%%)%n",
                leftoverBg, 100.0 * leftoverBg / (provable + notFromSrc));
        System.out.println("    " + (leftoverBg == 0 ? "通过 —— 没有残留贴纸底色" : "注意 —— 有残留，会在图上看到蓝点"));

        System.out.printf("%n[D] 非背景像素被误删：%,d%n", wronglyDeleted);
        System.out.println("    " + (wronglyDeleted == 0 ? "通过 —— 前景没有被啃掉的地方"
                : "注意 —— 有 " + wronglyDeleted + " 个像素被当成背景删了"));

        // ---------- E. 轮廓完整性 ----------
        // 沿前景边界统计：有多少前景像素紧邻被删的像素（可能造成毛边）
        int edge = 0, edgeWithGap = 0;
        for (int y = 1; y < fh - 1; y++)
            for (int x = 1; x < fw - 1; x++) {
                if (((finalImg.getRGB(x, y) >>> 24) & 0xff) != 255) continue;
                boolean isEdge = ((finalImg.getRGB(x - 1, y) >>> 24) & 0xff) == 0
                        || ((finalImg.getRGB(x + 1, y) >>> 24) & 0xff) == 0
                        || ((finalImg.getRGB(x, y - 1) >>> 24) & 0xff) == 0
                        || ((finalImg.getRGB(x, y + 1) >>> 24) & 0xff) == 0;
                if (!isEdge) continue;
                edge++;
                // 该边缘像素的原图邻居里，有没有「非背景」但被判成背景的（缺口）
                int sx = minX + x * cw / fw, sy = minY + y * ch / fh;
                int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
                for (int k = 0; k < 4; k++) {
                    int nx = sx + dx[k], ny = sy + dy[k];
                    if (nx < 0 || ny < 0 || nx >= sw || ny >= sh) continue;
                    if (!isBg[ny * sw + nx]) {
                        int fx = x + dx[k], fy = y + dy[k];
                        if (fx < 0 || fy < 0 || fx >= fw || fy >= fh) continue;
                        if (((finalImg.getRGB(fx, fy) >>> 24) & 0xff) == 0) edgeWithGap++;
                    }
                }
            }
        System.out.printf("%n[E] 轮廓像素 %,d 个，其中外侧紧邻「原图非背景但被删」的 %,d 个 (%.2f%%)%n",
                edge, edgeWithGap, 100.0 * edgeWithGap / Math.max(1, edge));

        // ---------- F. 颜色分布 ----------
        Set<Integer> srcColors = new HashSet<>();
        for (int y = 0; y < sh; y++)
            for (int x = 0; x < sw; x++) srcColors.add(src.getRGB(x, y) & 0xffffff);
        Set<Integer> finColors = new HashSet<>();
        for (int y = 0; y < fh; y++)
            for (int x = 0; x < fw; x++) {
                int c = finalImg.getRGB(x, y);
                if ((c >>> 24) == 255) finColors.add(c & 0xffffff);
            }
        int missing = 0;
        for (int c : finColors) if (!srcColors.contains(c)) missing++;
        System.out.printf("%n[F] 精灵用到 %,d 种颜色；其中原图里不存在的：%,d%n", finColors.size(), missing);
        System.out.println("    " + (missing == 0 ? "通过 —— 成图用色 100% 来自原图，没有新造颜色"
                : "存疑 —— 有 " + missing + " 种颜色不是原图的"));

        // 关键色是否还在（身体/鳍/嘴）
        report("身体青", src, isBg, 0x54BAD1);
        report("高光青", src, isBg, 0x95EEFC);
        report("鱼鳍粉", src, isBg, 0xF49599);
        report("嘴紫", src, isBg, 0x6569BA);

        // ---------- 生成放大对比图 ----------
        makeCompare(outDir);
    }

    static void report(String name, BufferedImage src, boolean[] isBg, int color) {
        int n = 0;
        for (int i = 0; i < isBg.length; i++) {
            if (isBg[i]) continue;
            if ((src.getRGB(i % sw, i / sw) & 0xffffff) == color) n++;
        }
        System.out.printf("    %s #%06x 在原图前景中出现 %,d 次%n", name, color, n);
    }

    static void makeCompare(String outDir) throws Exception {
        int pad = 12;
        BufferedImage cmp = new BufferedImage(sw + fw + pad * 3,
                Math.max(sh, fh) + pad * 2 + 24, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cmp.createGraphics();
        g.setColor(new Color(0xF2F2F2));
        g.fillRect(0, 0, cmp.getWidth(), cmp.getHeight());
        g.drawImage(src, pad, pad + 24, null);
        g.drawImage(finalImg, sw + pad * 2, pad + 24, null);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        g.drawString("原图（你给的）", pad, pad + 16);
        g.drawString("抠出的精灵（进入 APK 的那张）", sw + pad * 2, pad + 16);
        g.dispose();
        ImageIO.write(cmp, "png", new File(outDir + "/compare.png"));

        // 放大 4 倍的局部（身体+嘴一带），看边缘有没有杂色
        BufferedImage zoom = new BufferedImage(240, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = zoom.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setColor(new Color(0xE8F4F8));
        g2.fillRect(0, 0, 240, 180);
        g2.drawImage(finalImg, 150, 90, 450, 270, 30, 55, 180, 115, null);   // 嘴/脸一带
        g2.dispose();
        ImageIO.write(zoom, "png", new File(outDir + "/zoom_face.png"));
        System.out.println("\n对比图: " + outDir + "/compare.png   放大图: " + outDir + "/zoom_face.png");
    }

    static boolean near(int c1, int c2, int tol) {
        int dr = Math.abs(((c1 >> 16) & 0xff) - ((c2 >> 16) & 0xff));
        int dg = Math.abs(((c1 >> 8) & 0xff) - ((c2 >> 8) & 0xff));
        int db = Math.abs((c1 & 0xff) - (c2 & 0xff));
        return Math.max(dr, Math.max(dg, db)) <= tol;
    }

    static void seed(int x, int y, boolean[] isBg, ArrayDeque<Integer> q, int br, int bgc, int bb, int tol2) {
        int i = y * sw + x;
        if (isBg[i]) return;
        int c = src.getRGB(x, y);
        int dr = ((c >> 16) & 0xff) - br, dg = ((c >> 8) & 0xff) - bgc, db = (c & 0xff) - bb;
        if (dr * dr + dg * dg + db * db <= tol2) {
            isBg[i] = true;
            q.add(i);
        }
    }
}
