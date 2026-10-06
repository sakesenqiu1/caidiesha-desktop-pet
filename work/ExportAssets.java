import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** 从抠好的母图导出 APK 需要的各个尺寸 */
public class ExportAssets {
    public static void main(String[] args) throws Exception {
        String src = args[0];       // 透明母图
        String res = args[1];       // app/src/main/res
        BufferedImage master = ImageIO.read(new File(src));
        System.out.println("master = " + master.getWidth() + "x" + master.getHeight());
        write(scale(master, 480), args[2] + "/pet480.png");

        // 桌宠精灵（按屏幕密度取用，避免放大发虚）
        write(scale(master, 200), res + "/drawable-nodpi/pet_caidiesha.png");
        write(scale(master, 320), res + "/drawable-xxhdpi/pet_caidiesha.png");
        write(scale(master, 480), res + "/drawable-xxxhdpi/pet_caidiesha.png");

        // 自适应图标：前景要留安全边距，内容缩到约 60%
        BufferedImage fg = new BufferedImage(432, 432, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = fg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int w = (int) (432 * 0.72), h = (int) (master.getHeight() * (w / (double) master.getWidth()));
        g.drawImage(master, (432 - w) / 2, (432 - h) / 2, w, h, null);
        g.dispose();
        write(fg, res + "/drawable-nodpi/ic_fg.png");

        // 传统图标（API 26 以下 / 部分启动器）
        for (int size : new int[]{144, 192, 288}) {
            String dir = size <= 144 ? "mipmap-xhdpi" : size <= 192 ? "mipmap-xxhdpi" : "mipmap-xxxhdpi";
            write(legacyIcon(master, size), res + "/" + dir + "/ic_launcher.png");
            write(roundIcon(master, size), res + "/" + dir + "/ic_launcher_round.png");
        }
    }

    static BufferedImage scale(BufferedImage s, int tw) {
        int th = Math.max(1, (int) Math.round(s.getHeight() * (tw / (double) s.getWidth())));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(s, 0, 0, tw, th, null);
        g.dispose();
        return out;
    }

    static BufferedImage legacyIcon(BufferedImage s, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setPaint(new GradientPaint(0, 0, new Color(0x9DE9EF), 0, size, new Color(0x53B6DC)));
        g.fillRoundRect(0, 0, size, size, (int) (size * 0.22), (int) (size * 0.22));
        int w = (int) (size * 0.86), h = (int) (s.getHeight() * (w / (double) s.getWidth()));
        g.drawImage(s, (size - w) / 2, (size - h) / 2, w, h, null);
        g.dispose();
        return out;
    }

    static BufferedImage roundIcon(BufferedImage s, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setPaint(new GradientPaint(0, 0, new Color(0x9DE9EF), 0, size, new Color(0x53B6DC)));
        g.fillOval(0, 0, size, size);
        int w = (int) (size * 0.80), h = (int) (s.getHeight() * (w / (double) s.getWidth()));
        g.drawImage(s, (size - w) / 2, (size - h) / 2, w, h, null);
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
