public class ArcFinal {
  public static void main(String[] a) {
    float dp=3.0f, W=1260, H=2844;
    float petW = Math.max(110f, Math.min(180f, (W/dp)*0.44f))*dp;
    float petH = petW*279f/448f;
    float bodyR = (float)Math.hypot(petW/2, petH/2);
    float btnR = 30*dp;
    float[] ang = {12.5f, 51.25f, 92f, 128.75f, 167.5f};
    float r = 2.5f*bodyR;
    double chord = 2*Math.sin(Math.toRadians(38.75/2));
    r = Math.max(r, (float)((2*btnR + 8*dp)/chord));
    // 正确的横向约束：最外侧按钮的外沿必须留在屏幕内
    float maxR = 0;
    for (float x : ang) maxR = Math.max(maxR, Math.abs((float)Math.cos(Math.toRadians(x))));
    float byWidth = (W/2f - btnR - 4*dp) / maxR;
    float byHeight = H - petH/2 - 130*dp - bodyR - btnR;
    float ring = Math.max(120*dp, Math.min(r, Math.min(byWidth, byHeight)));
    System.out.printf("petW=%.0f petH=%.0f bodyR=%.0f btnR=%.0f%n", petW, petH, bodyR, btnR);
    System.out.printf("需要的半径=%.0f  横向上限=%.0f  纵向上限=%.0f  ->  采用=%.0f px (%.0f dp)%n%n",
        r, byWidth, byHeight, ring, ring/dp);
    for (float x : ang) {
      double rad = Math.toRadians(x);
      float bx = (float)Math.cos(rad)*ring, by = (float)Math.sin(rad)*ring;
      System.out.printf("  %.1f°  按钮中心(相对精灵) %6.0f,%6.0f   外沿 x=%.0f (屏宽一半 %.0f)  %s%n",
          x, bx, by, Math.abs(bx)+btnR, W/2, (Math.abs(bx)+btnR <= W/2 ? "OK" : "出屏"));
    }
    System.out.printf("%n按钮与身体矩形的间隙 = %.1f px%n", ring - btnR - bodyR);
    System.out.printf("按钮之间最近距离 = %.1f px (需 %.1f)%n",
        2*Math.sin(Math.toRadians(38.75/2))*ring, 2*btnR);
  }
}