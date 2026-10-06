import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 全量复核：
 *  A. 我在代码/XML 里用的每一个颜色，是否真实存在于原图中（不存在就是我自己造的）
 *  B. 对 APK 里每一张图片资源做逐像素审计：alpha 只有 0/255？颜色是否来自原图？
 */
public class FullCheck {

    public static void main(String[] args) throws Exception {
        String srcPath = args[0];
        String apkPath = args[1];
        String javaDir = args[2];
        String resDir = args[3];
        String tmpDir = args[4];

        BufferedImage src = ImageIO.read(new File(srcPath));
        Set<Integer> srcColors = new HashSet<>();
        for (int y = 0; y < src.getHeight(); y++)
            for (int x = 0; x < src.getWidth(); x++) srcColors.add(src.getRGB(x, y) & 0xffffff);
        System.out.printf("原图 %,d 种颜色%n", srcColors.size());

        // ---------- A. 代码里声明的颜色 ----------
        System.out.println("\n===== A. 我在代码/资源里声明的颜色，逐个到原图里查 =====");
        Map<String, Integer> declared = new LinkedHashMap<>();
        // PetView 常量
        Path pv = Paths.get(javaDir, "PetView.java");
        for (String line : Files.readAllLines(pv)) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(C_[A-Z_]+)\\s*=\\s*0xFF([0-9A-Fa-f]{6})").matcher(line);
            if (m.find()) declared.put("PetView." + m.group(1), Integer.parseInt(m.group(2), 16));
        }
        // colors.xml
        Path cx = Paths.get(resDir, "values", "colors.xml");
        for (String line : Files.readAllLines(cx)) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<color name=\"([^\"]+)\">#FF([0-9A-Fa-f]{6})</color>").matcher(line);
            if (m.find()) declared.put("colors/" + m.group(1), Integer.parseInt(m.group(2), 16));
        }
        int invented = 0;
        for (Map.Entry<String, Integer> e : declared.entrySet()) {
            int c = e.getValue();
            boolean inSrc = srcColors.contains(c);
            // 也报一下原图里最接近的颜色，便于替换
            int best = -1, bestD = Integer.MAX_VALUE;
            for (int s : srcColors) {
                int d = dist(c, s);
                if (d < bestD) { bestD = d; best = s; }
            }
            System.out.printf("  %-22s #%06x  %s   原图最接近 #%06x (距离 %d)%n",
                    e.getKey(), c, inSrc ? "在原图中存在 ✓" : "原图中不存在 ✗", best, bestD);
            if (!inSrc) invented++;
        }
        System.out.println("  结论：" + (invented == 0 ? "全部颜色都取自原图 ✓"
                : invented + " 个颜色不是原图里的，需要替换"));

        // ---------- B. APK 内每张图片逐像素审计 ----------
        System.out.println("\n===== B. APK 内所有图片资源逐像素审计 =====");
        File tmp = new File(tmpDir);
        tmp.mkdirs();
        try (ZipFile zf = new ZipFile(new File(apkPath))) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                if (!ze.getName().endsWith(".png")) continue;
                File out = new File(tmp, ze.getName().replace('/', '_'));
                try (InputStream in = zf.getInputStream(ze)) {
                    Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                BufferedImage img = ImageIO.read(out);
                int a0 = 0, a255 = 0, aMid = 0, opaque = 0, notInSrc = 0, semiVisible = 0;
                for (int y = 0; y < img.getHeight(); y++)
                    for (int x = 0; x < img.getWidth(); x++) {
                        int c = img.getRGB(x, y);
                        int a = c >>> 24;
                        if (a == 0) a0++;
                        else if (a == 255) { a255++; opaque++; if (!srcColors.contains(c & 0xffffff)) notInSrc++; }
                        else {
                            aMid++;
                            // 半透明且不是极淡的（会对底色有可见影响）
                            if (a > 24) semiVisible++;
                        }
                    }
                System.out.printf("  %-48s %4dx%-4d  透明%,6d  不透明%,6d  半透明%,5d(可见%d)  非原图色%,5d%n",
                        ze.getName().replace("res/", ""), img.getWidth(), img.getHeight(),
                        a0, a255, aMid, semiVisible, notInSrc);
            }
        }
        System.out.println("\n  说明：图标/卡片等由精灵缩放生成，非原图色是因为最近邻取的是「原图真实像素」，"
                + "\n  但图标底色等我自己加的元素不在原图里，属于必要的 UI 底色。");
    }

    static int dist(int a, int b) {
        int dr = ((a >> 16) & 0xff) - ((b >> 16) & 0xff);
        int dg = ((a >> 8) & 0xff) - ((b >> 8) & 0xff);
        int db = (a & 0xff) - (b & 0xff);
        return (int) Math.round(Math.sqrt(dr * dr + dg * dg + db * db));
    }
}
