import javax.imageio.ImageIO; import java.awt.*; import java.awt.image.BufferedImage; import java.io.File;
public class ShowIcon {
  public static void main(String[] a) throws Exception {
    BufferedImage sq = ImageIO.read(new File(a[0]));
    BufferedImage rd = ImageIO.read(new File(a[1]));
    int s = 192, k = 2, pad = 12;
    BufferedImage out = new BufferedImage((s*k)*2 + pad*3, s*k + pad*2 + 22, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    g.setColor(new Color(0xF0F0F0)); g.fillRect(0,0,out.getWidth(),out.getHeight());
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    g.drawImage(sq, pad, pad+20, s*k, s*k, null);
    g.drawImage(rd, pad*2 + s*k, pad+20, s*k, s*k, null);
    g.setColor(Color.DARK_GRAY); g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
    g.drawString("方形图标", pad, pad+14);
    g.drawString("圆形图标", pad*2 + s*k, pad+14);
    g.dispose();
    ImageIO.write(out, "png", new File(a[2]));
  }
}
