import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 严格模式抠图：只决定「每个像素是背景还是彩蝶鲨」，绝不改动任何像素的颜色。
 *
 * 规则：
 *   1) 从四条边 flood fill，只有和边缘背景色足够接近、且连通到边界的像素才算背景。
 *   2) 判为背景 -> alpha=0（RGB 原样保留，不预乘、不调色）。
 *   3) 判为主体 -> alpha=255，RGB 一个比特都不动。
 *   4) 缩小时用「最近邻」只搬运像素，不做任何插值混合 —— 所以颜色仍然是原图里那一组颜色。
 *   5) 不羽化、不加描边、不做任何"美化"。
 */
public class StrictCut {

    public static void main(String[] args) throws Exception {
        String in = args[0], out = args[1];
        int tol = Integer.parseInt(args[2]);
        int outW = Integer.parseInt(args[3]);
        String name = args[4];
        double[] ell = null;
        if (args.length > 5 && !args[5].isEmpty()) {
            String[] e = args[5].split(",");
            ell = new double[]{Double.parseDouble(e[0]), Double.parseDouble(e[1]),
                    Double.parseDouble(e[2]), Double.parseDouble(e[3])};
        }

        BufferedImage src = ImageIO.read(new File(in));
        int w = src.getWidth(), h = src.getHeight();
        System.out.println("原图 " + w + "x" + h);

        int bg = src.getRGB(1, 1) & 0xffffff;
        System.out.printf("边缘背景色 #%06x  容差 %d%n", bg, tol);

        boolean[] isBg = new boolean[w * h];
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        int br = (bg >> 16) & 0xff, bgc = (bg >> 8) & 0xff, bb = bg & 0xff;
        int tol2 = tol * tol * 3;

        // 边界种子
        for (int x = 0; x < w; x++) { seed(src, isBg, q, w, h, x, 0, br, bgc, bb, tol2); seed(src, isBg, q, w, h, x, h - 1, br, bgc, bb, tol2); }
        for (int y = 0; y < h; y++) { seed(src, isBg, q, w, h, 0, y, br, bgc, bb, tol2); seed(src, isBg, q, w, h, w - 1, y, br, bgc, bb, tol2); }
        while (!q.isEmpty()) {
            int i = q.poll();
            int x = i % w, y = i / w;
            if (x > 0) seed(src, isBg, q, w, h, x - 1, y, br, bgc, bb, tol2);
            if (x < w - 1) seed(src, isBg, q, w, h, x + 1, y, br, bgc, bb, tol2);
            if (y > 0) seed(src, isBg, q, w, h, x, y - 1, br, bgc, bb, tol2);
            if (y < h - 1) seed(src, isBg, q, w, h, x, y + 1, br, bgc, bb, tol2);
        }

        // 贴纸上的额外图形（例如气泡）用椭圆圈掉：圈外一律当背景
        if (ell != null) {
            double cx = w * ell[0], cy = h * ell[1], rx = w * ell[2], ry = h * ell[3];
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    double dx = (x - cx) / rx, dy = (y - cy) / ry;
                    if (dx * dx + dy * dy > 1.0) isBg[y * w + x] = true;
                }
        }

        // 组装 ARGB：主体 alpha=255 且 RGB 原样；背景 alpha=0
        BufferedImage argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int kept = 0;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y) & 0xffffff;
                row[x] = isBg[y * w + x] ? 0 : (0xff000000 | rgb);
                if (!isBg[y * w + x]) kept++;
            }
            argb.setRGB(0, y, w, 1, row, 0, w);
        }
        System.out.printf("  主体像素 %,d / %,d%n", kept, w * h);

        // 裁到主体包围盒（只裁，不缩放）
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if ((argb.getRGB(x, y) >>> 24) != 0) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
        BufferedImage cropped = argb.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        System.out.println("  裁剪后 " + cropped.getWidth() + "x" + cropped.getHeight());

        // 最近邻缩放（纯像素搬运，不插值 -> 颜色不变）
        int tw = Math.min(outW, cropped.getWidth());
        int th = Math.max(1, (int) Math.round(cropped.getHeight() * (tw / (double) cropped.getWidth())));
        BufferedImage outImg = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        int[] buf = new int[tw];
        for (int y = 0; y < th; y++) {
            int sy = minY + y * cropped.getHeight() / th;
            for (int x = 0; x < tw; x++) {
                int sx = minX + x * cropped.getWidth() / tw;
                buf[x] = argb.getRGB(sx, sy);
            }
            outImg.setRGB(0, y, tw, 1, buf, 0, tw);
        }

        File f = new File(out + "/" + name + ".png");
        f.getParentFile().mkdirs();
        ImageIO.write(outImg, "png", f);
        System.out.println("  -> " + f.getPath() + "  " + tw + "x" + th + "  " + f.length() + " B");
    }

    static void seed(BufferedImage src, boolean[] isBg, java.util.ArrayDeque<Integer> q,
                     int w, int h, int x, int y, int br, int bgc, int bb, int tol2) {
        int i = y * w + x;
        if (isBg[i]) return;
        int c = src.getRGB(x, y);
        int dr = ((c >> 16) & 0xff) - br, dg = ((c >> 8) & 0xff) - bgc, db = (c & 0xff) - bb;
        if (dr * dr + dg * dg + db * db <= tol2) {
            isBg[i] = true;
            q.add(i);
        }
    }
}
