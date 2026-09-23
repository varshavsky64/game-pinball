package pinball;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Звуки синтезируются при запуске (без файлов) и смешиваются на отдельном потоке. */
final class SoundEngine {
    enum Sfx {
        FLIPPER_UP, FLIPPER_DOWN, BUMPER, SLING, WALL, ROLLOVER, LANES,
        LAUNCH, DRAIN, SAVE, START, GAME_OVER, RANK,
        SPINNER, RAMP, RAMP_EXIT, CARD, DICE, SAUCER, EJECT, REEL_STOP, ROULETTE_TICK,
        SLOT_WIN, JACKPOT, MULTIBALL, EXTRA_BALL, KICKBACK, TILT, NUDGE, SKILL,
        BELL, QUACK, MAGIC, WHOOSH
    }

    private enum Wave { SINE, SQUARE, TRIANGLE }

    private static final class Voice {
        final float[] data;
        final float volume;
        int pos;

        Voice(float[] data, float volume) {
            this.data = data;
            this.volume = volume;
        }
    }

    private static final int RATE = 44100;
    private static final int CHUNK = 256;
    private static final long MIN_GAP_NANOS = 35_000_000L;

    private final EnumMap<Sfx, float[]> samples = new EnumMap<>(Sfx.class);
    private final EnumMap<Sfx, Long> lastPlayed = new EnumMap<>(Sfx.class);
    private final ConcurrentLinkedQueue<Voice> pending = new ConcurrentLinkedQueue<>();
    private final SourceDataLine line;
    private volatile boolean enabled = true;
    /** Фоновый треск старой пластинки, играет по кругу. */
    private float[] ambient = new float[0];

    SoundEngine() {
        this(true);
    }

    static SoundEngine silent() {
        return new SoundEngine(false);
    }

    private SoundEngine(boolean useAudio) {
        line = useAudio ? openLine() : null;
        if (line == null) return;
        buildSamples();
        Thread mixer = new Thread(this::mixLoop, "pinball-audio");
        mixer.setDaemon(true);
        mixer.start();
    }

    private static SourceDataLine openLine() {
        try {
            AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
            SourceDataLine l = AudioSystem.getSourceDataLine(format);
            l.open(format, CHUNK * 2 * 8);
            l.start();
            return l;
        } catch (Exception e) {
            return null;
        }
    }

    boolean isAvailable() {
        return line != null;
    }

    boolean isEnabled() {
        return enabled;
    }

    void toggle() {
        enabled = !enabled;
    }

    /** Вызывается из потока Swing; одинаковые звуки чаще MIN_GAP_NANOS отбрасываются. */
    void play(Sfx sfx, double volume) {
        if (line == null || !enabled) return;
        long now = System.nanoTime();
        Long last = lastPlayed.get(sfx);
        if (last != null && now - last < MIN_GAP_NANOS) return;
        lastPlayed.put(sfx, now);
        pending.add(new Voice(samples.get(sfx), (float) volume));
    }

    private void mixLoop() {
        List<Voice> active = new ArrayList<>();
        float[] mix = new float[CHUNK];
        byte[] out = new byte[CHUNK * 2];
        int ambientPos = 0;
        while (true) {
            for (Voice v; (v = pending.poll()) != null; ) active.add(v);
            Arrays.fill(mix, 0);
            if (enabled && ambient.length > 0) {
                for (int i = 0; i < CHUNK; i++) mix[i] += ambient[(ambientPos + i) % ambient.length];
                ambientPos = (ambientPos + CHUNK) % ambient.length;
            }
            for (Iterator<Voice> it = active.iterator(); it.hasNext(); ) {
                Voice v = it.next();
                int n = Math.min(CHUNK, v.data.length - v.pos);
                for (int i = 0; i < n; i++) mix[i] += v.data[v.pos + i] * v.volume;
                v.pos += n;
                if (v.pos >= v.data.length) it.remove();
            }
            for (int i = 0; i < CHUNK; i++) {
                int s = (int) (Math.tanh(mix[i] * 0.8) * 32000); // мягкое ограничение при наложении
                out[2 * i] = (byte) s;
                out[2 * i + 1] = (byte) (s >> 8);
            }
            line.write(out, 0, out.length);
        }
    }

    // ---------------------------------------------------------------- синтез

    private void buildSamples() {
        samples.put(Sfx.FLIPPER_UP, normalize(mix(
                noise(0.06, 70, 0.5, 1), 0.8,
                tone(160, 60, 0.07, Wave.SINE, 40), 0.9), 0.8));
        samples.put(Sfx.FLIPPER_DOWN, normalize(mix(
                noise(0.04, 90, 0.6, 2), 0.5,
                tone(110, 70, 0.05, Wave.SINE, 50), 0.6), 0.4));
        samples.put(Sfx.BUMPER, normalize(mix(
                tone(620, 260, 0.22, Wave.SQUARE, 16), 0.5,
                tone(1240, 520, 0.18, Wave.SINE, 20), 0.5), 0.7));
        samples.put(Sfx.SLING, normalize(mix(
                noise(0.05, 60, 0.3, 3), 0.6,
                tone(380, 180, 0.09, Wave.SQUARE, 35), 0.4), 0.7));
        samples.put(Sfx.WALL, normalize(mix(
                noise(0.03, 120, 0.7, 4), 0.5,
                tone(240, 180, 0.03, Wave.SINE, 80), 0.5), 0.5));
        samples.put(Sfx.ROLLOVER, normalize(notes(0.05, 25, Wave.TRIANGLE, 880, 1175, 1568), 0.5));
        samples.put(Sfx.LANES, normalize(notes(0.08, 10, Wave.SQUARE, 523, 659, 784, 1047), 0.45));
        samples.put(Sfx.LAUNCH, normalize(mix(
                noise(0.45, 7, 0.85, 5), 1.0,
                tone(120, 520, 0.35, Wave.TRIANGLE, 6), 0.4), 0.7));
        samples.put(Sfx.DRAIN, normalize(tone(700, 70, 1.0, Wave.SQUARE, 2.5), 0.4));
        samples.put(Sfx.SAVE, normalize(notes(0.07, 12, Wave.TRIANGLE, 784, 988, 1175, 1568), 0.55));
        // «Shave and a haircut» — народный водевильный мотив
        samples.put(Sfx.START, normalize(melody(Wave.SQUARE,
                new double[]{523, 392, 392, 440, 392, 0, 494, 523},
                new double[]{0.18, 0.09, 0.09, 0.18, 0.18, 0.18, 0.18, 0.34}), 0.5));
        samples.put(Sfx.GAME_OVER, normalize(trombone(466, 440, 415, 392), 0.65));
        samples.put(Sfx.RANK, normalize(notes(0.07, 10, Wave.SQUARE, 523, 659, 784, 1047, 784, 1047), 0.45));

        samples.put(Sfx.SPINNER, normalize(mix(
                tone(1800, 1400, 0.018, Wave.SQUARE, 120), 0.6,
                noise(0.012, 200, 0.2, 6), 0.4), 0.35));
        samples.put(Sfx.RAMP, normalize(mix(
                tone(200, 900, 0.4, Wave.TRIANGLE, 3), 0.5,
                noise(0.4, 5, 0.8, 7), 0.8), 0.6));
        samples.put(Sfx.RAMP_EXIT, normalize(mix(
                noise(0.05, 70, 0.5, 8), 0.7,
                tone(180, 110, 0.07, Wave.SINE, 40), 0.8), 0.7));
        samples.put(Sfx.CARD, normalize(mix(
                noise(0.06, 60, 0.55, 9), 0.7,
                tone(150, 80, 0.1, Wave.SINE, 30), 0.9), 0.8));
        float[] rattle = noise(0.025, 150, 0.3, 10);
        rattle = overlay(rattle, noise(0.025, 150, 0.3, 11), 0.045, 0.8);
        rattle = overlay(rattle, noise(0.025, 150, 0.3, 12), 0.08, 0.6);
        samples.put(Sfx.DICE, normalize(rattle, 0.6));
        samples.put(Sfx.SAUCER, normalize(mix(
                noise(0.05, 60, 0.6, 13), 0.6,
                tone(110, 60, 0.15, Wave.SINE, 18), 1.0), 0.8));
        samples.put(Sfx.EJECT, normalize(mix(
                noise(0.06, 50, 0.4, 14), 0.8,
                tone(240, 120, 0.08, Wave.SQUARE, 35), 0.5), 0.8));
        samples.put(Sfx.REEL_STOP, normalize(mix(
                tone(320, 200, 0.06, Wave.SQUARE, 50), 0.5,
                noise(0.03, 100, 0.4, 15), 0.7), 0.6));
        samples.put(Sfx.ROULETTE_TICK, normalize(tone(3200, 2800, 0.008, Wave.SINE, 300), 0.25));
        samples.put(Sfx.SLOT_WIN, normalize(coins(0.7, 12, 16), 0.6));
        samples.put(Sfx.JACKPOT, normalize(overlay(
                notes(0.09, 6, Wave.SQUARE, 523, 659, 784, 1047, 784, 1047, 1319),
                coins(1.4, 30, 17), 0.1, 0.8), 0.8));
        samples.put(Sfx.MULTIBALL, normalize(siren(1.3), 0.55));
        samples.put(Sfx.EXTRA_BALL, normalize(notes(0.09, 8, Wave.TRIANGLE, 659, 784, 988, 1319), 0.6));
        samples.put(Sfx.KICKBACK, normalize(mix(
                noise(0.08, 35, 0.4, 18), 0.9,
                tone(130, 50, 0.16, Wave.SINE, 15), 1.0), 0.9));
        samples.put(Sfx.TILT, normalize(tone(110, 105, 0.7, Wave.SQUARE, 1.5), 0.5));
        samples.put(Sfx.NUDGE, normalize(mix(
                noise(0.08, 40, 0.8, 19), 0.7,
                tone(80, 50, 0.12, Wave.SINE, 20), 1.0), 0.8));
        samples.put(Sfx.SKILL, normalize(notes(0.06, 12, Wave.SQUARE, 1047, 1319, 1568, 2093), 0.45));
        // Колокольчик: основной тон и негармонический обертон, долгое затухание
        samples.put(Sfx.BELL, normalize(mix(
                tone(1320, 1316, 0.9, Wave.SINE, 5), 0.7,
                tone(3190, 3180, 0.5, Wave.SINE, 9), 0.35), 0.5));
        // Кряканье: «квадрат» с быстрым падением высоты
        samples.put(Sfx.QUACK, normalize(mix(
                vibrato(420, 0.16, Wave.SQUARE, 30, 0.08, 12), 0.8,
                tone(700, 300, 0.12, Wave.SQUARE, 20), 0.3), 0.55));
        samples.put(Sfx.MAGIC, normalize(notes(0.05, 14, Wave.TRIANGLE, 1047, 1319, 1568, 2093, 2637), 0.45));
        samples.put(Sfx.WHOOSH, normalize(mix(
                noise(0.35, 6, 0.9, 20), 1.0,
                tone(300, 700, 0.3, Wave.SINE, 6), 0.2), 0.5));
        ambient = crackle(7, 78);
    }

    /** Мелодия с паузами (частота 0 — пауза) и лёгким вибрато, как у медных духовых. */
    private static float[] melody(Wave wave, double[] freqs, double[] durations) {
        float[] out = new float[0];
        double at = 0;
        for (int i = 0; i < freqs.length; i++) {
            if (freqs[i] > 0) out = overlay(out, vibrato(freqs[i], durations[i] * 0.95, wave, 5, 0.01, 6), at, 1);
            at += durations[i];
        }
        return out;
    }

    /** Грустный тромбон «ва-ва-ва-ваа»: ноты вниз, последняя долгая, с нарастающим вибрато. */
    private static float[] trombone(double... freqs) {
        float[] out = new float[0];
        double at = 0;
        for (int i = 0; i < freqs.length; i++) {
            boolean last = i == freqs.length - 1;
            double d = last ? 1.1 : 0.38;
            out = overlay(out, vibrato(freqs[i] / 2, d, Wave.SQUARE, last ? 6 : 4, last ? 0.03 : 0.012, 2.2), at, 1);
            at += d * 0.92;
        }
        return out;
    }

    private static float[] vibrato(double f, double duration, Wave wave, double rate, double depth, double decay) {
        int n = (int) (duration * RATE);
        float[] out = new float[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double freq = f * (1 + depth * Math.sin(2 * Math.PI * rate * t) * Math.min(1, t * 3));
            phase += 2 * Math.PI * freq / RATE;
            double attack = Math.min(1, t / 0.03);
            double release = Math.min(1, (duration - t) / 0.05);
            // Смесь «квадрата» и синуса — мягче, похоже на сурдину
            double s = 0.6 * osc(wave, phase) + 0.4 * Math.sin(phase);
            out[i] = (float) (s * attack * release * Math.exp(-decay * t * 0.3));
        }
        return out;
    }

    /** Треск винила: редкие щелчки разной силы и еле слышное шипение. */
    private static float[] crackle(double duration, long seed) {
        Random random = new Random(seed);
        int n = (int) (duration * RATE);
        float[] out = new float[n];
        double hiss = 0;
        for (int i = 0; i < n; i++) {
            hiss += (random.nextDouble() * 2 - 1 - hiss) * 0.08;
            out[i] = (float) (hiss * 0.012);
        }
        for (int k = 0; k < duration * 18; k++) {
            int at = random.nextInt(n - 200);
            double amp = 0.02 + Math.pow(random.nextDouble(), 4) * 0.12;
            for (int j = 0; j < 120; j++) out[at + j] += (float) ((random.nextDouble() * 2 - 1) * amp * Math.exp(-j / 18.0));
        }
        return out;
    }

    /** Звон падающих монет: короткие высокие «пинги» в случайные моменты. */
    private static float[] coins(double duration, int count, long seed) {
        Random random = new Random(seed);
        float[] out = new float[(int) (duration * RATE)];
        for (int i = 0; i < count; i++) {
            double f = 2400 + random.nextDouble() * 1800;
            out = overlay(out, tone(f, f * 0.98, 0.12, Wave.SINE, 30), random.nextDouble() * duration * 0.8,
                    0.5 + random.nextDouble() * 0.5);
        }
        return out;
    }

    /** Сирена: частота качается между 600 и 1200 Гц. */
    private static float[] siren(double duration) {
        int n = (int) (duration * RATE);
        float[] out = new float[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double f = 900 + 300 * Math.sin(2 * Math.PI * 4 * t);
            phase += 2 * Math.PI * f / RATE;
            out[i] = (float) (osc(Wave.TRIANGLE, phase) * envelope(t, duration, 0.8));
        }
        return out;
    }

    /** Накладывает add на base со сдвигом atSeconds (base при необходимости удлиняется). */
    private static float[] overlay(float[] base, float[] add, double atSeconds, double gain) {
        int offset = (int) (atSeconds * RATE);
        float[] out = Arrays.copyOf(base, Math.max(base.length, offset + add.length));
        for (int i = 0; i < add.length; i++) out[offset + i] += (float) (add[i] * gain);
        return out;
    }

    private static double osc(Wave wave, double phase) {
        double s = Math.sin(phase);
        return switch (wave) {
            case SINE -> s;
            case SQUARE -> Math.tanh(4 * s);
            case TRIANGLE -> 2 / Math.PI * Math.asin(s);
        };
    }

    private static double envelope(double t, double duration, double decay) {
        double attack = Math.min(1, t / 0.004);
        double release = Math.min(1, (duration - t) / 0.01);
        return attack * release * Math.exp(-decay * t);
    }

    /** Тон с экспоненциальным сдвигом частоты f0 → f1 и затуханием. */
    private static float[] tone(double f0, double f1, double duration, Wave wave, double decay) {
        int n = (int) (duration * RATE);
        float[] out = new float[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double f = f0 * Math.pow(f1 / f0, t / duration);
            phase += 2 * Math.PI * f / RATE;
            out[i] = (float) (osc(wave, phase) * envelope(t, duration, decay));
        }
        return out;
    }

    /** Шум, сглаженный однополюсным фильтром (smoothing ближе к 1 — глуше). */
    private static float[] noise(double duration, double decay, double smoothing, long seed) {
        Random random = new Random(seed);
        int n = (int) (duration * RATE);
        float[] out = new float[n];
        double y = 0;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            y += (random.nextDouble() * 2 - 1 - y) * (1 - smoothing);
            out[i] = (float) (y * envelope(t, duration, decay));
        }
        return out;
    }

    /** Последовательность нот; последняя звучит дольше. */
    private static float[] notes(double noteDuration, double decay, Wave wave, double... freqs) {
        List<float[]> parts = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < freqs.length; i++) {
            double d = i == freqs.length - 1 ? noteDuration * 3 : noteDuration;
            float[] p = tone(freqs[i], freqs[i], d, wave, i == freqs.length - 1 ? decay / 3 : decay);
            parts.add(p);
            total += p.length;
        }
        float[] out = new float[total];
        int pos = 0;
        for (float[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    private static float[] mix(float[] a, double gainA, float[] b, double gainB) {
        float[] out = new float[Math.max(a.length, b.length)];
        for (int i = 0; i < a.length; i++) out[i] += (float) (a[i] * gainA);
        for (int i = 0; i < b.length; i++) out[i] += (float) (b[i] * gainB);
        return out;
    }

    private static float[] normalize(float[] data, double peak) {
        float max = 0;
        for (float v : data) max = Math.max(max, Math.abs(v));
        if (max == 0) return data;
        float k = (float) (peak / max);
        for (int i = 0; i < data.length; i++) data[i] *= k;
        return data;
    }
}
