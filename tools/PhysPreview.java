
import com.dsh.caidiesha.Physics;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

/**
 * 用 App 里那份真正的 {@link Physics} 类，在电脑上把桌宠的表现渲染成 GIF 预览。
 * 画面宽高取 390x844 —— 相当于手机逻辑分辨率（常见 1080p 机型 ≈ 392dp 宽）。
 */
public class PhysPreview {

    static final float DENSITY = 1.25f;   // 画布 390px 折合约 312dp 宽，故取 1.25
    static final float dp = DENSITY;
    static final float PET_W = 164f;   // = 屏宽 42%（和 PetView 在 390px 画布上的取值一致）

    // ---- 真·物理（和手机上跑的是同一个类） ----
    static Physics p;
    static float dragTilt, spin, spinV;
    static boolean dragging = false;
    static final Random rnd = new Random(42);

    static Physics.Bounds bounds;

    // ---- 粒子 ----
    static class P {
        float x, y, vx, vy, life, maxLife, r;
        int type; // 0 drop 1 bubble 2 heart
    }

    static final ArrayList<P> parts = new ArrayList<>();

    // ---- 气泡 ----

    // ---- 菜单 ----
    static boolean menuOpen;
    static boolean lockedNow = false;   // 由命令行第 3 个参数控制
    static Rectangle2D.Float panel = new Rectangle2D.Float();
    static Rectangle2D.Float[] btns = new Rectangle2D.Float[4];
    static final String[] LABELS = {"喂食", "摸摸", "资料", "躲起来"};

    static BufferedImage sprite;
    static final int W = 390, H = 844;

    public static void main(String[] args) throws Exception {
        sprite = ImageIO.read(new File(args[0]));
        if (args.length > 3) lockedNow = Boolean.parseBoolean(args[3]);
        String out = args[1];
        int fps = 16, seconds = args.length > 2 ? Integer.parseInt(args[2]) : 18;
        int total = fps * seconds;

        float sw = PET_W, sh = sprite.getHeight() * sw / sprite.getWidth();
        final float petH = sh;
        // 真·物理：和手机上跑的 Physics 完全同一个类、同一套边界
        bounds = new Physics.Bounds() {
            public float width() { return W; }
            public float height() { return H; }
            public float petW() { return PET_W; }
            public float petH() { return petH; }
            public float topLimit() { return 44f * dp; }   // 与 PetView.topLimit() 一致
        };
        p = new Physics(dp);
        p.seed(W * 0.5f, H * 0.42f);

        // 时间轴（秒）：3.2 点一下；6.0 双击转圈；8.4 长按开菜单；11 收起
        java.util.List<BufferedImage> frames = new ArrayList<>();
        float dt = 1f / fps;
        float t = 0;
        for (int f = 0; f < total; f++) {
            if (f == (int) (4.0f * fps)) tap(false);
            if (f == (int) (8.5f * fps)) tap(true);
            if (seconds > 8 && f == (int) (12.0f * fps)) openMenu();
            if (seconds > 8 && f == (int) (15.5f * fps)) menuOpen = false;
            step(dt, t, sw, sh);
            updateParts(dt);
            frames.add(render(t, sw, sh));
            t += dt;
        }

        int delayMs = 1000 / fps;
        writeGif(frames, delayMs, out);
        // 抽几帧存 PNG，方便查看静态效果
        String dir = new File(out).getParent();
        ImageIO.write(frames.get((int) (frames.size() * 0.34f)), "png", new File(dir, "shot_tap.png"));
        ImageIO.write(frames.get((int) (frames.size() * 0.72f)), "png", new File(dir, "shot_menu.png"));
        ImageIO.write(frames.get((int) (frames.size() * 0.12f)), "png", new File(dir, "shot_idle.png"));
        System.out.printf("frames=%d  x∈[%.0f,%.0f] y∈[%.0f,%.0f]%n",
                frames.size(), minX, maxX, minY, maxY);
    }

    static float minX = 1e9f, maxX = -1e9f, minY = 1e9f, maxY = -1e9f;

    // ==================== 物理：直接调用 App 里的 Physics ====================

    static void step(float dt, float t, float sw, float sh) {

        p.step(dt, bounds, dragging, 0f);
        minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
        minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);

        dragTilt += (0 - dragTilt) * Math.min(1f, dt * 6f);

        if (spinV != 0) {
            spinV *= (float) Math.pow(0.12f, dt);
            spin += spinV * dt;
            if (Math.abs(spinV) < 6f) spinV = 0;
        }

        // 预览不显示任何文字气泡，只演示动作与粒子
    }

    static void tap(boolean dbl) {
        if (dbl) {
            spinV = p.facingRight ? 720f : -720f;
            // 转圈：只有动作和粒子，没有文字
            burst(10, 0);
        } else {
            p.squashV = -2.6f;
            // 点击：只有 Q 弹、水花和啵声，没有文字
            burst(7, 0);
        }
    }

    static void openMenu() {
        menuOpen = true;
        layoutMenu();
        // 长按：直接出菜单，不弹文字
        burst(6, 1);
    }



    static void layoutMenu() {
        // 用真实 dp 尺寸，和手机上一致
        float bw = 74f * dp, bh = 40f * dp, gap = 6f * dp, pad = 8f * dp;
        float totalW = Math.min(bw * 4 + gap * 3 + pad * 2, W - 12f * dp);
        bw = (totalW - pad * 2 - gap * 3) / 4f;
        float totalH = bh + pad * 2;
        float sw = PET_W, sh = sprite.getHeight() * sw / sprite.getWidth();
        float left = Math.max(6f * dp, Math.min(W - totalW - 6f * dp, p.x - totalW / 2f));
        float top = p.y + sh / 2f + 16f * dp;
        if (top + totalH > H - 10f * dp) top = p.y - sh / 2f - totalH - 16f * dp;
        if (top < 8f * dp) top = 8f * dp;
        panel = new Rectangle2D.Float(left, top, totalW, totalH);
        for (int i = 0; i < 4; i++) {
            float l = left + pad + i * (bw + gap);
            btns[i] = new Rectangle2D.Float(l, top + pad, bw, bh);
        }
    }

    static void burst(int n, int type) {
        float sw = PET_W, sh = sprite.getHeight() * sw / sprite.getWidth();
        for (int i = 0; i < n; i++) {
            P q = new P();
            q.type = type;
            q.x = PhysPreview.p.x + (rnd.nextFloat() - 0.5f) * sw * 0.7f;
            q.y = PhysPreview.p.y + PhysPreview.p.bob + (rnd.nextFloat() - 0.5f) * sh * 0.5f;
            double a = rnd.nextDouble() * Math.PI * 2;
            float sp = (18f + rnd.nextFloat() * 46f) * dp;
            q.vx = (float) Math.cos(a) * sp;
            q.vy = (float) Math.sin(a) * sp - 22f * dp;
            q.maxLife = q.life = 0.7f + rnd.nextFloat() * 0.7f;
            q.r = (type == 2 ? 4.2f : type == 1 ? 3.4f : 2.8f) * dp * (0.7f + rnd.nextFloat() * 0.6f);
            parts.add(q);
        }
    }

    static void updateParts(float dt) {
        for (Iterator<P> it = parts.iterator(); it.hasNext(); ) {
            P p = it.next();
            p.life -= dt;
            if (p.life <= 0) {
                it.remove();
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.type == 2) {
                p.vy -= 26f * dp * dt;
                p.vx *= (float) Math.pow(0.35f, dt);
            } else if (p.type == 1) {
                p.vy -= 34f * dp * dt;
                p.vx *= (float) Math.pow(0.5f, dt);
            } else {
                p.vy += 42f * dp * dt;
            }
        }
    }

    // ==================== 渲染 ====================

    static BufferedImage render(float t, float sw, float sh) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // 模拟一张浅色壁纸 + 状态栏
        g.setPaint(new GradientPaint(0, 0, new Color(0xEAF6FB), 0, H, new Color(0xC9E4F2)));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(0xFFFFFF));
        g.fillRoundRect(24, 150, 200, 120, 28, 28);
        g.fillRoundRect(60, 330, 250, 90, 24, 24);
        g.fillRoundRect(30, 620, 180, 120, 28, 28);
        g.setColor(new Color(0x9FB6C4));
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.drawString("9:41", 26, 30);
        g.fillRoundRect(W - 60, 20, 34, 12, 6, 6);

        float cy = p.y + p.bob;
        float w = sw * (1f + p.squash * 0.10f) * (1f + (float) Math.sin(p.bobT * 0.9f) * 0.012f);
        float h = sh * (1f - p.squash * 0.10f) * (1f - (float) Math.sin(p.bobT * 0.9f) * 0.012f);

        // 影子
        float lift = p.bob / (9f * dp);
        float shw = w * (0.62f - lift * 0.05f), shh = h * (0.16f - lift * 0.015f);
        g.setColor(new Color(0x20, 0x40, 0x60, (int) Math.max(12, 58 - lift * 16)));
        g.fillOval((int) (p.x - shw / 2), (int) (p.y + h * 0.42f), (int) shw, (int) shh);

        // 粒子
        for (P p : parts) {
            int a = (int) (255 * Math.min(1f, p.life / (p.maxLife * 0.55f)));
            switch (p.type) {
                case 2:
                    g.setColor(new Color(255, 118, 149, a));
                    g.fillOval((int) (p.x - p.r * 1.1f), (int) (p.y - p.r * 0.6f), (int) (p.r * 1.2f), (int) (p.r * 1.2f));
                    g.fillOval((int) (p.x - p.r * 0.1f), (int) (p.y - p.r * 0.6f), (int) (p.r * 1.2f), (int) (p.r * 1.2f));
                    g.fillOval((int) (p.x - p.r * 0.8f), (int) (p.y - p.r * 0.1f), (int) (p.r * 1.7f), (int) (p.r * 1.7f));
                    break;
                case 1:
                    g.setColor(new Color(255, 255, 255, a));
                    g.setStroke(new BasicStroke(1.4f));
                    g.drawOval((int) (p.x - p.r), (int) (p.y - p.r), (int) (p.r * 2), (int) (p.r * 2));
                    break;
                default:
                    g.setColor(new Color(0x7F, 0xD8, 0xE8, a));
                    g.fillOval((int) (p.x - p.r * 0.7f), (int) (p.y - p.r), (int) (p.r * 1.4f), (int) (p.r * 2));
                    break;
            }
        }

        // 彩蝶鲨
        java.awt.geom.AffineTransform old = g.getTransform();
        g.translate(p.x, cy);
        g.rotate(Math.toRadians(spin));
        if (!p.facingRight) g.scale(-1, 1);
        g.rotate(Math.toRadians(dragTilt * 0.35f));
        g.drawImage(sprite, (int) (-w / 2), (int) (-h / 2), (int) w, (int) h, null);
        g.setTransform(old);

        // 锁定标记（与 App 完全一致：粉圆 + 居中的锁体 + 上方锁梁）
        if (lockedNow) {
            float lcx = p.x + 15f * dp, lcy = p.y - (sh / 2f) + 15f * dp;
            float lr = 8f * dp;
            g.setColor(new Color(0xF4, 0x95, 0x99, 230));
            g.fillOval((int)(lcx - lr), (int)(lcy - lr), (int)(lr*2), (int)(lr*2));
            float bw = 9f * dp, bh = 8f * dp;
            float ll = lcx - bw/2f, lt = lcy - bh/2f + 0.8f * dp;
            g.setColor(new Color(0x21, 0x4B, 0x57));
            g.fillRect((int)ll, (int)lt, (int)bw, (int)bh);
            g.setStroke(new BasicStroke(1.6f * dp));
            g.drawArc((int)(lcx - 3.2f*dp), (int)(lcy - bh/2f - 5.2f*dp),
                    (int)(6.4f*dp), (int)(6.4f*dp), 0, 180);
        }

        // 气泡

        // 菜单：精灵正下方的一簇带字小圆点（参数与 App 里完全一致）
        if (menuOpen) {
            float bodyR = (float) Math.hypot(sw / 2f, sh / 2f);
            float btnR = 30f * dp;
            float rr = 2.5f * bodyR;
            double chord = 2.0 * Math.sin(Math.toRadians(38.75 / 2.0));
            rr = Math.max(rr, (float) ((2 * btnR + 8f * dp) / chord));
            float byW = (W / 2f - btnR - 4f * dp) - bodyR;
            float byH = H - sh / 2f - 130f * dp - bodyR - btnR;
            float ring = Math.max(120f * dp, Math.min(rr, Math.min(byW, byH)));
            float[] angles = {12.5f, 51.25f, 90f, 128.75f, 167.5f};
            String[] labels = {"喂食", "锁定", "资料", "摸摸", "躲起来"};
            g.setFont(new Font("Microsoft YaHei", Font.PLAIN,
                    (int) Math.max(12f * dp, Math.min(26f * dp, btnR * 0.84f))));
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < angles.length; i++) {
                double a = Math.toRadians(angles[i]);
                float bx = p.x + (float) Math.cos(a) * ring;
                float by = p.y + (float) Math.sin(a) * ring;
                bx = Math.max(btnR + 4 * dp, Math.min(W - btnR - 4 * dp, bx));
                by = Math.max(btnR + 4 * dp, Math.min(H - btnR - 4 * dp, by));
                g.setColor(new Color(0xFF, 0xFF, 0xFF, 242));
                g.fillOval((int) (bx - btnR), (int) (by - btnR), (int) (btnR * 2), (int) (btnR * 2));
                g.setColor(new Color(0x3E, 0x9C, 0xB5));
                g.setStroke(new BasicStroke(2.2f * dp));
                g.drawOval((int) (bx - btnR), (int) (by - btnR), (int) (btnR * 2), (int) (btnR * 2));
                g.setColor(new Color(0x21, 0x4B, 0x57));
                int tw = fm.stringWidth(labels[i]);
                g.drawString(labels[i], bx - tw / 2f, by + (fm.getAscent() - fm.getDescent()) / 2f);
            }
        }

        g.dispose();
        return img;
    }


    static void writeGif(java.util.List<BufferedImage> frames, int delayMs, String path) throws Exception {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("gif");
        ImageWriter writer = it.next();
        ImageOutputStream ios = ImageIO.createImageOutputStream(new File(path));
        writer.setOutput(ios);
        writer.prepareWriteSequence(null);

        IIOMetadata metadata = writer.getDefaultImageMetadata(
                new javax.imageio.ImageTypeSpecifier(frames.get(0)), writer.getDefaultWriteParam());
        String fmt = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(fmt);
        IIOMetadataNode gce = getNode(root, "GraphicControlExtension");
        gce.setAttribute("disposalMethod", "none");
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE");
        gce.setAttribute("delayTime", String.valueOf(delayMs / 10));
        gce.setAttribute("transparentColorIndex", "0");
        IIOMetadataNode appExt = getNode(root, "ApplicationExtensions");
        IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
        app.setAttribute("applicationID", "NETSCAPE");
        app.setAttribute("authenticationCode", "2.0");
        app.setUserObject(new byte[]{1, 0, 0});
        appExt.appendChild(app);
        metadata.setFromTree(fmt, root);

        for (BufferedImage f : frames) {
            writer.writeToSequence(new IIOImage(f, null, metadata), null);
        }
        writer.endWriteSequence();
        ios.close();
    }

    static IIOMetadataNode getNode(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) return (IIOMetadataNode) root.item(i);
        }
        IIOMetadataNode n = new IIOMetadataNode(name);
        root.appendChild(n);
        return n;
    }
}
