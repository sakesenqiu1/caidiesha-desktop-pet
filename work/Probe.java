import com.dsh.caidiesha.Physics;
public class Probe {
    public static void main(String[] a) {
        float dp = 2.75f; int w = 1080, h = 2340;
        final float petW = Math.max(112f, Math.min(178f, (w/dp)*0.42f)) * dp;
        final float petH = petW * 126f / 200f;
        final float topLimit = 26f*dp + petH/2f + 15f*dp;
        Physics.Bounds b = new Physics.Bounds() {
            public float width() { return w; } public float height() { return h; }
            public float petW() { return petW; } public float petH() { return petH; }
            public float topLimit() { return topLimit; }
        };
        Physics p = new Physics(dp);
        p.seed(w*0.5f, h*0.42f);
        System.out.println("petW=" + petW + " petH=" + petH + " topLimit=" + topLimit + " h=" + h);
        for (int i = 0; i < 700; i++) {
            p.step(1f/60f, b, false, 0f);
            if (i % 25 == 0 || (p.y < topLimit + 60))
                System.out.printf("f%-4d x=%7.1f y=%7.1f vx=%8.1f vy=%8.1f |v|=%.1f bob=%7.1f%n",
                        i, p.x, p.y, p.vx, p.vy, (float)Math.hypot(p.vx,p.vy), p.bob);
        }
    }
}