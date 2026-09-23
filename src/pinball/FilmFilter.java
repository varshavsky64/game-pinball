package pinball;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Эффект старой киноплёнки поверх кадра: зерно, виньетка, пылинки. Без мерцания яркости:
 * по умолчанию зерно неподвижно, в режиме «живой плёнки» оно медленно меняется и изредка
 * проскакивает тонкая царапина.
 */
final class FilmFilter {
    private static final int FRAMES = 4;
    /** Зерно рассчитывается в половинном разрешении — крупинки мягче и дешевле. */
    private static final int DOWNSCALE = 2;
    /** Смена кадров зерна в режиме «живой плёнки» — медленно, чтобы не рябило. */
    private static final int FPS = 6;

    private final int width;
    private final int height;
    private final BufferedImage[] frames = new BufferedImage[FRAMES];

    FilmFilter(int width, int height) {
        this.width = width;
        this.height = height;
        int w = width / DOWNSCALE, h = height / DOWNSCALE;
        Random rnd = new Random(1930);
        for (int f = 0; f < FRAMES; f++) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double dx = (x - w / 2.0) / (w / 2.0), dy = (y - h / 2.0) / (h / 2.0);
                    double d = Math.sqrt(dx * dx * 0.8 + dy * dy * 0.7);
                    double vignette = smooth(0.6, 1.25, d) * 130;
                    int argb = argb((int) vignette, 40, 22, 10);
                    double roll = rnd.nextDouble();
                    if (roll < 0.05) argb = blend(argb, argb(12 + rnd.nextInt(16), 255, 240, 210));
                    else if (roll < 0.11) argb = blend(argb, argb(12 + rnd.nextInt(18), 30, 18, 8));
                    img.setRGB(x, y, argb);
                }
            }
            Graphics2D g = img.createGraphics();
            Render.quality(g);
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(30, 18, 8, 60 + rnd.nextInt(60)));
                double x = rnd.nextDouble() * w, y = rnd.nextDouble() * h;
                if (rnd.nextBoolean()) {
                    g.fill(Render.circle(x, y, 0.6 + rnd.nextDouble() * 1.4));
                } else {
                    // Волосок на плёнке
                    Path2D hair = new Path2D.Double();
                    hair.moveTo(x, y);
                    hair.quadTo(x + rnd.nextDouble() * 12 - 6, y + rnd.nextDouble() * 12 - 6, x + rnd.nextDouble() * 16 - 8, y + rnd.nextDouble() * 16 - 8);
                    g.setStroke(new BasicStroke(0.6f));
                    g.draw(hair);
                }
            }
            g.dispose();
            frames[f] = img;
        }
    }

    void draw(Graphics2D g, double time) {
        if (!Toon.animatedFilm) {
            g.drawImage(frames[0], 0, 0, width, height, null);
            return;
        }
        int frame = (int) (time * FPS);
        g.drawImage(frames[frame % FRAMES], 0, 0, width, height, null);

        // Изредка — одна тонкая бледная царапина
        Random rnd = new Random(frame * 7919L);
        if (rnd.nextDouble() < 0.15) {
            for (int i = 0; i < 1; i++) {
                double x = rnd.nextDouble() * width;
                Path2D scratch = new Path2D.Double();
                scratch.moveTo(x, 0);
                for (int y = 0; y <= height; y += 60) scratch.lineTo(x + Math.sin(y * 0.01 + frame) * 3, y);
                g.setStroke(new BasicStroke(0.8f));
                g.setColor(new Color(255, 245, 220, 28));
                g.draw(scratch);
            }
        }
    }

    private static double smooth(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    private static int argb(int a, int r, int g, int b) {
        return (Math.max(0, Math.min(255, a)) << 24) | (r << 16) | (g << 8) | b;
    }

    /** Наложение src поверх dst (обе ARGB, не premultiplied). */
    private static int blend(int dst, int src) {
        double sa = (src >>> 24) / 255.0, da = (dst >>> 24) / 255.0;
        double oa = sa + da * (1 - sa);
        if (oa <= 0) return 0;
        int r = (int) ((((src >> 16) & 255) * sa + ((dst >> 16) & 255) * da * (1 - sa)) / oa);
        int gg = (int) ((((src >> 8) & 255) * sa + ((dst >> 8) & 255) * da * (1 - sa)) / oa);
        int b = (int) (((src & 255) * sa + (dst & 255) * da * (1 - sa)) / oa);
        return argb((int) (oa * 255), r, gg, b);
    }
}
