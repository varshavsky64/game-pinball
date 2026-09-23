package pinball;

import java.util.Random;
import java.util.Set;

/** Европейская рулетка: колесо вращается всегда, по запросу запускается шарик. */
final class Roulette {
    /** Порядок номеров на колесе. */
    static final int[] WHEEL = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
            5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26};
    static final double POCKET = 2 * Math.PI / WHEEL.length;

    private static final Set<Integer> RED = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    private static final double WHEEL_SPEED = 1.2;
    private static final double SPIN_TIME = 2.6;
    private static final double BALL_TURNS = 5;

    double wheelAngle;
    double ballAngle;
    /** Расстояние шарика от центра в долях радиуса колеса. */
    double ballRadius = 0.93;
    boolean ballVisible;
    int result = -1;

    private final Random rnd = new Random();
    private int pocketIndex;
    private double timer;
    private double endAngle;
    private boolean spinning;
    private boolean finished;
    private int lastTick;

    static boolean isRed(int n) {
        return RED.contains(n);
    }

    boolean isSpinning() {
        return spinning;
    }

    void reset() {
        spinning = false;
        finished = false;
        ballVisible = false;
        result = -1;
    }

    void spin() {
        pocketIndex = rnd.nextInt(WHEEL.length);
        result = WHEEL[pocketIndex];
        timer = 0;
        spinning = true;
        finished = false;
        ballVisible = true;
        endAngle = wheelAngle + WHEEL_SPEED * SPIN_TIME + pocketCenter(pocketIndex);
        lastTick = Integer.MIN_VALUE;
    }

    /** Возвращает число пройденных шариком лунок (для щелчков). */
    int update(double dt) {
        wheelAngle += WHEEL_SPEED * dt;
        if (!ballVisible) return 0;
        if (!spinning) {
            ballAngle = wheelAngle + pocketCenter(pocketIndex);
            return 0;
        }
        timer += dt;
        double x = Math.min(1, timer / SPIN_TIME);
        double remaining = Math.pow(1 - x, 3);
        // Шарик бежит против вращения колеса и замедляется
        ballAngle = endAngle + 2 * Math.PI * BALL_TURNS * remaining;
        ballRadius = 0.93 - 0.2 * x * x;

        int tick = (int) Math.floor((ballAngle - wheelAngle) / POCKET);
        int ticks = lastTick == Integer.MIN_VALUE ? 0 : Math.abs(tick - lastTick);
        lastTick = tick;
        if (x >= 1) {
            spinning = false;
            finished = true;
        }
        return ticks;
    }

    boolean consumeFinished() {
        boolean f = finished;
        finished = false;
        return f;
    }

    void hideBall() {
        ballVisible = false;
    }

    private static double pocketCenter(int index) {
        return index * POCKET + POCKET / 2;
    }
}
