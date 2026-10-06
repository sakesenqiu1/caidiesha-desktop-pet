import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/**
 * 边缘与配色复核：
 *  1) 统计「靠近背景色」的前景像素有多少、有多接近 —— 这是原图自带的抗锯齿晕边
 *  2) 把这些像素分别合成到白色/深色背景上，看实际观感（并输出对比图）
 *  3) 找出嘴部区域真正出现的紫色（我界面上之前用的 #6569BA 是色相平均值，原图里并不存在）
 */
public class EdgeCheck {

    public static void main(String[] args) throws Exception {
        String srcPath = args[0], petPath = args[1], outDir = args[2];
        BufferedImage src = ImageIO.read(new File(srcPath));
        BufferedImage pet = ImageIO.read(new File(petPath));
        int sw = src.getWidth(), sh = src.getHeight();
        int fw = pet.getWidth(), fh = pet.getHeight();
        int bg = src.getRGB(1, 1) & 0xffffff;
        System.out.printf("背景色 #%06x%n", bg);

        // 1) 前景像素与背景色的距离分布
        int[] bucket = new int[8];
        String[] names = {"0-5 (几乎就是背景)", "6-10", "11-20", "21-30", "31-45", "46-70", "71-110", ">110"};
        int opaque = 0;
        for (int y = 0; y < fh; y++)
            for (int x = 0; x < fw; x++) {
                int c = pet.getRGB(x, y);
                if ((c >>> 24) != 255) continue;
                opaque++;
                int d = dist(c & 0xffffff, bg);
                if (d <= 5) bucket[0]++;
                else if (d <= 10) bucket[1]++;
                else if (d <= 20) bucket[2]++;
                else if (d <= 30) bucket[3]++;
                else if (d <= 45) bucket[4]++;
                else if (d <= 70) bucket[5]++;
                else if (d <= 110) bucket[6]++;
                else bucket[7]++;
            }
        System.out.printf("%n前景像素 %,d 个，与背景色的距离分布：%n", opaque);
        for (int i = 0; i < 8; i++)
            System.out.printf("  %-20s %,7d  (%.3f%%)%n", names[i], bucket[i], 100.0 * bucket[i] / opaque);

        // 2) 合成到白/深色底，输出对比图
        BufferedImage onWhite = compose(pet, Color.WHITE, 2);
        BufferedImage onDark = compose(pet, new Color(0x1B2A33), 2);
        BufferedImage cmp = new BufferedImage(onWhite.getWidth() * 2 + 24,
                onWhite.getHeight() + 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cmp.createGraphics();
        g.setColor(new Color(0xDDDDDD));
        g.fillRect(0, 0, cmp.getWidth(), cmp.getHeight());
        g.drawImage(onWhite, 8, 32, null);
        g.drawImage(onDark, onWhite.getWidth() + 16, 32, null);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        g.drawString("合成到白色背景", 8, 22);
        g.drawString("合成到深色背景", onWhite.getWidth() + 16, 22);
        g.dispose();
        ImageIO.write(cmp, "png", new File(outDir + "/composite.png"));

        // 放大 3 倍看边缘
        BufferedImage zoom = zoom(pet, Color.WHITE, 60, 40, 120, 70, 3);
        ImageIO.write(zoom, "png", new File(outDir + "/zoom_edge.png"));
        System.out.println("\n已输出: " + outDir + "/composite.png  " + outDir + "/zoom_edge.png");

        // 3) 嘴部真实紫色：在原图里按「紫蓝色相 + 饱和」找
        Map<Integer, Integer> purples = new HashMap<>();
        for (int y = 0; y < sh; y++)
            for (int x = 0; x < sw; x++) {
                int c = src.getRGB(x, y) & 0xffffff;
                float[] hsb = Color.RGBtoHSB((c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff, null);
                float hue = hsb[0] * 360;
                if (hue >= 225 && hue <= 285 && hsb[1] > 0.35 && hsb[2] > 0.35)
                    purples.merge(c, 1, Integer::sum);
            }
        System.out.println("\n原图里真正的紫蓝色（嘴部），出现最多的 10 种：");
        purples.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(10)
                .forEach(e -> System.out.printf("  #%06x  %,d px%n", e.getKey(), e.getValue()));
        System.out.println("（之前界面上用的 #6569BA 是色相加权平均，原图里并不存在这个像素值）");
    }

    static BufferedImage compose(BufferedImage pet, Color bgc, int scale) {
        int w = pet.getWidth() * scale, h = pet.getHeight() * scale;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(bgc);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(pet, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    static BufferedImage zoom(BufferedImage pet, Color bgc, int x0, int y0, int w0, int h0, int scale) {
        int w = w0 * scale, h = h0 * scale;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(bgc);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(pet, 0, 0, w, h, x0, y0, x0 + w0, y0 + h0, null);
        g.dispose();
        return out;
    }

    static int dist(int a, int b) {
        int dr = ((a >> 16) & 0xff) - ((b >> 16) & 0xff);
        int dg = ((a >> 8) & 0xff) - ((b >> 8) & 0xff);
        int db = (a & 0xff) - (b & 0xff);
        return (int) Math.round(Math.sqrt(dr * dr + dg * dg + db * db));
    }
}
