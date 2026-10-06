import javax.imageio.ImageIO; import java.awt.image.BufferedImage; import java.io.File;
public class Peek {
  public static void main(String[] a) throws Exception {
    BufferedImage s = ImageIO.read(new File(a[0]));
    int bg = s.getRGB(1,1) & 0xffffff;
    System.out.printf("背景 #%06x%n", bg);
    // 沿手臂中线采样
    int[][] pts = {{470,300},{480,320},{490,335},{500,345},{455,290},{440,280},{430,270}};
    for (int[] p : pts) {
      if (p[0] >= s.getWidth() || p[1] >= s.getHeight()) continue;
      int c = s.getRGB(p[0], p[1]) & 0xffffff;
      int dr=((c>>16)&0xff)-((bg>>16)&0xff), dg=((c>>8)&0xff)-((bg>>8)&0xff), db=(c&0xff)-(bg&0xff);
      double d = Math.sqrt(dr*dr+dg*dg+db*db);
      System.out.printf("  (%d,%d) #%06x  与背景距离 %.1f%n", p[0], p[1], c, d);
    }
  }
}
