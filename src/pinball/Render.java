package pinball;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Общие приёмы отрисовки: качество, кэш спрайтов, свечение, текст-контуры. */
final class Render {
    private static final Map<String, BufferedImage> SPRITES = new HashMap<>();

    private Render() {}

    static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    static Ellipse2D circle(double x, double y, double r) {
        return new Ellipse2D.Double(x - r, y - r, r * 2, r * 2);
    }

    static Color alpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    static Color shade(Color c, double k) {
        return new Color((int) Math.min(255, c.getRed() * k), (int) Math.min(255, c.getGreen() * k),
                (int) Math.min(255, c.getBlue() * k), c.getAlpha());
    }

    static BasicStroke round(float width) {
        return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    /** Мягкое свечение; готовый спрайт берётся из кэша. */
    static void glow(Graphics2D g, double x, double y, double r, Color c, int alpha) {
        double radius = Math.round(r * 2) / 2.0;
        sprite(g, "glow:" + c.getRGB() + ":" + radius + ":" + alpha, x, y, radius, 0,
                s -> rawGlow(s, radius, c, alpha));
    }

    private static void rawGlow(Graphics2D g, double r, Color c, int alpha) {
        g.setPaint(new RadialGradientPaint(0f, 0f, (float) r,
                new float[]{0f, 0.35f, 1f},
                new Color[]{alpha(c, alpha), alpha(c, alpha / 3), alpha(c, 0)}));
        g.fill(circle(0, 0, r));
    }

    /**
     * Кэшируемый спрайт. painter рисует в локальных координатах с центром (0,0) в пределах ±half;
     * key должен однозначно описывать картинку. Спрайт можно повернуть на angle.
     */
    static void sprite(Graphics2D g, String key, double cx, double cy, double half, double angle,
                       Consumer<Graphics2D> painter) {
        sprite(g, key, cx, cy, half, angle, 1, 1, painter);
    }

    /** То же, с растяжением по осям (мультяшные squash-and-stretch). */
    static void sprite(Graphics2D g, String key, double cx, double cy, double half, double angle,
                       double scaleX, double scaleY, Consumer<Graphics2D> painter) {
        int scale = half > 120 ? 1 : half > 60 ? 2 : 3;
        BufferedImage img = SPRITES.get(key);
        if (img == null) {
            // Не computeIfAbsent: painter сам может рисовать вложенные спрайты и менять кэш
            img = renderSprite(half, scale, painter);
            SPRITES.put(key, img);
        }
        AffineTransform tx = AffineTransform.getTranslateInstance(cx, cy);
        if (angle != 0) tx.rotate(angle);
        if (scaleX != 1 || scaleY != 1) tx.scale(scaleX, scaleY);
        tx.translate(-half, -half);
        tx.scale(1.0 / scale, 1.0 / scale);
        g.drawImage(img, tx, null);
    }

    private static BufferedImage renderSprite(double half, int scale, Consumer<Graphics2D> painter) {
        int size = Math.max(1, (int) Math.ceil(half * 2 * scale));
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        quality(g);
        g.scale(scale, scale);
        g.translate(half, half);
        painter.accept(g);
        g.dispose();
        return img;
    }

    static Shape centeredText(Graphics2D g, String text, double centerX, double y) {
        return centeredText(g, text, g.getFont(), centerX, y);
    }

    static Shape centeredText(Graphics2D g, String text, Font font, double centerX, double y) {
        TextLayout layout = new TextLayout(text, font, g.getFontRenderContext());
        return layout.getOutline(AffineTransform.getTranslateInstance(centerX - layout.getAdvance() / 2, y));
    }
}
