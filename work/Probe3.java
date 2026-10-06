import com.dsh.caidiesha.Physics;
public class Probe3 {
    public static void main(String[] a) {
        float dp = 2.75f; final int W = 390, H = 844;
        final float petW = 138f * dp, petH = petW * 126f / 200f;
        Physics.Bounds b = new Physics.Bounds() {
            public float width() { return W; } public float height() { return H; }
            public float petW() { return petW; } public float petH() { return petH; }
            public float topLimit() { return 26f * dp; }
        };
        Physics p = new Physics(dp);
        p.seed(W * 0.5f, H * 0.42f);
        System.out.println("petW=" + petW + " petH=" + petH + " 落点范围 x=[" + (petW/2+10*dp) + "," + (W-petW/2-10*dp) + "]");
        float dt = 1f/16f;
        for (int i = 0; i < 120; i++) {
            float oldTx = p.tx;
            p.step(dt, b, false, 0f);
            if (i % 8 == 0 || p.tx != oldTx)
                System.out.printf("f%-4d x=%7.2f y=%7.2f vx=%8.2f vy=%8.2f tx=%7.2f ty=%7.2f%s%n",
                        i, p.x, p.y, p.vx, p.vy, p.tx, p.ty, (p.tx != oldTx ? "   <-- 新落点" : ""));
        }
    }
}