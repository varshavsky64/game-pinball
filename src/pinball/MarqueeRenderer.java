package pinball;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Корпус автомата: деревянная рама театральной сцены с винтажными лампочками. */
final class MarqueeRenderer {
    static final int FRAME = 24;
    static final int GAP = 16;

    private static final int SUPERSAMPLE = 2;
    private static final double SPACING = 24;
    private static final double BULB_RADIUS = 4.5;

    /** Лампа гирлянды: позиция и порядковый номер для «бегущей» анимации. */
    private record Bulb(double x, double y, int index) {}

    private final int width;
    private final int height;
    private final List<Bulb> bulbs = new ArrayList<>();
    /** Рама с погасшими лампами; на каждом кадре дорисовываются только горящие. */
    private final BufferedImage staticLayer;

    MarqueeRenderer(int width, int height, int separatorX) {
        this.width = width;
        this.height = height;
        double inset = FRAME / 2.0;
        int index = edge(inset, inset, width - inset, inset, 0);
        index = edge(width - inset, inset, width - inset, height - inset, index);
        index = edge(width - inset, height - inset, inset, height - inset, index);
        edge(inset, height - inset, inset, inset, index);
        double sx = separatorX + GAP / 2.0;
        edge(sx, FRAME + SPACING, sx, height - FRAME - SPACING, 0);

        staticLayer = new BufferedImage(width * SUPERSAMPLE, height * SUPERSAMPLE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = staticLayer.createGraphics();
        Render.quality(g);
        g.scale(SUPERSAMPLE, SUPERSAMPLE);
        g.setPaint(new GradientPaint(0, 0, new Color(130, 58, 36), width, height, new Color(70, 28, 18)));
        g.fillRect(0, 0, width, height);
        // Древесные волокна
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(40, 15, 5, 60));
        for (int y = 0; y < height; y += 6) {
            Path2D grain = new Path2D.Double();
            grain.moveTo(0, y);
            for (int x = 0; x <= width; x += 40) grain.lineTo(x, y + 1.5 * Math.sin(x * 0.03 + y));
            g.draw(grain);
        }
        g.setStroke(Render.round(3f));
        g.setColor(Toon.INK);
        g.draw(Toon.wobble(new Rectangle2D.Double(2, 2, width - 4, height - 4), 0.6, 0));
        g.draw(Toon.wobble(new Rectangle2D.Double(FRAME - 3, FRAME - 3, separatorX - FRAME + 6, height - 2 * FRAME + 6), 0.6, 1));
        g.draw(Toon.wobble(new Rectangle2D.Double(separatorX + GAP - 3, FRAME - 3, width - separatorX - GAP - FRAME + 6,
                height - 2 * FRAME + 6), 0.6, 2));
        for (Bulb b : bulbs) Toon.bulb(g, b.x, b.y, BULB_RADIUS, false);
        g.dispose();
    }

    private int edge(double x0, double y0, double x1, double y1, int startIndex) {
        int count = (int) (Math.hypot(x1 - x0, y1 - y0) / SPACING);
        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            bulbs.add(new Bulb(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, startIndex + i));
        }
        return startIndex + count;
    }

    void draw(Graphics2D g, Game game) {
        g.drawImage(staticLayer, 0, 0, width, height, null);
        if (game.tilted) return;
        // Только «бегущие» огни: одновременное мигание всей рамкой утомляет глаза
        boolean fast = game.eventFlash > 0 || game.multiball;
        int chase = (int) (game.time * (game.gameOver ? 3 : fast ? 14 : 6));
        for (Bulb b : bulbs) {
            boolean lit;
            if (fast) lit = Math.floorMod(b.index - chase, 3) != 2;
            else lit = Math.floorMod(b.index - chase, 3) == 0;
            if (lit) Toon.bulb(g, b.x, b.y, BULB_RADIUS, true);
        }
    }
}
