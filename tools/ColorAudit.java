import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 颜色审计 v2：不做缩放比对（缩放必然改变像素），只回答问题：
 *   1) 我抠出来的精灵里，不透明像素的颜色是不是原图里真实存在的颜色？
 *   2) 有没有被羽化（半透明）——那会让颜色在深色/浅色背景上看起来变掉。
 *   3) 有没有非背景像素被误删。
 */
public class ColorAudit {

    public static void main(String[] args) throws Exception {
        BufferedImage src = ImageIO.read(new File(args[0]));
        BufferedImage cut = ImageIO.read(new File(args[1]));
        int ox = Integer.parseInt(args[2]), oy = Integer.parseInt(args[3]);

        System.out.println("原图 " + src.getWidth() + "x" + src.getHeight()
                + "   精灵 " + cut.getWidth() + "x" + cut.getHeight());

        // 原图所有颜色（含背景），用 HashSet 做存在性检查
        java.util.HashSet<Integer> srcColors = new java.util.HashSet<>();
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++)
                srcColors.add(src.getRGB(x, y) & 0xffffff);

        int opaque = 0, semi = 0, exact = 0, near = 0, off = 0, fullyTransparent = 0;
        int maxOff = 0;
        java.util.Map<Integer, Integer> missing = new java.util.HashMap<>();

        for (int y = 0; y < cut.getHeight(); y++) {
            for (int x = 0; x < cut.getWidth(); x++) {
                int c = cut.getRGB(x, y);
                int a = (c >>> 24) & 0xff;
                if (a == 0) {
                    fullyTransparent++;
                    continue;
                }
                opaque++;
                if (a < 250) semi++;
                int rgb = c & 0xffffff;
                if (srcColors.contains(rgb)) {
                    exact++;
                    continue;
                }
                // 允许 ±2 的整数舍入误差
                if (nearIn(srcColors, rgb, 2)) near++;
                else {
                    off++;
                    missing.merge(rgb, 1, Integer::sum);
                }
            }
        }

        System.out.printf("%n精灵像素 %,d 个（不透明 %,d，全透明 %,d）%n",
                cut.getWidth() * cut.getHeight(), opaque, fullyTransparent);
        System.out.printf("  颜色与原图完全一致 : %,d  (%.2f%%)%n", exact, 100.0 * exact / Math.max(1, opaque));
        System.out.printf("  误差 ≤2/255        : %,d  (%.2f%%)%n", near, 100.0 * near / Math.max(1, opaque));
        System.out.printf("  偏差 >2/255        : %,d  (%.2f%%)%n", off, 100.0 * off / Math.max(1, opaque));
        System.out.printf("  被羽化成半透明     : %,d  (%.2f%%)%n", semi, 100.0 * semi / Math.max(1, opaque));
        if (!missing.isEmpty()) {
            System.out.println("  对不上的颜色（前 8 个）:");
            missing.entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .limit(8)
                    .forEach(e -> System.out.printf("    #%06x  %,d px%n", e.getKey(), e.getValue()));
        }
        System.out.println(off == 0 && semi == 0
                ? "\n结论：颜色与原图逐像素一致，且没有羽化 —— 官方配色没有被改动。"
                : "\n结论：存在颜色改动/羽化，需要返工。");
    }

    static boolean nearIn(java.util.HashSet<Integer> set, int rgb, int tol) {
        int r = (rgb >> 16) & 0xff, g = (rgb >> 8) & 0xff, b = rgb & 0xff;
        for (int dr = -tol; dr <= tol; dr++)
            for (int dg = -tol; dg <= tol; dg++)
                for (int db = -tol; db <= tol; db++) {
                    int rr = r + dr, gg = g + dg, bb = b + db;
                    if (rr < 0 || gg < 0 || bb < 0 || rr > 255 || gg > 255 || bb > 255) continue;
                    if (set.contains((rr << 16) | (gg << 8) | bb)) return true;
                }
        return false;
    }
}
