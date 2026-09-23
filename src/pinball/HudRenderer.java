package pinball;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static pinball.Toon.CREAM;
import static pinball.Toon.DARK_RED;
import static pinball.Toon.INK;
import static pinball.Toon.MUSTARD;
import static pinball.Toon.RED;
import static pinball.Toon.WOOD;

/** Правая панель — старая афиша: медальон с названием, механические счётчики, титры немого кино. */
final class HudRenderer {
    static final int WIDTH = 280;

    private static final int SUPERSAMPLE = 2;
    private static final Rectangle2D SCORE_HOUSING = new Rectangle2D.Double(16, 222, WIDTH - 32, 70);
    private static final Rectangle2D JACKPOT_HOUSING = new Rectangle2D.Double(16, 326, WIDTH - 32, 62);
    private static final RoundRectangle2D STATUS_CARD = new RoundRectangle2D.Double(16, 404, WIDTH - 32, 196, 14, 14);
    private static final RoundRectangle2D MISSION_CARD = new RoundRectangle2D.Double(16, 612, WIDTH - 32, 106, 14, 14);
    private static final RoundRectangle2D TICKER_CARD = new RoundRectangle2D.Double(16, 730, WIDTH - 32, 116, 14, 14);
    private static final RoundRectangle2D RECORD_PLAQUE = new RoundRectangle2D.Double(16, 858, WIDTH - 32, 46, 12, 12);
    private static final String[] STATUS_LABELS = {"BALL", "RANK", "MULTIPLIER", "COMBO", "RAMPS", "KICKBACK"};
    private static final int STATUS_Y = 434;
    private static final int STATUS_STEP = 28;
    private static final int CONTROLS_Y = 918;
    private static final int CONTROLS_STEP = 34;
    private static final int CONTROLS_COLUMN_2 = 150;
    private static final int DIGITS = 9;

    /** Неизменная часть панели — по варианту на каждое «дрожание» линий. */
    private final BufferedImage[] staticLayer = new BufferedImage[Toon.BOIL_FRAMES];
    private double shownScore;
    private double shownJackpot;
    private double lastTime;
    /** Готовые контуры строк титров: текст меняется редко, а «дрожащий» контур дорого считать. */
    private final java.util.Map<String, Shape> tickerShapes = new java.util.HashMap<>();
    private final Odometer scoreCounter = new Odometer(SCORE_HOUSING, 22, 46, CREAM, INK);
    private final Odometer jackpotCounter = new Odometer(JACKPOT_HOUSING, 22, 40, new Color(236, 110, 90), CREAM);

    HudRenderer() {
        for (int v = 0; v < Toon.BOIL_FRAMES; v++) {
            BufferedImage img = new BufferedImage(WIDTH * SUPERSAMPLE, Table.HEIGHT * SUPERSAMPLE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            Render.quality(g);
            g.scale(SUPERSAMPLE, SUPERSAMPLE);
            paintStatic(g, v);
            g.dispose();
            staticLayer[v] = img;
        }
    }

    void draw(Graphics2D g, Game game, SoundEngine sound) {
        int boil = Toon.boil(game.time);
        double dt = Math.max(0, Math.min(0.1, game.time - lastTime));
        lastTime = game.time;
        shownScore = approach(shownScore, game.score, dt);
        shownJackpot = approach(shownJackpot, game.jackpot, dt);

        g.drawImage(staticLayer[boil], 0, 0, WIDTH, Table.HEIGHT, null);
        scoreCounter.draw(g, shownScore);
        jackpotCounter.draw(g, shownJackpot);
        if (game.multiball) {
            // Плавная пульсация вместо резкого мигания
            int alpha = (int) (60 + 40 * Math.sin(game.time * 3));
            Render.glow(g, WIDTH / 2.0, JACKPOT_HOUSING.getCenterY(), 150, new Color(255, 200, 110), alpha);
        }
        drawStatus(g, game);
        drawMission(g, game);
        drawTicker(g, game, boil);
        g.setFont(Toon.title(20));
        g.setColor(RED);
        rightAlign(g, Game.formatPoints(Math.max(game.highScore, game.score)), 889);
        g.setFont(Toon.card(13));
        g.setColor(INK);
        g.drawString(!sound.isAvailable() ? "sound: n/a" : sound.isEnabled() ? "sound: on" : "sound: off",
                CONTROLS_COLUMN_2 + 30, CONTROLS_Y + CONTROLS_STEP + 15);
        g.drawString(Toon.animatedFilm ? "film: on" : "film: off",
                CONTROLS_COLUMN_2 + 30, CONTROLS_Y + 3 * CONTROLS_STEP + 15);
    }

    /** Счётчик плавно «докручивается» до нового значения; сброс — мгновенно. */
    private static double approach(double shown, double target, double dt) {
        if (target < shown) return target;
        double next = shown + (target - shown) * Math.min(1, dt * 5);
        return target - next < 0.5 ? target : next;
    }

    // ================================================================ статичная часть

    private void paintStatic(Graphics2D g, int v) {
        Rectangle2D all = new Rectangle2D.Double(0, 0, WIDTH, Table.HEIGHT);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(WIDTH / 2.0, 500), 700f,
                new float[]{0f, 1f}, new Color[]{new Color(246, 232, 200), new Color(210, 176, 128)}));
        g.fill(all);
        Random rnd = new Random(31);
        Color[] tints = {MUSTARD, RED, Toon.TEAL, Toon.BROWN};
        for (int i = 0; i < 30; i++) {
            Render.glow(g, rnd.nextDouble() * WIDTH, rnd.nextDouble() * Table.HEIGHT, 40 + rnd.nextDouble() * 90,
                    tints[rnd.nextInt(tints.length)], 16 + rnd.nextInt(14));
        }
        for (int i = 0; i < 1400; i++) {
            g.setColor(new Color(90, 50, 20, 14 + rnd.nextInt(24)));
            g.fill(new Rectangle2D.Double(rnd.nextDouble() * WIDTH, rnd.nextDouble() * Table.HEIGHT, 0.6 + rnd.nextDouble() * 1.4, 0.6));
        }

        // Орнаментная рамка
        g.setStroke(Render.round(3f));
        g.setColor(INK);
        g.draw(Toon.wobble(new RoundRectangle2D.Double(5, 5, WIDTH - 10, Table.HEIGHT - 10, 16, 16), 0.6, v));
        g.setStroke(Render.round(1.3f));
        g.draw(Toon.wobble(new RoundRectangle2D.Double(11, 11, WIDTH - 22, Table.HEIGHT - 22, 12, 12), 0.6, v));
        for (double[] c : new double[][]{{11, 11}, {WIDTH - 11, 11}, {11, Table.HEIGHT - 11}, {WIDTH - 11, Table.HEIGHT - 11}}) {
            Toon.toon(g, Render.circle(c[0], c[1], 6), RED, 1.6f, v);
        }

        drawLogo(g, v);

        label(g, "SCORE", 214, v);
        housing(g, SCORE_HOUSING, v);
        label(g, "JACKPOT", 318, v);
        housing(g, JACKPOT_HOUSING, v);

        Toon.toon(g, STATUS_CARD, new Color(250, 240, 214), 2.5f, v);
        g.setFont(Toon.label(13));
        g.setColor(INK);
        for (int i = 0; i < STATUS_LABELS.length; i++) g.drawString(STATUS_LABELS[i], 30, STATUS_Y + i * STATUS_STEP);
        g.setStroke(Render.round(1f));
        g.setColor(new Color(120, 80, 50, 120));
        for (int i = 0; i < STATUS_LABELS.length - 1; i++) {
            double y = STATUS_Y + i * STATUS_STEP + 9;
            g.draw(Toon.wobble(new java.awt.geom.Line2D.Double(30, y, WIDTH - 30, y), 0.5, v + i));
        }

        Toon.toon(g, MISSION_CARD, new Color(250, 240, 214), 2.5f, v);
        g.setColor(INK);
        g.fill(Render.centeredText(g, "MISSION", Toon.label(13), WIDTH / 2.0, MISSION_CARD.getY() + 20));

        // Карточка титров немого кино
        g.setColor(new Color(24, 16, 12));
        g.fill(TICKER_CARD);
        g.setColor(CREAM);
        g.setStroke(Render.round(2.4f));
        g.draw(Toon.wobble(inset(TICKER_CARD, 7), 0.5, v));
        g.setStroke(Render.round(1f));
        g.draw(Toon.wobble(inset(TICKER_CARD, 12), 0.5, v + 1));
        for (double[] c : new double[][]{{TICKER_CARD.getMinX() + 12, TICKER_CARD.getMinY() + 12},
                {TICKER_CARD.getMaxX() - 12, TICKER_CARD.getMinY() + 12},
                {TICKER_CARD.getMinX() + 12, TICKER_CARD.getMaxY() - 12},
                {TICKER_CARD.getMaxX() - 12, TICKER_CARD.getMaxY() - 12}}) {
            g.fill(Render.circle(c[0], c[1], 3.2));
        }

        Toon.toon(g, RECORD_PLAQUE, MUSTARD, 2.5f, v);
        g.setFont(Toon.label(13));
        g.setColor(INK);
        g.drawString("HIGH SCORE", 30, 886);

        drawControls(g, v);
    }

    private static RoundRectangle2D inset(RoundRectangle2D r, double d) {
        return new RoundRectangle2D.Double(r.getX() + d, r.getY() + d, r.getWidth() - 2 * d, r.getHeight() - 2 * d,
                Math.max(4, r.getArcWidth() - d), Math.max(4, r.getArcHeight() - d));
    }

    private static void drawLogo(Graphics2D g, int v) {
        double cx = WIDTH / 2.0;
        RoundRectangle2D medallion = new RoundRectangle2D.Double(18, 18, WIDTH - 36, 172, 30, 30);
        Toon.toon(g, medallion, new Color(250, 236, 204), 0, v);
        Shape clip = g.getClip();
        g.clip(medallion);
        for (int i = 0; i < 24; i += 2) {
            double a0 = i * Math.PI / 12, a1 = a0 + Math.PI / 12;
            Path2D ray = new Path2D.Double();
            ray.moveTo(cx, 100);
            ray.lineTo(cx + 400 * Math.cos(a0), 100 + 400 * Math.sin(a0));
            ray.lineTo(cx + 400 * Math.cos(a1), 100 + 400 * Math.sin(a1));
            ray.closePath();
            g.setColor(new Color(214, 110, 70, 70));
            g.fill(ray);
        }
        g.setClip(clip);
        Toon.ink(g, medallion, 3f, v);

        Shape title = Toon.arcText(g, "JACKPOT", Toon.title(46), cx, 100, 230, true);
        Toon.titleText(g, title, MUSTARD, RED, 3.2f, v);

        // Лента с подписью
        double y = 146, w = 190, h = 26;
        Shape tailL = polygon(cx - w / 2 - 26, y - h / 2 + 7, cx - w / 2 + 6, y - h / 2 + 7, cx - w / 2 + 6, y + h / 2 + 7,
                cx - w / 2 - 26, y + h / 2 + 7, cx - w / 2 - 16, y + 7);
        Shape tailR = polygon(cx + w / 2 + 26, y - h / 2 + 7, cx + w / 2 - 6, y - h / 2 + 7, cx + w / 2 - 6, y + h / 2 + 7,
                cx + w / 2 + 26, y + h / 2 + 7, cx + w / 2 + 16, y + 7);
        Toon.toon(g, tailL, DARK_RED, 2f, v);
        Toon.toon(g, tailR, DARK_RED, 2f, v);
        Toon.toon(g, new Rectangle2D.Double(cx - w / 2, y - h / 2, w, h), RED, 2.2f, v);
        g.setColor(CREAM);
        g.fill(Render.centeredText(g, "PINBALL CASINO", Toon.label(14), cx, y + 5));

        for (int k = -1; k <= 1; k += 2) {
            Toon.toon(g, star(cx + k * 100, 176, 7, 3, 5), MUSTARD, 1.4f, v);
        }
    }

    private static void label(Graphics2D g, String text, double y, int v) {
        g.setColor(INK);
        g.fill(Render.centeredText(g, text, Toon.label(15), WIDTH / 2.0, y));
    }

    private static void housing(Graphics2D g, Rectangle2D box, int v) {
        Toon.toon(g, new RoundRectangle2D.Double(box.getX(), box.getY(), box.getWidth(), box.getHeight(), 14, 14), WOOD, 3f, v);
        for (double x : new double[]{box.getX() + 9, box.getMaxX() - 9}) {
            Toon.toon(g, Render.circle(x, box.getCenterY(), 3.5), MUSTARD, 1.2f, v);
        }
    }

    /** Клавиши в две колонки; подпись про звук дорисовывается на каждом кадре. */
    private static void drawControls(Graphics2D g, int v) {
        String[][] left = {{"Z", "←", "left"}, {"/", "→", "right"}, {"SPACE", null, "launch"}, {"↑", "N", "nudge"}};
        String[][] right = {{"P", null, "pause"}, {"M", null, ""}, {"ENTER", null, "restart"}, {"F", null, ""}};
        drawControlColumn(g, left, 26, v);
        drawControlColumn(g, right, CONTROLS_COLUMN_2, v);
    }

    private static void drawControlColumn(Graphics2D g, String[][] rows, double x0, int v) {
        int y = CONTROLS_Y;
        for (String[] row : rows) {
            double x = x0;
            x += keycap(g, row[0], x, y, v) + 4;
            if (row[1] != null) x += keycap(g, row[1], x, y, v) + 4;
            g.setFont(Toon.card(13));
            g.setColor(INK);
            g.drawString(row[2], (float) (x + 2), y + 15);
            y += CONTROLS_STEP;
        }
    }

    private static double keycap(Graphics2D g, String label, double x, double y, int v) {
        Font font = Toon.title(11);
        g.setFont(font);
        double w = Math.max(24, g.getFontMetrics().stringWidth(label) + 12);
        Toon.toon(g, new RoundRectangle2D.Double(x, y, w, 21, 7, 7), CREAM, 1.8f, v);
        g.setColor(INK);
        g.fill(Render.centeredText(g, label, font, x + w / 2, y + 15));
        return w;
    }

    // ================================================================ динамика

    /**
     * Механический счётчик: барабаны с цифрами. Младший барабан крутится непрерывно,
     * старший — только когда младшие переходят через 9, как у настоящего одометра.
     * Пока значение не меняется, счётчик рисуется одной готовой картинкой.
     */
    private static final class Odometer {
        private final Rectangle2D housing;
        private final double w, h;
        private final Color drum, digitColor;
        private BufferedImage cached;
        private double cachedValue = -1;

        Odometer(Rectangle2D housing, double w, double h, Color drum, Color digitColor) {
            this.housing = housing;
            this.w = w;
            this.h = h;
            this.drum = drum;
            this.digitColor = digitColor;
        }

        void draw(Graphics2D g, double value) {
            if (value != Math.floor(value)) {
                paint(g, value);
                return;
            }
            if (cached == null || cachedValue != value) {
                Rectangle2D b = housing;
                cached = new BufferedImage((int) b.getWidth() * SUPERSAMPLE, (int) b.getHeight() * SUPERSAMPLE,
                        BufferedImage.TYPE_INT_ARGB);
                Graphics2D c = cached.createGraphics();
                Render.quality(c);
                c.scale(SUPERSAMPLE, SUPERSAMPLE);
                c.translate(-b.getX(), -b.getY());
                paint(c, value);
                c.dispose();
                cachedValue = value;
            }
            g.drawImage(cached, (int) housing.getX(), (int) housing.getY(), (int) housing.getWidth(), (int) housing.getHeight(), null);
        }

        private void paint(Graphics2D g, double value) {
            double gap = 3;
            double total = DIGITS * w + (DIGITS - 1) * gap;
            double x0 = housing.getCenterX() - total / 2, y0 = housing.getCenterY() - h / 2;
            Shape clip = g.getClip();
            for (int i = 0; i < DIGITS; i++) {
                int power = DIGITS - 1 - i;
                double p = Math.pow(10, power);
                int digit = (int) (Math.floor(value / p) % 10);
                double lower = value - Math.floor(value / p) * p;
                double frac = power == 0 ? value - Math.floor(value) : Math.max(0, Math.min(1, lower - (p - 1)));
                double x = x0 + i * (w + gap), cx = x + w / 2, cy = y0 + h / 2;

                Render.sprite(g, "drum:" + drum.getRGB() + ":" + w + ":" + h, cx, cy, h / 2 + 2, 0, s -> {
                    s.setPaint(new GradientPaint(0, (float) (-h / 2), Render.shade(drum, 0.55), 0, 0, drum));
                    s.fill(new Rectangle2D.Double(-w / 2, -h / 2, w, h / 2));
                    s.setPaint(new GradientPaint(0, 0, drum, 0, (float) (h / 2), Render.shade(drum, 0.55)));
                    s.fill(new Rectangle2D.Double(-w / 2, 0, w, h / 2));
                });
                g.clip(new Rectangle2D.Double(x, y0, w, h));
                double offset = frac * h;
                int next = (digit + 1) % 10;
                Render.sprite(g, "odo:" + digit + ":" + digitColor.getRGB() + ":" + h, cx, cy - offset, h / 2, 0,
                        s -> odoDigit(s, digit, h, digitColor));
                if (offset > 0.01) {
                    Render.sprite(g, "odo:" + next + ":" + digitColor.getRGB() + ":" + h, cx, cy - offset + h, h / 2, 0,
                            s -> odoDigit(s, next, h, digitColor));
                }
                g.setClip(clip);
                Render.sprite(g, "drumGlass:" + w + ":" + h, cx, cy, h / 2 + 2, 0, s -> {
                    s.setPaint(new GradientPaint(0, (float) (-h / 2), new Color(40, 20, 10, 150), 0, (float) (-h / 4), new Color(40, 20, 10, 0)));
                    s.fill(new Rectangle2D.Double(-w / 2, -h / 2, w, h / 4));
                    s.setPaint(new GradientPaint(0, (float) (h / 4), new Color(40, 20, 10, 0), 0, (float) (h / 2), new Color(40, 20, 10, 150)));
                    s.fill(new Rectangle2D.Double(-w / 2, h / 4, w, h / 4));
                    s.setStroke(Render.round(1.8f));
                    s.setColor(INK);
                    s.draw(new Rectangle2D.Double(-w / 2, -h / 2, w, h));
                });
            }
        }
    }

    private static void odoDigit(Graphics2D g, int digit, double h, Color color) {
        Shape s = Render.centeredText(g, String.valueOf(digit), Toon.title((float) (h * 0.72)), 0, h * 0.26);
        g.setColor(color);
        g.fill(s);
    }

    private void drawStatus(Graphics2D g, Game game) {
        int y = STATUS_Y;
        value(g, game.gameOver ? "—" : game.ballNumber + " / " + (game.ballNumber + game.ballsLeft - 1), y, INK);
        y += STATUS_STEP;
        value(g, Game.RANKS[game.rank], y, RED);
        y += STATUS_STEP;
        value(g, "x" + game.multiplier, y, INK);
        y += STATUS_STEP;
        drawCombo(g, game, y);
        y += STATUS_STEP;
        if (game.multiball) {
            value(g, "MULTIBALL!", y, ((int) (game.time * 6)) % 2 == 0 ? RED : INK);
        } else {
            int lit = game.ramps % Game.RAMPS_FOR_MULTIBALL;
            for (int i = 0; i < Game.RAMPS_FOR_MULTIBALL; i++) Toon.bulb(g, WIDTH - 76 + i * 20, y - 5, 6, i < lit);
        }
        y += STATUS_STEP;
        value(g, game.kickbackLit ? "READY" : "—", y, INK);
    }

    /** Множитель комбо и полоска: сколько времени осталось до следующего попадания. */
    private static void drawCombo(Graphics2D g, Game game, int y) {
        int mult = game.comboMultiplier();
        value(g, game.combo > 0 ? "x" + mult + " · " + game.combo : "—", y, mult > 1 ? RED : INK);
        if (game.combo == 0) return;
        Rectangle2D bar = new Rectangle2D.Double(112, y - 10, 58, 8);
        g.setColor(new Color(214, 184, 140));
        g.fill(bar);
        g.setColor(mult > 1 ? RED : MUSTARD);
        g.fill(new Rectangle2D.Double(bar.getX(), bar.getY(), bar.getWidth() * game.comboFraction(), bar.getHeight()));
        g.setStroke(Render.round(1.4f));
        g.setColor(INK);
        g.draw(bar);
    }

    /** Текущее задание: что сделать, прогресс, время и награда. */
    private static void drawMission(Graphics2D g, Game game) {
        Mission m = game.mission;
        double cx = WIDTH / 2.0, top = MISSION_CARD.getY();
        if (m == null) {
            g.setColor(new Color(90, 60, 40));
            g.fill(Render.centeredText(g, game.gameOver ? "—" : "new mission…", Toon.card(15), cx, top + 60));
            return;
        }
        g.setColor(INK);
        g.fill(Render.centeredText(g, m.describe(), Toon.title(16), cx, top + 46));

        Rectangle2D bar = new Rectangle2D.Double(30, top + 56, WIDTH - 60, 12);
        g.setColor(new Color(214, 184, 140));
        g.fill(bar);
        g.setColor(MUSTARD);
        g.fill(new Rectangle2D.Double(bar.getX(), bar.getY(), bar.getWidth() * m.progress / m.target, bar.getHeight()));
        double timeShare = Math.max(0, m.timeLeft / m.duration);
        g.setColor(RED);
        g.fill(new Rectangle2D.Double(bar.getX(), bar.getMaxY() - 3, bar.getWidth() * timeShare, 3));
        g.setStroke(Render.round(1.6f));
        g.setColor(INK);
        g.draw(bar);

        boolean hurry = m.timeLeft < 10;
        int seconds = (int) Math.ceil(m.timeLeft);
        g.setFont(Toon.card(13));
        g.setColor(hurry ? RED : INK);
        g.drawString(String.format("%d:%02d", seconds / 60, seconds % 60), 30, (float) (top + 90));
        g.setColor(INK);
        g.setFont(Toon.title(14));
        rightAlign(g, "+" + Game.formatPoints(m.reward), (int) (top + 90));
    }

    private static void value(Graphics2D g, String text, int y, Color color) {
        g.setFont(Toon.title(15));
        g.setColor(color);
        rightAlign(g, text, y);
    }

    private static void rightAlign(Graphics2D g, String text, int y) {
        g.drawString(text, WIDTH - 30 - g.getFontMetrics().stringWidth(text), y);
    }

    /** Сообщения игры — белым курсивом на чёрной карточке, как титры немого кино. */
    private void drawTicker(Graphics2D g, Game game, int boil) {
        String text = game.message();
        if (text.isEmpty()) return;
        // Новое сообщение плавно проявляется, без мигания
        double fade = Math.min(1, game.messageAge() / 0.25);
        List<String> lines = wrap(text, 18);
        int lineHeight = 26;
        double y = TICKER_CARD.getCenterY() + 8 - (lines.size() - 1) * lineHeight / 2.0;
        Font font = Toon.card(20);
        g.setColor(new Color(253, 246, 226, (int) (255 * fade)));
        for (String line : lines) {
            double lineY = y;
            if (tickerShapes.size() > 200) tickerShapes.clear();
            Shape shape = tickerShapes.computeIfAbsent(line + "\n" + lineY + "\n" + boil,
                    k -> Toon.wobble(Render.centeredText(g, line, font, WIDTH / 2.0, lineY), 0.35, boil));
            g.fill(shape);
            y += lineHeight;
        }
    }

    private static List<String> wrap(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > maxChars) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.size() > 4 ? lines.subList(0, 4) : lines;
    }

    // ================================================================ геометрия

    private static Shape star(double cx, double cy, double outer, double inner, int points) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < points * 2; i++) {
            double a = -Math.PI / 2 + i * Math.PI / points;
            double r = i % 2 == 0 ? outer : inner;
            if (i == 0) p.moveTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
            else p.lineTo(cx + r * Math.cos(a), cy + r * Math.sin(a));
        }
        p.closePath();
        return p;
    }

    private static Path2D polygon(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.closePath();
        return p;
    }
}
