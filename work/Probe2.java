import com.dsh.caidiesha.Physics;
import java.util.Random;
public class Probe2 {
    public static void main(String[] a) {
        float dp = 2.75f; final int w = 1080, h = 2340;
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
        Random rnd = new Random(20261006);
        for (int i = 0; i < 700; i++) {
            float dt;
            int r = rnd.nextInt(1000);
            if (r < 5) dt = 0.25f; else if (r < 60) dt = 0.05f; else if (r < 90) dt = 0.033f; else dt = 0.0167f;
            p.step(dt, b, false, 0f);
            double sp = Math.hypot(p.vx, p.vy);
            double bobAmp = 5.5*dp + Math.min(9*dp, sp*0.06);
            boolean bad = p.y + bobAmp - petH/2 < topLimit - 0.5;
            if (bad || (p.y < topLimit + 200)) {
                System.out.printf("f%-4d dt=%.4f x=%7.1f y=%8.2f bob=%7.2f amp=%6.2f top=%8.2f vy=%8.1f %s%n",
                        i, dt, p.x, p.y, p.bob, bobAmp, topLimit, p.vy, bad ? "<<< VIOLATION" : "");
            }
        }
    }
}