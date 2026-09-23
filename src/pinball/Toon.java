package pinball;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Стиль мультфильмов 1930-х: чернильный контур, «дрожащие» линии, плоская заливка
 * с тенью-полумесяцем, приглушённая «акварельная» палитра и винтажные шрифты.
 */
final class Toon {
    static final Color INK = new Color(34, 24, 20);
    static final Color PAPER = new Color(243, 228, 196);
    static final Color PAPER_DARK = new Color(206, 172, 124);
    static final Color CREAM = new Color(253, 246, 226);
    static final Color RED = new Color(204, 58, 46);
    static final Color DARK_RED = new Color(122, 30, 30);
    static final Color MUSTARD = new Color(234, 176, 52);
    static final Color TEAL = new Color(72, 150, 146);
    static final Color BLUE = new Color(66, 102, 152);
    static final Color GREEN = new Color(92, 150, 70);
    static final Color PURPLE = new Color(126, 82, 140);
    static final Color BROWN = new Color(122, 74, 42);
    static final Color WOOD = new Color(96, 44, 30);

    /** Сколько вариантов «дрожания» линий. */
    static final int BOIL_FRAMES = 3;
    /**
     * «Живая плёнка»: дрожание линий и движущееся зерно. По умолчанию выключена —
     * мелькание по всему экрану утомляет глаза. Переключается клавишей F.
     */
    static volatile boolean animatedFilm = false;

    private static final Set<String> FAMILIES = new HashSet<>(Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
    private static final String TITLE_FAMILY = pick("Rockwell", "Georgia", Font.SERIF);
    private static final String LABEL_FAMILY = pick("Copperplate", "Rockwell", Font.SERIF);
    private static final String CARD_FAMILY = pick("Georgia", "Baskerville", Font.SERIF);

    private Toon() {}

    static int boil(double time) {
        return animatedFilm ? (int) (time * 4) % BOIL_FRAMES : 0;
    }

    private static String pick(String... names) {
        for (String n : names) if (FAMILIES.contains(n) || n.equals(Font.SERIF)) return n;
        return Font.SERIF;
    }

    /** Плакатный шрифт заголовков. */
    static Font title(float size) {
        return new Font(TITLE_FAMILY, Font.BOLD, 1).deriveFont(size);
    }

    /** Шрифт подписей (капитель). */
    static Font label(float size) {
        return new Font(LABEL_FAMILY, Font.BOLD, 1).deriveFont(size);
    }

    /** Шрифт титров немого кино. */
    static Font card(float size) {
        return new Font(CARD_FAMILY, Font.BOLD | Font.ITALIC, 1).deriveFont(size);
    }

    // ================================================================ линии

    /** Слегка «дрожащий» контур, как у рисованной от руки линии; seed задаёт вариант дрожания. */
    static Shape wobble(Shape shape, double amp, int seed) {
        Path2D out = new Path2D.Double();
        double[] c = new double[6];
        double sx = 0, sy = 0, px = 0, py = 0;
        for (PathIterator it = shape.getPathIterator(null, 0.4); !it.isDone(); it.next()) {
            switch (it.currentSegment(c)) {
                case PathIterator.SEG_MOVETO -> {
                    sx = px = c[0];
                    sy = py = c[1];
                    out.moveTo(px + dx(px, py, amp, seed), py + dy(px, py, amp, seed));
                }
                case PathIterator.SEG_LINETO -> {
                    lineTo(out, px, py, c[0], c[1], amp, seed);
                    px = c[0];
                    py = c[1];
                }
                case PathIterator.SEG_CLOSE -> {
                    lineTo(out, px, py, sx, sy, amp, seed);
                    out.closePath();
                    px = sx;
                    py = sy;
                }
                default -> {
                }
            }
        }
        return out;
    }

    private static void lineTo(Path2D out, double x0, double y0, double x1, double y1, double amp, int seed) {
        int n = Math.max(1, (int) Math.ceil(Math.hypot(x1 - x0, y1 - y0) / 6));
        for (int k = 1; k <= n; k++) {
            double x = x0 + (x1 - x0) * k / n, y = y0 + (y1 - y0) * k / n;
            out.lineTo(x + dx(x, y, amp, seed), y + dy(x, y, amp, seed));
        }
    }

    private static double dx(double x, double y, double amp, int seed) {
        return amp * (0.6 * Math.sin(x * 0.09 + y * 0.05 + seed * 1.7) + 0.4 * Math.sin(y * 0.21 - x * 0.07 + seed * 2.9));
    }

    private static double dy(double x, double y, double amp, int seed) {
        return amp * (0.6 * Math.sin(y * 0.08 - x * 0.06 + seed * 2.3) + 0.4 * Math.sin(x * 0.19 + y * 0.04 + seed * 1.1));
    }

    /** Чернильная обводка. */
    static void ink(Graphics2D g, Shape shape, float width, int seed) {
        g.setStroke(Render.round(width));
        g.setColor(INK);
        g.draw(wobble(shape, 0.5 + width * 0.25, seed));
    }

    /** Мультяшная заливка: плоский цвет, тень-полумесяц снизу справа и чернильный контур. */
    static void toon(Graphics2D g, Shape shape, Color base, float inkWidth, int seed) {
        g.setColor(base);
        g.fill(shape);
        Rectangle2D b = shape.getBounds2D();
        double off = Math.max(1.5, Math.min(b.getWidth(), b.getHeight()) * 0.12);
        Area shade = new Area(shape);
        shade.subtract(new Area(AffineTransform.getTranslateInstance(-off, -off).createTransformedShape(shape)));
        g.setColor(Render.shade(base, 0.72));
        g.fill(shade);
        if (inkWidth > 0) ink(g, shape, inkWidth, seed);
    }

    /** Белый блик-«фасолина». */
    static void shine(Graphics2D g, double x, double y, double w, double h, double angle) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(angle);
        g.setColor(new Color(255, 255, 250, 220));
        g.fill(new Ellipse2D.Double(-w / 2, -h / 2, w, h));
        g.setTransform(old);
    }

    // ================================================================ текст

    /** Надпись как на титрах 30-х: толстый контур и сдвинутая цветная тень. */
    static void titleText(Graphics2D g, Shape text, Color fill, Color shadow, float inkWidth, int seed) {
        Shape t = wobble(text, 0.5, seed);
        Shape back = AffineTransform.getTranslateInstance(inkWidth * 0.9, inkWidth * 1.2).createTransformedShape(t);
        g.setStroke(Render.round(inkWidth * 2));
        g.setColor(INK);
        g.draw(back);
        g.setColor(shadow);
        g.fill(back);
        g.setColor(INK);
        g.draw(t);
        g.setColor(fill);
        g.fill(t);
    }

    /**
     * Текст по дуге. arch=true — дуга выгнута вверх (∩), иначе улыбкой (∪).
     * (cx, y) — середина базовой линии, radius — радиус дуги.
     */
    static Shape arcText(Graphics2D g, String text, Font font, double cx, double y, double radius, boolean arch) {
        GlyphVector gv = font.createGlyphVector(g.getFontRenderContext(), text);
        double total = gv.getLogicalBounds().getWidth();
        Path2D out = new Path2D.Double();
        for (int i = 0; i < gv.getNumGlyphs(); i++) {
            Shape glyph = gv.getGlyphOutline(i);
            Rectangle2D gb = gv.getGlyphLogicalBounds(i).getBounds2D();
            double mid = gb.getCenterX();
            double a = (mid - total / 2) / radius;
            double px = cx + radius * Math.sin(a);
            double py = arch ? y + radius * (1 - Math.cos(a)) : y - radius * (1 - Math.cos(a));
            AffineTransform tx = AffineTransform.getTranslateInstance(px, py);
            tx.rotate(arch ? a : -a);
            tx.translate(-mid, 0);
            out.append(tx.createTransformedShape(glyph), false);
        }
        return out;
    }

    // ================================================================ детали

    /** Винтажная лампочка с чернильным контуром (спрайт из кэша). */
    static void bulb(Graphics2D g, double x, double y, double r, boolean lit) {
        Render.sprite(g, "tbulb:" + r + ":" + lit, x, y, r * 3.2, 0, s -> {
            if (lit) Render.glow(s, 0, 0, r * 3.2, new Color(255, 214, 120), 150);
            s.setPaint(new RadialGradientPaint((float) (-r * 0.3), (float) (-r * 0.35), (float) (r * 1.3),
                    new float[]{0f, 1f},
                    new Color[]{lit ? Color.WHITE : new Color(214, 188, 140), lit ? new Color(255, 206, 90) : new Color(150, 116, 76)}));
            s.fill(Render.circle(0, 0, r));
            s.setStroke(Render.round(Math.max(1f, (float) r * 0.35f)));
            s.setColor(INK);
            s.draw(Render.circle(0, 0, r));
            if (lit) shine(s, -r * 0.35, -r * 0.35, r * 0.5, r * 0.3, -0.6);
        });
    }

    /**
     * «Пирожковые» глаза в духе старых мультфильмов: белок, зрачок с вырезанным клином.
     * (lookX, lookY) — направление взгляда в пределах [-1, 1].
     */
    static void eye(Graphics2D g, double x, double y, double w, double h, double lookX, double lookY, boolean closed) {
        if (closed) {
            g.setStroke(Render.round((float) Math.max(1.5, w * 0.18)));
            g.setColor(INK);
            Path2D lid = new Path2D.Double();
            lid.moveTo(x - w / 2, y);
            lid.quadTo(x, y + h * 0.35, x + w / 2, y);
            g.draw(lid);
            return;
        }
        Ellipse2D white = new Ellipse2D.Double(x - w / 2, y - h / 2, w, h);
        g.setColor(CREAM);
        g.fill(white);
        g.setStroke(Render.round((float) Math.max(1.2, w * 0.14)));
        g.setColor(INK);
        g.draw(white);
        double pw = w * 0.52, ph = h * 0.62;
        double px = x + lookX * (w - pw) * 0.45, py = y + lookY * (h - ph) * 0.45;
        g.fill(new Ellipse2D.Double(px - pw / 2, py - ph / 2, pw, ph));
        // Вырезанный клин — фирменный «pie-cut» зрачок
        g.setColor(CREAM);
        g.fill(new java.awt.geom.Arc2D.Double(px - pw / 2, py - ph / 2, pw, ph, 40, 45, java.awt.geom.Arc2D.PIE));
    }
}
