public class RingParam {
  public static void main(String[] a) {
    float dp = 3.0f, W = 1260, H = 2844;
    float petW = Math.max(110f, Math.min(180f, (W/dp)*0.44f)) * dp;
    float petH = petW * 279f/448f;
    float bodyR = (float)Math.hypot(petW/2, petH/2);
    System.out.printf("petW=%.0f petH=%.0f bodyR=%.0f%n", petW, petH, bodyR);

    float[] angles = {36f, 64f, 92f, 120f, 270f};   // 4 个成弧 + 1 个在上方
    System.out.printf("%-6s %-6s %-8s %-10s %-10s%n", "ring", "btnR", "btnGap", "bodyGap", "verdict");
    for (float ringDp : new float[]{120, 126, 132, 138, 144}) {
      for (float btnDp : new float[]{20, 22, 24}) {
        float ring = ringDp*dp, btnR = btnDp*dp;
        double minGap = 1e9;
        for (int i=0;i<4;i++) for (int j=i+1;j<4;j++) {
          double x = Math.toRadians(angles[i]), y = Math.toRadians(angles[j]);
          double d = Math.hypot(Math.cos(x)-Math.cos(y), Math.sin(x)-Math.sin(y))*ring;
          minGap = Math.min(minGap, d - 2*btnR);
        }
        double bodyGap = ring - btnR - bodyR;
        boolean ok = minGap >= 2*dp && bodyGap >= 2*dp;
        System.out.printf("%-6.0f %-6.0f %-8.1f %-10.1f %s%n", ringDp, btnDp, minGap, bodyGap, ok ? "OK" : "-");
      }
    }
  }
}