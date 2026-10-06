public class MenuSize {
  public static void main(String[] a) {
    float dp = 3.0f, W = 1260;
    float gap = 6*dp, apad = 8*dp, bh = 40*dp;
    int cols = 3, rows = 2;
    float w = Math.min(74*dp*cols + gap*(cols-1) + apad*2, W - 12*dp);
    float h = bh*rows + gap*(rows-1) + apad*2;
    System.out.printf("菜单面板 %.0fx%.0f px = %.0fx%.0f dp%n", w, h, w/dp, h/dp);
    System.out.printf("单个按钮 %.0fx%.0f px = %.0fx%.0f dp%n",
        (w - apad*2 - gap*(cols-1))/cols, bh, ((w - apad*2 - gap*(cols-1))/cols)/dp, bh/dp);
  }
}
