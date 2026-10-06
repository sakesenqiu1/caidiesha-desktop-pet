public class ArcPick {
  public static void main(String[] a) {
    float dp=3.0f, W=1260, H=2844;
    float petW = Math.max(110f, Math.min(180f, (W/dp)*0.44f))*dp;
    float petH = petW*279f/448f;
    float bodyR = (float)Math.hypot(petW/2, petH/2);
    System.out.printf("bodyR=%.0f  屏宽限制的半径上限=%.0f px%n%n", bodyR, (W/2 - 4*dp) - bodyR - 0);
    System.out.printf("%-7s %-7s %-8s %-9s %-9s %-8s %s%n","按钮dp","半径dp","跨度°","间距px","bodyGap","横向占用","判定");
    for (float btnDp : new float[]{21,22,23,24}) {
      for (float span : new float[]{120,130,140,150}) {
        float btnR = btnDp*dp;
        double per = span/4.0;
        double chord = 2*Math.sin(Math.toRadians(per/2));
        double needR = (2*btnR + 8*dp)/chord;
        float byW = (W/2f - btnR - 4*dp) - bodyR;
        float ring = (float)Math.max(90*dp, Math.min(Math.max(needR, bodyR+btnR+6*dp), byW));
        double gap = chord*ring - 2*btnR;
        double bg = ring - btnR - bodyR;
        double halfW = Math.cos(Math.toRadians(90-span/2))*0 + Math.abs(Math.sin(Math.toRadians(span/2)))*ring;
        double extent = Math.abs(Math.cos(Math.toRadians(90-span/2)))*ring + btnR;
        boolean ok = gap >= 4*dp && bg >= 0 && extent <= W/2;
        System.out.printf("%-7.0f %-7.0f %-8.0f %-9.1f %-9.1f %-8.0f %s%n",
            btnDp, ring/dp, span, gap, bg, extent, ok?"OK":"-");
      }
    }
  }
}