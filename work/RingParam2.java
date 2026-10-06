public class RingParam2 {
  public static void main(String[] a) {
    float dp = 3.0f, W = 1260, H = 2844;
    float petW = Math.max(110f, Math.min(180f, (W/dp)*0.44f)) * dp;
    float petH = petW * 279f/448f;
    float bodyR = (float)Math.hypot(petW/2, petH/2);
    float[] ang = {-5f, 42.5f, 90f, 137.5f, 185f};
    System.out.printf("petW=%.0f petH=%.0f bodyR=%.0f%n", petW, petH, bodyR);
    System.out.printf("%-6s %-6s %-9s %-9s %-8s %-8s %s%n",
        "ring", "btnR", "间距", "bodyGap", "最左", "最右", "判定");
    for (float ringDp : new float[]{120,130,140,150}) {
      for (float btnDp : new float[]{18,20,22}) {
        float ring = ringDp*dp, btnR = btnDp*dp;
        double minGap = 1e9;
        for (int i=0;i<5;i++) for (int j=i+1;j<5;j++) {
          double x=Math.toRadians(ang[i]), y=Math.toRadians(ang[j]);
          minGap = Math.min(minGap, Math.hypot(Math.cos(x)-Math.cos(y), Math.sin(x)-Math.sin(y))*ring - 2*btnR);
        }
        double bodyGap = ring - btnR - bodyR;
        // 横向占用（按钮被夹在屏幕内之前的位置）
        double leftMost = Math.cos(Math.toRadians(185))*ring - btnR;
        double rightMost = Math.cos(Math.toRadians(-5))*ring + btnR;
        boolean fits = Math.abs(leftMost) <= W/2 && Math.abs(rightMost) <= W/2;
        boolean ok = minGap >= 6*dp && bodyGap >= 4*dp && fits;
        System.out.printf("%-6.0f %-6.0f %-9.1f %-9.1f %-8.0f %-8.0f %s%n",
            ringDp, btnDp, minGap, bodyGap, leftMost, rightMost, ok?"OK":"-");
      }
    }
  }
}