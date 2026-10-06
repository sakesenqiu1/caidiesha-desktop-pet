import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 从严格模式抠好的精灵导出各档位资源。
 * 只做纯像素搬运（最近邻），不做任何插值混色、不加渐变、不加描边。
 */
public class ExportStrict {

    public static void main(String[] args) throws Exception {
        String srcPath = args[0];   // strict/pet_full.png（448x279）
        String res = args[1];
        BufferedImage master = ImageIO.read(new File(srcPath));
        System.out.println("母图 " + master.getWidth() + "x" + master.getHeight());

        // 桌宠本体：同一张图放进 nodpi（画布 448x279 px），由 App 自己缩放
        write(master, res + "/drawable-nodpi/pet_caidiesha.png");

        // 自适应图标前景：官方 108dp 画布，内容居中且留安全边距
        // 底色用彩蝶鲨身体色 #9EDFD6（取自原图），不用我配的渐变
        BufferedImage fg = new BufferedImage(432, 432, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = fg.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int w = (int) (432 * 0.70), h = (int) (master.getHeight() * (w / (double) master.getWidth()));
        g.drawImage(master, (432 - w) / 2, (432 - h) / 2, w, h, null);
        g.dispose();
        write(fg, res + "/drawable-nodpi/ic_fg.png");

        // 传统图标：官方色底 + 精灵
        for (int size : new int[]{144, 192, 288}) {
            String dir = size <= 144 ? "mipmap-xhdpi" : size <= 192 ? "mipmap-xxhdpi" : "mipmap-xxxhdpi";
            write(icon(master, size, false), res + "/" + dir + "/ic_launcher.png");
            write(icon(master, size, true), res + "/" + dir + "/ic_launcher_round.png");
        }

        // 主界面顶部展示用小图（和桌宠同源，只做像素搬运）
        BufferedImage small = nearest(master, 320);
        write(small, res + "/drawable-nodpi/pet_caidiesha_card.png");
    }

    static BufferedImage nearest(BufferedImage s, int tw) {
        int th = Math.max(1, (int) Math.round(s.getHeight() * (tw / (double) s.getWidth())));
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < th; y++)
            for (int x = 0; x < tw; x++)
                out.setRGB(x, y, s.getRGB(x * s.getWidth() / tw, y * s.getHeight() / th));
        return out;
    }

    static BufferedImage icon(BufferedImage sprite, int size, boolean round) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(new Color(0x9EDFD6));           // 官方身体色
        if (round) g.fillOval(0, 0, size, size);
        else g.fillRoundRect(0, 0, size, size, (int) (size * 0.22), (int) (size * 0.22));
        int w = (int) (size * 0.84), h = (int) (sprite.getHeight() * (w / (double) sprite.getWidth()));
        g.drawImage(sprite, (size - w) / 2, (size - h) / 2, w, h, null);
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
