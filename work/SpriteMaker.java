import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.util.ArrayDeque;

/**
 * 彩蝶鲨桌宠素材生成器 v2
 * 用法: java SpriteMaker <in.jpg> <outDir> <trimX,trimY,trimW,trimH> <tolerance> <targetWidth> <radiusFactor>
 * radiusFactor > 0 时把整图裁成圆形（用于只取贴纸主体）
 */
public class SpriteMaker {

    public static void main(String[] args) throws Exception {
        String in = args[0], outDir = args[1];
        String[] t = args[2].split(",");
        int tx = Integer.parseInt(t[0]), ty = Integer.parseInt(t[1]);
        int tw = Integer.parseInt(t[2]), th = Integer.parseInt(t[3]);
        int tol = Integer.parseInt(args[3]);
        int target = Integer.parseInt(args[4]);
        double circle = Double.parseDouble(args[5]);
        String name = args[6];
        // 可选的第 7 个参数: cx,cy,rx,ry 椭圆遮罩（图内相对 0~1），用来切掉贴纸上的气泡
        double[] ell = null;
        if (args.length > 7 && !args[7].isEmpty()) {
            String[] e = args[7].split(",");
            ell = new double[]{Double.parseDouble(e[0]), Double.parseDouble(e[1]),
                    Double.parseDouble(e[2]), Double.parseDouble(e[3])};
        }

        BufferedImage full = ImageIO.read(new File(in));
        System.out.println("src = " + full.getWidth() + "x" + full.getHeight());

        BufferedImage sub = crop(full, tx, ty, tw, th);
        if (ell != null) sub = elliptical(sub, ell);
        if (circle > 0) sub = circular(sub, circle);
        BufferedImage cut = removeBackground(sub, tol);
        cut = feather(cut);
        BufferedImage pet = fitWidth(trim(cut, 3), target);
        write(pet, outDir + "/" + name + ".png");
        System.out.println("out = " + pet.getWidth() + "x" + pet.getHeight());
    }

    static BufferedImage crop(BufferedImage s, int x, int y, int w, int h) {
        x = Math.max(0, x); y = Math.max(0, y);
        w = Math.min(w, s.getWidth() - x);
        h = Math.min(h, s.getHeight() - y);
        return s.getSubimage(x, y, w, h);
    }

    /** 椭圆遮罩：贴纸主体偏在一侧时，用它把不需要的气泡/水印切掉 */
    static BufferedImage elliptical(BufferedImage src, double[] e) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(new Ellipse2D.Double(w * (e[0] - e[2]), h * (e[1] - e[3]), w * 2 * e[2], h * 2 * e[3]));
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** 圆形遮罩：贴纸里主体居中时很好用（圆外直接透明） */
    static BufferedImage circular(BufferedImage src, double r) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(new Ellipse2D.Double(w * (0.5 - r), h * (0.5 - r), w * 2 * r, h * 2 * r));
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    static BufferedImage removeBackground(BufferedImage src, int tol) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();

        int[] px = out.getRGB(0, 0, w, h, null, 0, w);
        boolean[] bg = new boolean[w * h];
        ArrayDeque<Integer> q = new ArrayDeque<>();

        int sr = 0, sg = 0, sb = 0, n = 0;
        for (int x = 0; x < w; x++) {
            for (int y : new int[]{0, h - 1}) {
                int c = px[y * w + x];
                if (((c >>> 24) & 0xff) < 200) continue;
                sr += (c >> 16) & 0xff; sg += (c >> 8) & 0xff; sb += c & 0xff; n++;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x : new int[]{0, w - 1}) {
                int c = px[y * w + x];
                if (((c >>> 24) & 0xff) < 200) continue;
                sr += (c >> 16) & 0xff; sg += (c >> 8) & 0xff; sb += c & 0xff; n++;
            }
        }
        if (n == 0) n = 1;
        final int br = sr / n, bgc = sg / n, bb = sb / n;
        System.out.println("  bg = #" + String.format("%02x%02x%02x", br, bgc, bb) + "  tol=" + tol);

        final int tol2 = tol * tol * 3;
        for (int x = 0; x < w; x++) { push(q, bg, px, w, h, x, 0, br, bgc, bb, tol2); push(q, bg, px, w, h, x, h - 1, br, bgc, bb, tol2); }
        for (int y = 0; y < h; y++) { push(q, bg, px, w, h, 0, y, br, bgc, bb, tol2); push(q, bg, px, w, h, w - 1, y, br, bgc, bb, tol2); }

        while (!q.isEmpty()) {
            int idx = q.poll();
            int x = idx % w, y = idx / w;
            if (x > 0) push(q, bg, px, w, h, x - 1, y, br, bgc, bb, tol2);
            if (x < w - 1) push(q, bg, px, w, h, x + 1, y, br, bgc, bb, tol2);
            if (y > 0) push(q, bg, px, w, h, x, y - 1, br, bgc, bb, tol2);
            if (y < h - 1) push(q, bg, px, w, h, x, y + 1, br, bgc, bb, tol2);
        }

        int cleared = 0;
        for (int i = 0; i < px.length; i++) if (bg[i]) { px[i] = 0; cleared++; }
        System.out.println("  cleared " + cleared + "/" + px.length);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    static void push(ArrayDeque<Integer> q, boolean[] bg, int[] px, int w, int h,
                     int x, int y, int br, int bgc, int bb, int tol2) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        int i = y * w + x;
        if (bg[i]) return;
        int c = px[i];
        if (((c >>> 24) & 0xff) == 0) { bg[i] = true; return; }
        int dr = ((c >> 16) & 0xff) - br, dg = ((c >> 8) & 0xff) - bgc, db = (c & 0xff) - bb;
        if (dr * dr + dg * dg + db * db <= tol2) { bg[i] = true; q.add(i); }
    }

    /** alpha 通道做一次 3x3 均值，去掉硬锯齿 */
    static BufferedImage feather(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage alpha = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int a = (src.getRGB(x, y) >>> 24) & 0xff;
                alpha.setRGB(x, y, 0xff000000 | (a << 16) | (a << 8) | a);
            }
        float[] k = new float[9];
        for (int i = 0; i < 9; i++) k[i] = 1f / 9f;
        BufferedImage blurred = new ConvolveOp(new Kernel(3, 3, k), ConvolveOp.EDGE_NO_OP, null).filter(alpha, null);

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int c = src.getRGB(x, y);
                int a0 = (c >>> 24) & 0xff;
                if (a0 == 0) { out.setRGB(x, y, 0); continue; }
                int a1 = blurred.getRGB(x, y) & 0xff;
                out.setRGB(x, y, (Math.min(a0, a1 + 45) << 24) | (c & 0x00ffffff));
            }
        return out;
    }

    static BufferedImage trim(BufferedImage src, int pad) {
        int w = src.getWidth(), h = src.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (((src.getRGB(x, y) >>> 24) & 0xff) > 12) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
        if (maxX < 0) return src;
        minX = Math.max(0, minX - pad); minY = Math.max(0, minY - pad);
        maxX = Math.min(w - 1, maxX + pad); maxY = Math.min(h - 1, maxY + pad);
        BufferedImage out = new BufferedImage(maxX - minX + 1, maxY - minY + 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, -minX, -minY, null);
        g.dispose();
        System.out.println("  bbox = " + out.getWidth() + "x" + out.getHeight());
        return out;
    }

    static BufferedImage fitWidth(BufferedImage src, int tw) {
        double s = tw / (double) src.getWidth();
        int w = tw, h = Math.max(1, (int) Math.round(src.getHeight() * s));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    static void write(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println("  -> " + path + " (" + f.length() + " B)");
    }
}
