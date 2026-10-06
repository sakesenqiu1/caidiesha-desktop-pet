import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 图标导出（零半透明、零自造色）：
 *   - 底色只用官方身体色 #54BAD1
 *   - 圆角/圆形用「硬阈值」判定：完全在内=不透明，完全在外=透明，不做抗锯齿渐变
 *   - 精灵部分最近邻搬运（1:1 / 整数倍放大），颜色仍是原图那一组
 *   - 放大用整数倍像素复制，绝不插值
 */
public class IconStrict {

    public static void main(String[] args) throws Exception {
        String spritePath = args[0];
        String res = args[1];
        BufferedImage master = ImageIO.read(new File(spritePath));
        final int BODY = 0xFF54BAD1;   // 官方身体色（原图中真实存在）

        // 自适应图标前景：432x432，中心放 2 倍整数放大的精灵，透明底
        BufferedImage fg = new BufferedImage(432, 432, BufferedImage.TYPE_INT_ARGB);
        BufferedImage up2 = integerScale(master, 2);
        int fw = 300, fh = (int) Math.round(up2.getHeight() * (fw / (double) up2.getWidth()));
        if (fh > 300) { fh = 300; fw = (int) Math.round(up2.getWidth() * (fh / (double) up2.getHeight())); }
        BufferedImage fit = nearest(up2, fw, fh);
        pasteCentered(fg, fit, BODY, false, true);
        write(fg, res + "/drawable-nodpi/ic_fg.png");

        // 传统图标 + 圆形图标
        for (int size : new int[]{144, 192, 288}) {
            String dir = size <= 144 ? "mipmap-xhdpi" : size <= 192 ? "mipmap-xxhdpi" : "mipmap-xxxhdpi";
            write(icon(master, size, false, BODY), res + "/" + dir + "/ic_launcher.png");
            write(icon(master, size, true, BODY), res + "/" + dir + "/ic_launcher_round.png");
        }

        // 主界面顶部展示图：正好 2 倍整数放大（像素复制，颜色不变，不模糊）
        write(integerScale(master, 2), res + "/drawable-nodpi/pet_caidiesha_card.png");
    }

    /** 整数倍像素复制放大 */
    static BufferedImage integerScale(BufferedImage s, int k) {
        BufferedImage out = new BufferedImage(s.getWidth() * k, s.getHeight() * k, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < s.getHeight(); y++)
            for (int x = 0; x < s.getWidth(); x++) {
                int c = s.getRGB(x, y);
                for (int j = 0; j < k; j++)
                    for (int i = 0; i < k; i++)
                        out.setRGB(x * k + i, y * k + j, c);
            }
        return out;
    }

    /** 最近邻缩放（只搬运像素） */
    static BufferedImage nearest(BufferedImage s, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out.setRGB(x, y, s.getRGB(x * s.getWidth() / w, y * s.getHeight() / h));
        return out;
    }

    /** 放置精灵；mask 为 true 时套一个硬边圆角底板 */
    static void pasteCentered(BufferedImage canvas, BufferedImage sprite, int body, boolean mask, boolean pad) {
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int x = (canvas.getWidth() - sprite.getWidth()) / 2;
        int y = (canvas.getHeight() - sprite.getHeight()) / 2;
        g.drawImage(sprite, x, y, null);
        g.dispose();
    }

    /** 硬阈值圆角/圆形底 */
    static BufferedImage icon(BufferedImage sprite, int size, boolean round, int body) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        // 精灵先按比例放进来（最近邻）
        int w = (int) (size * 0.80);
        int h = (int) Math.round(sprite.getHeight() * (w / (double) sprite.getWidth()));
        if (h > size * 0.80) { h = (int) (size * 0.80); w = (int) Math.round(sprite.getWidth() * (h / (double) sprite.getHeight())); }
        BufferedImage fit = nearest(sprite, w, h);

        double r = size * 0.22;             // 圆角半径
        double cx = size / 2.0, cy = size / 2.0, rad = size / 2.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean inside;
                if (round) {
                    double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                    inside = dx * dx + dy * dy < rad * rad;      // 硬阈值：没有抗锯齿半透明
                } else {
                    // 圆角矩形：硬判定
                    double px = x + 0.5, py = y + 0.5;
                    double qx = Math.max(r, Math.min(size - r, px));
                    double qy = Math.max(r, Math.min(size - r, py));
                    double d = Math.hypot(px - qx, py - qy);
                    inside = d <= r;
                }
                if (inside) out.setRGB(x, y, 0xFF000000 | body);
            }
        }
        // 贴精灵
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.drawImage(fit, (size - w) / 2, (size - h) / 2, null);
        g.dispose();
        return out;
    }

    static void write(BufferedImage img, String path) throws Exception {
        File f = new File(path);
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println("  " + path + "  " + img.getWidth() + "x" + img.getHeight() + "  " + f.length() + "B");
    }
}
