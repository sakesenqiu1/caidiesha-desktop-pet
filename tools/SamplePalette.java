import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

/**
 * 取色：从你给的原图里统计出彩蝶鲨真正使用的颜色，作为 App 界面配色的依据。
 * 不做主观挑选，只按出现频次列出，并给出几类主色。
 */
public class SamplePalette {
    public static void main(String[] args) throws Exception {
        BufferedImage s = ImageIO.read(new File(args[0]));
        String cutPath = args[1];
        BufferedImage cut = ImageIO.read(new File(cutPath));

        Map<Integer, Integer> hist = new LinkedHashMap<>();
        for (int y = 0; y < cut.getHeight(); y++) {
            for (int x = 0; x < cut.getWidth(); x++) {
                int c = cut.getRGB(x, y);
                if ((c >>> 24) == 0) continue;
                hist.merge(c & 0xffffff, 1, Integer::sum);
            }
        }
        List<Map.Entry<Integer, Integer>> list = new ArrayList<>(hist.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        int total = 0;
        for (int v : hist.values()) total += v;

        System.out.println("彩蝶鲨用到的颜色（按像素数排序，共 " + list.size() + " 种）:");
        for (int i = 0; i < Math.min(28, list.size()); i++) {
            int c = list.get(i).getKey();
            int n = list.get(i).getValue();
            System.out.printf("  #%06x  %6.2f%%  %,d px%n", c, 100.0 * n / total, n);
        }

        // 按色相聚类，找出体色 / 鳍色 / 嘴色
        long sumR = 0, sumG = 0, sumB = 0;
        Map<String, long[]> buckets = new LinkedHashMap<>();
        buckets.put("青色系", new long[3]);
        buckets.put("粉色系", new long[3]);
        buckets.put("紫色系", new long[3]);
        Map<String, Integer> bucketCount = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> e : list) {
            int c = e.getKey();
            float[] hsb = java.awt.Color.RGBtoHSB((c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff, null);
            float hue = hsb[0] * 360, sat = hsb[1], bri = hsb[2];
            if (sat < 0.12) continue;
            String k = null;
            if (hue >= 150 && hue < 210) k = "青色系";
            else if (hue >= 300 || hue < 20) k = "粉色系";
            else if (hue >= 230 && hue < 300) k = "紫色系";
            if (k == null) continue;
            long[] acc = buckets.get(k);
            acc[0] += ((c >> 16) & 0xff) * (long) e.getValue();
            acc[1] += ((c >> 8) & 0xff) * (long) e.getValue();
            acc[2] += (c & 0xff) * (long) e.getValue();
            bucketCount.merge(k, e.getValue(), Integer::sum);
        }
        System.out.println("\n主色（按色相归类，像素加权平均）:");
        for (Map.Entry<String, long[]> e : buckets.entrySet()) {
            int n = bucketCount.getOrDefault(e.getKey(), 0);
            if (n == 0) continue;
            long[] a = e.getValue();
            System.out.printf("  %s  #%02x%02x%02x  (%.1f%% 的像素)%n",
                    e.getKey(), a[0] / n, a[1] / n, a[2] / n, 100.0 * n / total);
        }
    }
}
