package pinball;

import java.util.Random;

/** Три барабана слот-машины: вращение, поочерёдная остановка, результат. */
final class SlotMachine {
    static final int SEVEN = 0, BAR = 1, CHERRY = 2, BELL = 3, LEMON = 4, DIAMOND = 5;
    static final int SYMBOL_COUNT = 6;

    private static final int[] WEIGHTS = {1, 2, 4, 3, 4, 2};
    private static final double[] STOP_TIMES = {0.9, 1.35, 1.8};
    private static final double REEL_SPEED = 16; // символов в секунду

    /** Позиция ленты каждого барабана в символах (дробная часть — прокрутка). */
    final double[] reels = {SEVEN, SEVEN, SEVEN};
    final int[] result = new int[3];

    private final boolean[] stopped = new boolean[3];
    private final Random rnd = new Random();
    private double timer;
    private boolean spinning;
    private boolean finished;

    boolean isSpinning() {
        return spinning;
    }

    void reset() {
        spinning = false;
        finished = false;
        for (int r = 0; r < 3; r++) reels[r] = SEVEN;
    }

    void spin(boolean forceJackpot) {
        for (int r = 0; r < 3; r++) {
            result[r] = forceJackpot ? SEVEN : weightedSymbol();
            stopped[r] = false;
        }
        timer = 0;
        spinning = true;
        finished = false;
    }

    /** Возвращает, сколько барабанов остановилось за этот шаг (для звука). */
    int update(double dt) {
        if (!spinning) return 0;
        timer += dt;
        int stops = 0;
        for (int r = 0; r < 3; r++) {
            if (stopped[r]) continue;
            if (timer >= STOP_TIMES[r]) {
                stopped[r] = true;
                reels[r] = result[r];
                stops++;
            } else {
                reels[r] = (reels[r] + REEL_SPEED * dt) % SYMBOL_COUNT;
            }
        }
        if (timer >= STOP_TIMES[2] + 0.3) {
            spinning = false;
            finished = true;
        }
        return stops;
    }

    /** true один раз — сразу после полной остановки барабанов. */
    boolean consumeFinished() {
        boolean f = finished;
        finished = false;
        return f;
    }

    private int weightedSymbol() {
        int total = 0;
        for (int w : WEIGHTS) total += w;
        int roll = rnd.nextInt(total);
        for (int s = 0; s < WEIGHTS.length; s++) {
            roll -= WEIGHTS[s];
            if (roll < 0) return s;
        }
        return LEMON;
    }
}
