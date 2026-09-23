package pinball;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static pinball.Toon.BROWN;
import static pinball.Toon.CREAM;
import static pinball.Toon.DARK_RED;
import static pinball.Toon.GREEN;
import static pinball.Toon.INK;
import static pinball.Toon.MUSTARD;
import static pinball.Toon.RED;
import static pinball.Toon.TEAL;
import static pinball.Toon.WOOD;

/** Стол в стиле мультфильмов 1930-х: акварель, чернильный контур, персонажи с глазами. */
final class TableRenderer {
    /** Статичные слои рисуются в удвоенном разрешении — чётко на Retina и при масштабировании. */
    private static final int SUPERSAMPLE = 2;
    private static final Rectangle FULL = new Rectangle(0, 0, Table.WIDTH, Table.HEIGHT);
    private static final Rectangle FOREGROUND_BOUNDS = new Rectangle(0, 985, Table.WIDTH, Table.HEIGHT - 985);
    private static final Color RAIL = new Color(236, 196, 118);
    private static final Color RAIL_LIGHT = new Color(255, 244, 214);
    private static final Color TAN = new Color(190, 150, 100);
    private static final Color[] MULTIPLIER_COLORS = {RED, MUSTARD, TEAL, Toon.PURPLE};

    private final Table table;
    /** Для каждого варианта «дрожания» линий — свой набор статичных слоёв. */
    private final BufferedImage[] background = new BufferedImage[Toon.BOIL_FRAMES];
    private final BufferedImage[] foreground = new BufferedImage[Toon.BOIL_FRAMES];
    private final BufferedImage[] rampLayer = new BufferedImage[Toon.BOIL_FRAMES];
    private final Rectangle rampBounds;
    private final Shape[] pockets = new Shape[Roulette.WHEEL.length];
    private final List<Vec2> rampBulbs = new ArrayList<>();
    private final Shape flipperShape;

    TableRenderer(Table table) {
        this.table = table;

        double r1 = Table.ROULETTE_RADIUS * 0.6, r2 = Table.ROULETTE_RADIUS * 0.86;
        double deg = Math.toDegrees(Roulette.POCKET);
        for (int i = 0; i < pockets.length; i++) {
            // Arc2D отсчитывает углы против часовой стрелки; на экране (ось Y вниз) это наш «минус»
            Area wedge = new Area(new Arc2D.Double(-r2, -r2, r2 * 2, r2 * 2, -(i + 1) * deg, deg, Arc2D.PIE));
            wedge.subtract(new Area(new Ellipse2D.Double(-r1, -r1, r1 * 2, r1 * 2)));
            pockets[i] = wedge;
        }

        double pivotR = 9, tipR = 5, len = table.leftFlipper.length;
        Area flipper = new Area(new Ellipse2D.Double(-pivotR, -pivotR, pivotR * 2, pivotR * 2));
        flipper.add(new Area(new Ellipse2D.Double(len - tipR, -tipR, tipR * 2, tipR * 2)));
        flipper.add(new Area(polygon(0, -pivotR, len, -tipR, len, tipR, 0, pivotR)));
        flipperShape = flipper;

        for (double d = 20; d < table.ramp.length - 10; d += 34) {
            Vec2 p = table.ramp.pointAt(d), dir = table.ramp.directionAt(d);
            rampBulbs.add(p.add(new Vec2(-dir.y(), dir.x()).scale(17)));
        }
        Rectangle band = rampEdge(17).getBounds().union(rampEdge(-17).getBounds());
        rampBounds = new Rectangle(band.x - 10, band.y - 10, band.width + 26, band.height + 28);

        for (int v = 0; v < Toon.BOIL_FRAMES; v++) {
            int variant = v;
            background[v] = prerender(FULL, g -> paintBackground(g, variant));
            foreground[v] = prerender(FOREGROUND_BOUNDS, g -> paintForeground(g, variant));
            rampLayer[v] = prerender(rampBounds, g -> paintRamp(g, variant));
        }
    }

    /** Статичный слой в пределах bounds, в удвоенном разрешении. */
    private static BufferedImage prerender(Rectangle bounds, Consumer<Graphics2D> painter) {
        BufferedImage img = new BufferedImage(bounds.width * SUPERSAMPLE, bounds.height * SUPERSAMPLE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        Render.quality(g);
        g.scale(SUPERSAMPLE, SUPERSAMPLE);
        g.translate(-bounds.x, -bounds.y);
        painter.accept(g);
        g.dispose();
        return img;
    }

    private static void blit(Graphics2D g, BufferedImage img, Rectangle bounds) {
        g.drawImage(img, bounds.x, bounds.y, bounds.width, bounds.height, null);
    }

    void draw(Graphics2D g, Game game) {
        int boil = Toon.boil(game.time);
        blit(g, background[boil], FULL);
        drawLamps(g, game, boil);
        drawRoulette(g, game, boil);
        drawSlotMachine(g, game, boil);
        drawSaucerHole(g, table.slotSaucer, game, boil);
        drawCards(g, game, boil);
        drawStars(g, game, boil);
        drawVortex(g, game, boil);
        drawHat(g, game, boil);
        drawBells(g, game, boil);
        drawDucks(g, game, boil);
        drawSpinner(g);
        drawSlingLamps(g);
        for (int i = 0; i < table.bumpers.size(); i++) drawChip(g, game, table.bumpers.get(i), i, boil);
        drawPlunger(g, game, boil);
        for (Ball b : game.balls) if (b.held) drawBall(g, b, 0.8, false, boil);
        drawFlipper(g, table.leftFlipper, boil);
        drawFlipper(g, table.rightFlipper, boil);
        drawRabbit(g, game, boil);
        for (Ball b : game.balls) if (!b.held && !b.onRamp()) drawBall(g, b, 1, true, boil);
        blit(g, rampLayer[boil], rampBounds);
        drawRampBulbs(g, game);
        for (Ball b : game.balls) if (b.onRamp()) drawBall(g, b, 1.12, true, boil);
        blit(g, foreground[boil], FOREGROUND_BOUNDS);
        drawKickback(g, game, boil);
        drawParticles(g, game);
        drawHurryUp(g, game, boil);
        drawPopups(g, game, boil);
        drawOverlay(g, game, boil);
    }

    /** Тающий приз «Хватай!» над слот-машиной. */
    private static void drawHurryUp(Graphics2D g, Game game, int boil) {
        if (game.hurryUp <= 0) return;
        double pulse = 1 + 0.06 * Math.sin(game.time * 12);
        AffineTransform old = g.getTransform();
        g.translate(Table.CENTER_X, 458);
        g.scale(pulse, pulse);
        int shown = (int) (game.hurryUp / 1000) * 1000;
        Toon.titleText(g, Render.centeredText(g, "HURRY UP! " + Game.formatPoints(shown), Toon.title(22), 0, 8),
                CREAM, RED, 2.2f, boil);
        g.setTransform(old);
        arrowDown(g, Table.CENTER_X, 578 + 4 * Math.sin(game.time * 10), boil);
    }

    private static void arrowDown(Graphics2D g, double x, double y, int boil) {
        Render.sprite(g, "hurryArrow:" + boil, x, y, 16, 0, s ->
                Toon.toon(s, polygon(-9, -8, 9, -8, 9, -2, 0, 8, -9, -2), MUSTARD, 1.8f, boil));
    }

    // ================================================================ статичный фон

    private void paintBackground(Graphics2D g, int v) {
        Shape field = playfieldShape();

        // Старая бумага, тёплая в центре
        g.setPaint(new RadialGradientPaint(new Point2D.Double(Table.CENTER_X, 520), 700f,
                new float[]{0f, 0.6f, 1f},
                new Color[]{new Color(248, 234, 202), new Color(232, 204, 158), new Color(196, 150, 100)}));
        g.fill(field);

        Shape oldClip = g.getClip();
        g.clip(field);
        rays(g, Table.CENTER_X, 560, 32);
        watercolor(g, 11);
        paperFibers(g, 23);

        // Цирковой манеж вокруг бамперов
        Ellipse2D ringArea = new Ellipse2D.Double(Table.CENTER_X - 150, 165, 300, 212);
        g.setColor(new Color(255, 246, 220, 150));
        g.fill(ringArea);
        Toon.toon(g, new BasicStroke(14f).createStrokedShape(ringArea), RED, 2.5f, v);
        for (int i = 0; i < 20; i++) {
            double a = i * Math.PI / 10;
            double x = ringArea.getCenterX() + Math.cos(a) * 150, y = ringArea.getCenterY() + Math.sin(a) * 106;
            Toon.toon(g, Render.circle(x, y, 3), CREAM, 1.2f, v + i);
        }

        // Кольцо вокруг рулетки и «взрыв» за слот-машиной
        Toon.toon(g, new BasicStroke(9f).createStrokedShape(
                Render.circle(Table.ROULETTE_CENTER.x(), Table.ROULETTE_CENTER.y(), Table.ROULETTE_RADIUS + 12)), MUSTARD, 2f, v);
        Toon.toon(g, star(Table.CENTER_X, 540, 98, 76, 16), new Color(246, 210, 120), 2.5f, v);

        suits(g, v);
        gallery(g, v);
        vortexBase(g, v);

        Shape lucky = Toon.arcText(g, "LUCKY 7", Toon.title(46), Table.CENTER_X, 812, 300, false);
        Toon.titleText(g, lucky, MUSTARD, RED, 3f, v);

        // Виньетка по краям поля
        g.setPaint(new RadialGradientPaint(new Point2D.Double(Table.CENTER_X, 520), 640f,
                new float[]{0.55f, 1f}, new Color[]{new Color(80, 40, 10, 0), new Color(80, 40, 10, 130)}));
        g.fill(field);
        g.setColor(new Color(60, 30, 10, 70));
        g.fill(new Rectangle2D.Double(Table.LANE_LEFT, 290, Table.LANE_RIGHT - Table.LANE_LEFT, Table.HEIGHT));
        g.setClip(oldClip);

        cabinet(g, field, v);
        double gy = Table.GUIDE_TOP_Y, right = Table.LANE_LEFT;
        apron(g, polygon(20, gy, 193, 922, 193, 1080, 20, 1080), 193, 922, 20, gy, 105, v);
        apron(g, polygon(right, gy, 387, 922, 387, 1080, right, 1080), 387, 922, right, gy, 475, v);

        for (Table.Sling s : table.slings) sling(g, s, v);
        for (List<Vec2> rail : table.rails) rail(g, path(rail), 6f, v);
        Table.Wall gate = table.gate;
        rail(g, new Line2D.Double(gate.a.x(), gate.a.y(), gate.b.x(), gate.b.y()), 4f, v);
    }

    private Shape playfieldShape() {
        Path2D p = path(table.outline);
        p.lineTo(Table.LANE_RIGHT, Table.HEIGHT);
        p.lineTo(20, Table.HEIGHT);
        p.closePath();
        return p;
    }

    /** Лучи, расходящиеся из центра, — как на заставках старых мультфильмов. */
    private static void rays(Graphics2D g, double cx, double cy, int count) {
        double far = 1500;
        for (int i = 0; i < count; i += 2) {
            double a0 = i * 2 * Math.PI / count, a1 = a0 + 2 * Math.PI / count;
            Path2D wedge = new Path2D.Double();
            wedge.moveTo(cx, cy);
            wedge.lineTo(cx + far * Math.cos(a0), cy + far * Math.sin(a0));
            wedge.lineTo(cx + far * Math.cos(a1), cy + far * Math.sin(a1));
            wedge.closePath();
            g.setColor(new Color(214, 120, 80, 42));
            g.fill(wedge);
        }
    }

    /** Акварельные разводы: мягкие пятна тёплых цветов. */
    private static void watercolor(Graphics2D g, long seed) {
        Random rnd = new Random(seed);
        Color[] tints = {MUSTARD, RED, TEAL, BROWN, CREAM};
        for (int i = 0; i < 70; i++) {
            double x = rnd.nextDouble() * Table.WIDTH, y = rnd.nextDouble() * Table.HEIGHT;
            Render.glow(g, x, y, 40 + rnd.nextDouble() * 120, tints[rnd.nextInt(tints.length)], 18 + rnd.nextInt(18));
        }
    }

    private static void paperFibers(Graphics2D g, long seed) {
        Random rnd = new Random(seed);
        for (int i = 0; i < 2600; i++) {
            double x = rnd.nextDouble() * Table.WIDTH, y = rnd.nextDouble() * Table.HEIGHT;
            g.setColor(new Color(90, 50, 20, 14 + rnd.nextInt(26)));
            g.fill(new Rectangle2D.Double(x, y, 0.6 + rnd.nextDouble() * 1.6, 0.6));
        }
    }

    private static void suits(Graphics2D g, int v) {
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 58);
        Object[][] items = {{"♣", 510, 120, GREEN}};
        for (Object[] it : items) {
            Shape s = Render.centeredText(g, (String) it[0], font, (int) it[1], (int) it[2]);
            Toon.toon(g, s, (Color) it[3], 2.5f, v);
            Rectangle2D b = s.getBounds2D();
            Toon.shine(g, b.getX() + b.getWidth() * 0.32, b.getY() + b.getHeight() * 0.3, 9, 5, -0.7);
        }
    }

    /** Корпус вокруг поля: тёмное дерево. */
    private static void cabinet(Graphics2D g, Shape field, int v) {
        Area outside = new Area(FULL);
        outside.subtract(new Area(field));
        g.setPaint(new GradientPaint(0, 0, new Color(120, 54, 36), Table.WIDTH, Table.HEIGHT, new Color(70, 30, 20)));
        g.fill(outside);
        Shape clip = g.getClip();
        g.clip(outside);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(40, 15, 5, 70));
        for (int x = 0; x < Table.WIDTH; x += 7) {
            Path2D grain = new Path2D.Double();
            grain.moveTo(x, 0);
            for (int y = 0; y <= Table.HEIGHT; y += 40) grain.lineTo(x + 2 * Math.sin(y * 0.02 + x), y);
            g.draw(grain);
        }
        g.setClip(clip);
        Toon.ink(g, field, 4f, v);
    }

    private static void apron(Graphics2D g, Shape shape, double x1, double y1, double x2, double y2, double starX, int v) {
        Toon.toon(g, shape, new Color(214, 160, 70), 3f, v);
        g.setStroke(Render.round(5f));
        g.setColor(RED);
        g.draw(Toon.wobble(new Line2D.Double(x1, y1 + 18, x2, y2 + 18), 0.6, v));
        Toon.toon(g, star(starX, 985, 24, 10, 5), CREAM, 2.5f, v);
        Toon.toon(g, star(starX, 985, 11, 5, 5), RED, 1.5f, v + 1);
    }

    private static void sling(Graphics2D g, Table.Sling s, int v) {
        Shape tri = triangle(s);
        Toon.toon(g, tri, RED, 0, v);
        g.setColor(DARK_RED);
        g.fill(slingLamp(s));
        Toon.toon(g, new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(tri), CREAM, 2f, v);
        for (Vec2 p : new Vec2[]{s.top(), s.bottom(), s.corner()}) Toon.toon(g, Render.circle(p.x(), p.y(), 5), MUSTARD, 1.8f, v);
    }

    private static Shape triangle(Table.Sling s) {
        return polygon(s.top().x(), s.top().y(), s.bottom().x(), s.bottom().y(), s.corner().x(), s.corner().y());
    }

    private static Shape slingLamp(Table.Sling s) {
        double cx = (s.top().x() + s.bottom().x() + s.corner().x()) / 3;
        double cy = (s.top().y() + s.bottom().y() + s.corner().y()) / 3;
        double k = 0.45;
        return polygon(
                cx + (s.top().x() - cx) * k, cy + (s.top().y() - cy) * k,
                cx + (s.bottom().x() - cx) * k, cy + (s.bottom().y() - cy) * k,
                cx + (s.corner().x() - cx) * k, cy + (s.corner().y() - cy) * k);
    }

    /** Рельс: толстая чернильная линия, латунь и блик. */
    private static void rail(Graphics2D g, Shape path, float width, int v) {
        Shape w = Toon.wobble(path, 0.7, v);
        g.setColor(new Color(60, 30, 10, 70));
        g.setStroke(Render.round(width + 4));
        g.draw(AffineTransform.getTranslateInstance(3, 4).createTransformedShape(w));
        g.setColor(INK);
        g.draw(w);
        g.setStroke(Render.round(width));
        g.setColor(RAIL);
        g.draw(w);
        g.setStroke(Render.round(width * 0.35f));
        g.setColor(RAIL_LIGHT);
        g.draw(AffineTransform.getTranslateInstance(-0.8, -0.8).createTransformedShape(w));
    }

    // ================================================================ передний план

    /** Дощатая сцена внизу с лентой-вывеской; шар «уходит» под неё при потере. */
    private void paintForeground(Graphics2D g, int v) {
        Path2D plate = new Path2D.Double();
        plate.moveTo(20, 1080);
        plate.lineTo(20, 1032);
        plate.lineTo(193, 1032);
        plate.quadTo(Table.CENTER_X, 994, 387, 1032);
        plate.lineTo(Table.LANE_LEFT, 1032);
        plate.lineTo(Table.LANE_LEFT, 1080);
        plate.closePath();
        Toon.toon(g, plate, new Color(150, 84, 44), 0, v);
        Shape clip = g.getClip();
        g.clip(plate);
        g.setStroke(Render.round(1.5f));
        g.setColor(new Color(70, 30, 10, 160));
        for (int y = 1000; y < 1080; y += 13) g.draw(Toon.wobble(new Line2D.Double(0, y, Table.WIDTH, y), 0.6, v));
        for (int x = 40; x < Table.WIDTH; x += 70) g.draw(Toon.wobble(new Line2D.Double(x, 1000, x, 1080), 0.6, v));
        g.setClip(clip);
        Toon.ink(g, plate, 3.5f, v);

        // Лента-вывеска
        double cx = Table.CENTER_X, y = 1058, w = 290, h = 30;
        Shape tailL = polygon(cx - w / 2 - 34, y - h / 2 + 8, cx - w / 2 + 8, y - h / 2 + 8, cx - w / 2 + 8, y + h / 2 + 8,
                cx - w / 2 - 34, y + h / 2 + 8, cx - w / 2 - 22, y + 8);
        Shape tailR = polygon(cx + w / 2 + 34, y - h / 2 + 8, cx + w / 2 - 8, y - h / 2 + 8, cx + w / 2 - 8, y + h / 2 + 8,
                cx + w / 2 + 34, y + h / 2 + 8, cx + w / 2 + 22, y + 8);
        Toon.toon(g, tailL, DARK_RED, 2.5f, v);
        Toon.toon(g, tailR, DARK_RED, 2.5f, v);
        Toon.toon(g, new Rectangle2D.Double(cx - w / 2, y - h / 2, w, h), RED, 2.5f, v);
        Shape text = Render.centeredText(g, "JACKPOT  PINBALL", Toon.title(19), cx, y + 7);
        Toon.titleText(g, text, CREAM, DARK_RED, 1.6f, v);
    }

    // ================================================================ рампа

    private Path2D rampEdge(double offset) {
        List<Vec2> pts = table.ramp.points();
        Path2D p = new Path2D.Double();
        for (int i = 0; i < pts.size(); i++) {
            Vec2 a = pts.get(Math.max(0, i - 1)), b = pts.get(Math.min(pts.size() - 1, i + 1));
            Vec2 d = b.sub(a);
            Vec2 q = pts.get(i).add(new Vec2(-d.y(), d.x()).scale(offset / d.length()));
            if (i == 0) p.moveTo(q.x(), q.y());
            else p.lineTo(q.x(), q.y());
        }
        return p;
    }

    /** Рампа — как деревянные «американские горки» со шпалами и красными рельсами. */
    private void paintRamp(Graphics2D g, int v) {
        Path2D band = new Path2D.Double(rampEdge(17));
        List<double[]> rightPts = new ArrayList<>();
        for (PathIterator it = rampEdge(-17).getPathIterator(null); !it.isDone(); it.next()) {
            double[] c = new double[6];
            it.currentSegment(c);
            rightPts.add(c);
        }
        for (int i = rightPts.size() - 1; i >= 0; i--) band.lineTo(rightPts.get(i)[0], rightPts.get(i)[1]);
        band.closePath();
        g.setColor(new Color(60, 30, 10, 90));
        g.fill(AffineTransform.getTranslateInstance(9, 11).createTransformedShape(band));

        for (double d = 6; d < table.ramp.length; d += 15) {
            Vec2 p = table.ramp.pointAt(d), dir = table.ramp.directionAt(d);
            AffineTransform tx = AffineTransform.getTranslateInstance(p.x(), p.y());
            tx.rotate(Math.atan2(dir.y(), dir.x()));
            Shape tie = tx.createTransformedShape(new Rectangle2D.Double(-3.5, -19, 7, 38));
            Toon.toon(g, tie, new Color(168, 104, 56), 1.6f, v + (int) d);
        }
        for (Path2D edge : new Path2D[]{rampEdge(13), rampEdge(-13)}) {
            Shape w = Toon.wobble(edge, 0.6, v);
            g.setStroke(Render.round(8f));
            g.setColor(INK);
            g.draw(w);
            g.setStroke(Render.round(4.5f));
            g.setColor(RED);
            g.draw(w);
            g.setStroke(Render.round(1.4f));
            g.setColor(new Color(255, 190, 170));
            g.draw(AffineTransform.getTranslateInstance(-0.8, -0.8).createTransformedShape(w));
        }

        AffineTransform old = g.getTransform();
        g.translate(545, 505);
        g.rotate(Math.PI / 2);
        Toon.toon(g, new RoundRectangle2D.Double(-58, -11, 116, 22, 8, 8), MUSTARD, 2f, v);
        Toon.titleText(g, Render.centeredText(g, "HIGH ROLLER", Toon.title(13), 0, 5), CREAM, RED, 1.2f, v);
        g.setTransform(old);
    }

    private void drawRampBulbs(Graphics2D g, Game game) {
        boolean busy = false;
        for (Ball b : game.balls) busy |= b.onRamp();
        int chase = (int) (game.time * (busy ? 24 : 8));
        for (int i = 0; i < rampBulbs.size(); i++) {
            Vec2 p = rampBulbs.get(i);
            Toon.bulb(g, p.x(), p.y(), 3,
                    !game.tilted && (game.gameOver || (i + chase) % 4 == 0 || game.eventFlash > 0 && chase % 2 == 0));
        }
    }

    // ================================================================ лампы

    private void drawLamps(Graphics2D g, Game game, int boil) {
        boolean attract = game.gameOver;
        boolean dark = game.tilted;
        int beat = (int) (game.time * 6);

        boolean lanesBlink = game.lanesFlash > 0 && ((int) (game.time * 5)) % 2 == 0;
        for (int i = 0; i < table.rollovers.size(); i++) {
            Table.Rollover r = table.rollovers.get(i);
            boolean skill = game.skillLane == i && beat % 2 == 0;
            boolean lit = attract ? (beat + i) % 3 == 0 : !dark && ((r.lit && !lanesBlink) || skill);
            lamp(g, r.center.x(), r.center.y(), 11, skill ? TEAL : MUSTARD, lit, "7", boil);
        }

        for (int i = 0; i < 4; i++) {
            boolean lit = attract ? (beat + i) % 4 == 0 : !dark && game.multiplier >= i + 2;
            lamp(g, 230 + 40 * i, 700, 13, MULTIPLIER_COLORS[i], lit, (i + 2) + "X", boil);
        }

        boolean jackpotLit = attract ? beat % 2 == 0 : !dark && (game.multiball ? beat % 2 == 0 : game.eventFlash > 0);
        Render.sprite(g, "jackpotStar:" + jackpotLit + ":" + boil, Table.CENTER_X, 652, 62, 0, s -> {
            Shape st = star(0, -6, 24, 11, 5);
            if (jackpotLit) Render.glow(s, 0, -6, 62, new Color(255, 214, 110), 170);
            Toon.toon(s, st, jackpotLit ? new Color(255, 214, 80) : new Color(170, 120, 60), 2.2f, boil);
            Toon.eye(s, -5, -7, 6, 8, 0, 0.3, false);
            Toon.eye(s, 5, -7, 6, 8, 0, 0.3, false);
            s.setColor(INK);
            s.fill(Render.centeredText(s, "JACKPOT", Toon.label(10), 0, 30));
        });

        int rampLit = game.ramps % Game.RAMPS_FOR_MULTIBALL;
        for (int i = 0; i < 3; i++) {
            boolean lit;
            if (attract || game.multiball) lit = (beat + i) % 3 == 0;
            else lit = !dark && (i >= 3 - rampLit || (i == 2 - rampLit && beat % 2 == 0));
            arrow(g, 480, 492 + 28 * i, 12, RED, lit, boil);
        }

        boolean save = !dark && (game.ballSaveTimer > 2 || (game.ballSaveTimer > 0 && beat % 2 == 0));
        boolean saveLit = attract ? beat % 2 == 0 : save;
        Render.sprite(g, "save:" + saveLit + ":" + boil, Table.CENTER_X, 985.5, 60, 0, s -> {
            RoundRectangle2D plate = new RoundRectangle2D.Double(-46, -9.5, 92, 19, 19, 19);
            if (saveLit) Render.glow(s, 0, 0, 60, new Color(160, 230, 120), 140);
            Toon.toon(s, plate, saveLit ? new Color(170, 220, 110) : new Color(110, 120, 80), 2f, boil);
            s.setColor(saveLit ? INK : new Color(230, 220, 190, 170));
            s.fill(Render.centeredText(s, "SHOOT AGAIN", Toon.title(11), 0, 4));
        });


        die(g, Table.SLOT_BODY.getMaxX() + 22, 525, game.diceHit[0] || attract && beat % 2 == 0, boil);
        die(g, Table.SLOT_BODY.getMinX() - 22, 525, game.diceHit[1] || attract && beat % 2 == 1, boil);

        for (int i = 0; i < 4; i++) {
            boolean lit;
            if (game.plungerHeld) lit = game.plungerCharge * 4 > i;
            else lit = game.ballOnPlunger() != null && beat % 5 == i;
            arrow(g, 575, 985 - 50 * i, 10, GREEN, lit, boil);
        }
    }

    /** Круглая лампа-вставка с подписью. */
    private static void lamp(Graphics2D g, double x, double y, double r, Color color, boolean lit, String label, int boil) {
        String key = "lamp:" + color.getRGB() + ":" + r + ":" + lit + ":" + label + ":" + boil;
        Render.sprite(g, key, x, y, r * 2.8, 0, s -> {
            if (lit) Render.glow(s, 0, 0, r * 2.8, warm(color), 170);
            s.setPaint(new RadialGradientPaint((float) (-r * 0.3), (float) (-r * 0.35), (float) (r * 1.3),
                    new float[]{0f, 1f},
                    new Color[]{lit ? new Color(255, 250, 225) : Render.shade(color, 0.6), lit ? warm(color) : Render.shade(color, 0.4)}));
            Shape disc = Render.circle(0, 0, r);
            s.fill(disc);
            Toon.ink(s, disc, 2.2f, boil);
            Font font = Toon.title((float) (r * (label.length() > 1 ? 0.95 : 1.4)));
            s.setColor(lit ? INK : new Color(250, 236, 200, 160));
            s.fill(Render.centeredText(s, label, font, 0, r * (label.length() > 1 ? 0.33 : 0.48)));
            if (lit) Toon.shine(s, -r * 0.45, -r * 0.45, r * 0.45, r * 0.28, -0.7);
        });
    }

    private static Color warm(Color c) {
        return new Color((c.getRed() + 255) / 2, (c.getGreen() + 220) / 2, (c.getBlue() + 120) / 2);
    }

    private static void arrow(Graphics2D g, double x, double y, double w, Color color, boolean lit, int boil) {
        Render.sprite(g, "arrow:" + color.getRGB() + ":" + w + ":" + lit + ":" + boil, x, y, w * 2.6, 0, s -> {
            Shape shape = polygon(-w, w * 0.45, 0, -w * 0.6, w, w * 0.45, w, w, 0, 0, -w, w);
            if (lit) Render.glow(s, 0, 2, w * 2.6 - 1, warm(color), 170);
            Toon.toon(s, shape, lit ? warm(color) : Render.shade(color, 0.5), 1.8f, boil);
        });
    }

    private static void die(Graphics2D g, double x, double y, boolean lit, int boil) {
        Render.sprite(g, "die:" + lit + ":" + boil, x, y, 32, 0, s -> {
            if (lit) Render.glow(s, 0, 0, 32, new Color(255, 240, 200), 160);
            Toon.toon(s, new RoundRectangle2D.Double(-11, -11, 22, 22, 7, 7), lit ? CREAM : TAN, 2f, boil);
            s.setColor(lit ? RED : new Color(110, 70, 40));
            for (double[] p : new double[][]{{-5, -5}, {5, 5}, {0, 0}, {5, -5}, {-5, 5}}) s.fill(Render.circle(p[0], p[1], 2.2));
        });
    }

    private void drawSlingLamps(Graphics2D g) {
        for (Table.Sling s : table.slings) {
            if (s.kicker().flash <= 0) continue;
            Shape lamp = slingLamp(s);
            Rectangle2D b = lamp.getBounds2D();
            Render.glow(g, b.getCenterX(), b.getCenterY(), 60, new Color(255, 200, 110), 190);
            g.setColor(new Color(255, 240, 190));
            g.fill(lamp);
        }
    }

    // ================================================================ рулетка и слот

    private void drawRoulette(Graphics2D g, Game game, int boil) {
        Roulette wheel = game.roulette;
        double cx = Table.ROULETTE_CENTER.x(), cy = Table.ROULETTE_CENTER.y(), r = Table.ROULETTE_RADIUS;

        Render.sprite(g, "wheelRim:" + boil, cx, cy, r + 10, 0, s -> {
            s.setColor(new Color(60, 30, 10, 90));
            s.fill(Render.circle(5, 7, r + 1));
            Toon.toon(s, Render.circle(0, 0, r), WOOD, 3f, boil);
        });
        Render.sprite(g, "wheelFace:" + boil, cx, cy, r, wheel.wheelAngle, s -> {
            for (int i = 0; i < pockets.length; i++) {
                int n = Roulette.WHEEL[i];
                s.setColor(n == 0 ? GREEN : Roulette.isRed(n) ? RED : new Color(46, 36, 34));
                s.fill(pockets[i]);
            }
            s.setStroke(Render.round(1f));
            s.setColor(new Color(250, 230, 190, 160));
            for (int i = 0; i < pockets.length; i++) {
                double a = i * Roulette.POCKET;
                s.draw(new Line2D.Double(Math.cos(a) * r * 0.6, Math.sin(a) * r * 0.6, Math.cos(a) * r * 0.86, Math.sin(a) * r * 0.86));
            }
            Toon.ink(s, Render.circle(0, 0, r * 0.86), 2f, boil);
            Toon.toon(s, Render.circle(0, 0, r * 0.58), MUSTARD, 2f, boil);
            for (int k = 0; k < 4; k++) {
                AffineTransform old = s.getTransform();
                s.rotate(k * Math.PI / 2);
                Toon.toon(s, new RoundRectangle2D.Double(12, -3, r * 0.44, 6, 6, 6), CREAM, 1.5f, boil + k);
                s.setTransform(old);
            }
        });

        int chase = (int) (game.time * (wheel.isSpinning() ? 20 : 5));
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8;
            Toon.bulb(g, cx + Math.cos(a) * (r + 6), cy + Math.sin(a) * (r + 6), 2.8, game.gameOver || (k + chase) % 4 == 0);
        }

        drawSaucerHole(g, table.rouletteSaucer, game, boil);

        if (wheel.ballVisible) {
            double br = r * wheel.ballRadius;
            double bx = cx + Math.cos(wheel.ballAngle) * br, by = cy + Math.sin(wheel.ballAngle) * br;
            Render.sprite(g, "rouletteBall", bx, by, 7, 0, s -> {
                Shape ball = Render.circle(0, 0, 4.2);
                s.setColor(CREAM);
                s.fill(ball);
                s.setStroke(Render.round(1.4f));
                s.setColor(INK);
                s.draw(ball);
            });
        }
    }

    private static void drawSaucerHole(Graphics2D g, Table.Saucer s, Game game, int boil) {
        double x = s.center.x(), y = s.center.y(), r = Table.SAUCER_RADIUS;
        boolean busy = s.held != null;
        if (!busy && s.type == Table.SaucerType.SLOT && game.multiball && ((int) (game.time * 6)) % 2 == 0) {
            Render.glow(g, x, y, 42, new Color(255, 214, 110), 190);
        }
        Render.sprite(g, "hole:" + busy + ":" + boil, x, y, r + 4, 0, sp -> {
            Shape hole = Render.circle(0, 0, r);
            Toon.toon(sp, new BasicStroke(5f).createStrokedShape(hole), busy ? CREAM : MUSTARD, 1.6f, boil);
            sp.setColor(new Color(30, 18, 14));
            sp.fill(Render.circle(0, 0, r - 2.5));
        });
    }

    private void drawSlotMachine(Graphics2D g, Game game, int boil) {
        Rectangle2D body = Table.SLOT_BODY;
        double x0 = body.getMinX(), y0 = body.getMinY(), w = body.getWidth(), h = body.getHeight();
        double cx = body.getCenterX(), cy = body.getCenterY();
        double wx = x0 + 11, wy = y0 + 36, cellW = 34, cellH = 44;

        Render.sprite(g, "slotBody:" + boil, cx, cy, 82, 0, s -> {
            s.translate(-cx, -cy);
            Shape shape = new RoundRectangle2D.Double(x0, y0, w, h, 26, 26);
            s.setColor(new Color(60, 30, 10, 100));
            s.fill(AffineTransform.getTranslateInstance(6, 8).createTransformedShape(shape));
            Toon.toon(s, shape, RED, 3f, boil);
            Toon.toon(s, new RoundRectangle2D.Double(wx - 5, wy - 4, 3 * cellW + 14, cellH + 8, 12, 12), MUSTARD, 2.2f, boil);
            for (int r = 0; r < 3; r++) {
                s.setColor(CREAM);
                s.fill(new RoundRectangle2D.Double(wx + r * (cellW + 2), wy, cellW, cellH, 6, 6));
            }
            Toon.shine(s, x0 + 16, y0 + 12, 14, 6, -0.4);
        });

        drawDicePlate(g, x0, y0 + 12, y0 + h - 12, table.dice[1], game.diceHit[1], boil);
        drawDicePlate(g, x0 + w, y0 + 12, y0 + h - 12, table.dice[0], game.diceHit[0], boil);

        // Слот-машина — персонаж: следит глазами за шаром, крутит ими во время вращения
        boolean spinning = game.slot.isSpinning();
        boolean happy = table.slotSaucer.ejectTimer > 0;
        double[] look = look(game, cx, y0 + 16);
        double lx = spinning ? Math.cos(game.time * 14) : look[0];
        double ly = spinning ? Math.sin(game.time * 14) : look[1];
        boolean blink = blink(game.time, 1);
        Toon.eye(g, cx - 22, y0 + 17, 20, 23, lx, ly, blink || happy);
        Toon.eye(g, cx + 22, y0 + 17, 20, 23, lx, ly, blink || happy || game.gameOver);
        g.setStroke(Render.round(2.5f));
        g.setColor(INK);
        double brow = spinning ? -3 : 0;
        g.draw(new Line2D.Double(cx - 32, y0 + 3 + brow, cx - 14, y0 + 1));
        g.draw(new Line2D.Double(cx + 14, y0 + 1, cx + 32, y0 + 3 + brow));

        int chase = (int) (game.time * (spinning ? 18 : 6));
        for (int k = 0; k < 7; k++) {
            Toon.bulb(g, x0 + 20 + k * (w - 40) / 6, y0 - 3, 3, game.gameOver || (k + chase) % 3 == 0);
        }

        Shape clip = g.getClip();
        for (int r = 0; r < 3; r++) {
            double rx = wx + r * (cellW + 2);
            g.clip(new Rectangle2D.Double(rx, wy, cellW, cellH));
            double pos = game.slot.reels[r];
            int base = (int) Math.floor(pos);
            double frac = pos - base;
            for (int k = -1; k <= 1; k++) {
                int symbol = Math.floorMod(base + k, SlotMachine.SYMBOL_COUNT);
                Render.sprite(g, "symbol:" + symbol, rx + cellW / 2, wy + cellH / 2 + (k - frac) * 30, 17, 0,
                        s -> drawSymbol(s, symbol));
            }
            g.setClip(clip);
        }

        Render.sprite(g, "slotGlass:" + boil, cx, cy, 82, 0, s -> {
            s.translate(-cx, -cy);
            for (int r = 0; r < 3; r++) {
                double rx = wx + r * (cellW + 2);
                s.setPaint(new GradientPaint(0, (float) wy, new Color(90, 50, 20, 130), 0, (float) (wy + 12), new Color(90, 50, 20, 0)));
                s.fill(new Rectangle2D.Double(rx, wy, cellW, 12));
                s.setPaint(new GradientPaint(0, (float) (wy + cellH - 12), new Color(90, 50, 20, 0), 0, (float) (wy + cellH), new Color(90, 50, 20, 130)));
                s.fill(new Rectangle2D.Double(rx, wy + cellH - 12, cellW, 12));
                Toon.ink(s, new RoundRectangle2D.Double(rx, wy, cellW, cellH, 6, 6), 2f, boil + r);
            }
        });
    }

    private static void drawDicePlate(Graphics2D g, double x, double y0, double y1, Table.Wall wall, boolean hit, int boil) {
        if (wall.flash > 0 || hit) Render.glow(g, x, (y0 + y1) / 2, 40, new Color(255, 240, 200), wall.flash > 0 ? 200 : 100);
        double half = (y1 - y0) / 2;
        Render.sprite(g, "dicePlate:" + boil, x, (y0 + y1) / 2, half + 4, 0, s -> {
            Toon.toon(s, new RoundRectangle2D.Double(-5, -half + 4, 10, half * 2 - 8, 5, 5), CREAM, 1.8f, boil);
            s.setColor(RED);
            for (int k = -1; k <= 1; k++) s.fill(Render.circle(0, k * 16, 2));
        });
    }

    private static void drawSymbol(Graphics2D g, int symbol) {
        switch (symbol) {
            case SlotMachine.SEVEN -> Toon.titleText(g, Render.centeredText(g, "7", Toon.title(28), 0, 10), RED, DARK_RED, 1.8f, 0);
            case SlotMachine.BAR -> {
                Toon.toon(g, new RoundRectangle2D.Double(-14, -7, 28, 14, 5, 5), INK, 0, 0);
                g.setColor(CREAM);
                g.fill(Render.centeredText(g, "BAR", Toon.title(9), 0, 3.5));
            }
            case SlotMachine.CHERRY -> {
                g.setStroke(Render.round(2f));
                g.setColor(INK);
                g.draw(new Line2D.Double(-5, 2, 2, -10));
                g.draw(new Line2D.Double(6, 1, 2, -10));
                Toon.toon(g, Render.circle(-5, 5, 5.5), RED, 1.6f, 0);
                Toon.toon(g, Render.circle(6, 4, 5.5), RED, 1.6f, 1);
                Toon.shine(g, -6.5, 3, 3, 2, -0.6);
                Toon.shine(g, 4.5, 2, 3, 2, -0.6);
            }
            case SlotMachine.BELL -> {
                Path2D bell = new Path2D.Double();
                bell.moveTo(-11, 7);
                bell.quadTo(-9, 2, -8, -3);
                bell.quadTo(0, -17, 8, -3);
                bell.quadTo(9, 2, 11, 7);
                bell.closePath();
                Toon.toon(g, bell, MUSTARD, 1.8f, 0);
                Toon.toon(g, Render.circle(0, 8.5, 2.8), MUSTARD, 1.4f, 1);
            }
            case SlotMachine.LEMON -> Toon.toon(g, new Ellipse2D.Double(-11, -7.5, 22, 15), new Color(246, 214, 70), 1.8f, 0);
            default -> {
                Toon.toon(g, polygon(0, -11, 10, 0, 0, 11, -10, 0), TEAL, 1.8f, 0);
                Toon.shine(g, -3, -4, 4, 2.5, -0.8);
            }
        }
    }

    // ================================================================ ярмарочные аттракционы

    /** Будка тира: полосатый навес, полка и волны, по которым едут утки. */
    private static void gallery(Graphics2D g, int v) {
        double x0 = 358, x1 = 470;
        // Навес с фестонами
        Shape awning = new Rectangle2D.Double(x0, 112, x1 - x0, 14);
        g.setColor(CREAM);
        g.fill(awning);
        g.setColor(RED);
        for (double x = x0; x < x1; x += 16) g.fill(new Rectangle2D.Double(x, 112, 8, 14));
        Path2D scallops = new Path2D.Double();
        scallops.moveTo(x0, 126);
        for (double x = x0; x < x1; x += 14) scallops.quadTo(x + 7, 136, Math.min(x1, x + 14), 126);
        scallops.lineTo(x0, 126);
        g.setColor(RED);
        g.fill(scallops);
        Toon.ink(g, awning, 2f, v);
        Toon.ink(g, scallops, 1.6f, v);
        // Полка и волны перед утками
        Toon.toon(g, new Rectangle2D.Double(x0, 160, x1 - x0, 8), new Color(168, 104, 56), 2f, v);
        Path2D waves = new Path2D.Double();
        waves.moveTo(x0, 178);
        for (double x = x0; x < x1; x += 16) waves.quadTo(x + 8, 164, Math.min(x1, x + 16), 178);
        waves.lineTo(x1, 184);
        waves.lineTo(x0, 184);
        waves.closePath();
        Toon.toon(g, waves, new Color(96, 150, 200), 1.8f, v);
    }

    /** Нарисованное на поле основание смерча. */
    private static void vortexBase(Graphics2D g, int v) {
        Vec2 c = Table.VORTEX_CENTER;
        double r = Table.VORTEX_RADIUS;
        g.setColor(new Color(120, 190, 200, 110));
        g.fill(Render.circle(c.x(), c.y(), r));
        Toon.ink(g, Render.circle(c.x(), c.y(), r), 2.5f, v);
    }

    private static void drawVortex(Graphics2D g, Game game, int boil) {
        Vec2 c = Table.VORTEX_CENTER;
        double r = Table.VORTEX_RADIUS;
        Render.sprite(g, "vortex:" + boil, c.x(), c.y(), r, game.time * 4, s -> {
            for (int arm = 0; arm < 3; arm++) {
                Path2D spiral = new Path2D.Double();
                for (int k = 0; k <= 30; k++) {
                    double t = k / 30.0;
                    double a = arm * 2 * Math.PI / 3 + t * 4.2;
                    double rr = 4 + t * (r - 7);
                    if (k == 0) spiral.moveTo(Math.cos(a) * rr, Math.sin(a) * rr);
                    else spiral.lineTo(Math.cos(a) * rr, Math.sin(a) * rr);
                }
                s.setStroke(Render.round(5f));
                s.setColor(INK);
                s.draw(spiral);
                s.setStroke(Render.round(2.6f));
                s.setColor(new Color(235, 250, 250));
                s.draw(spiral);
            }
            Toon.toon(s, Render.circle(0, 0, 6), TEAL, 1.6f, boil);
        });
    }

    private void drawStars(Graphics2D g, Game game, int boil) {
        for (Table.Rollover r : table.stars) {
            boolean lit = !game.tilted && (r.lit || game.gameOver && ((int) (game.time * 3)) % 2 == 0);
            Render.sprite(g, "starLamp:" + lit + ":" + boil, r.center.x(), r.center.y(), 30, 0, s -> {
                if (lit) Render.glow(s, 0, 0, 30, new Color(255, 214, 110), 160);
                Toon.toon(s, star(0, 0, 12, 5.5, 5), lit ? new Color(255, 214, 80) : TAN, 2f, boil);
                if (lit) Toon.shine(s, -3, -4, 4, 2.5, -0.6);
            });
        }
    }

    private void drawBells(Graphics2D g, Game game, int boil) {
        for (Table.Bell bell : table.bells) {
            boolean lit = bell.lit && !game.tilted || game.gameOver && ((int) (game.time * 2)) % 2 == 0;
            double swing = Math.sin(game.time * 30) * 0.4 * (bell.flash / 0.6);
            if (lit) Render.glow(g, bell.center.x(), bell.center.y(), 34, new Color(255, 214, 110), 150);
            Render.sprite(g, "bell:" + lit + ":" + boil, bell.center.x(), bell.center.y(), 22, swing, s -> {
                Path2D shape = new Path2D.Double();
                shape.moveTo(-14, 10);
                shape.quadTo(-12, 4, -10, -2);
                shape.quadTo(0, -20, 10, -2);
                shape.quadTo(12, 4, 14, 10);
                shape.closePath();
                Toon.toon(s, shape, lit ? new Color(255, 206, 70) : new Color(190, 150, 80), 2f, boil);
                Toon.toon(s, Render.circle(0, 12, 3.5), lit ? new Color(255, 206, 70) : new Color(190, 150, 80), 1.5f, boil + 1);
                Toon.shine(s, -5, -3, 4, 7, 0.3);
            });
        }
    }

    private void drawDucks(Graphics2D g, Game game, int boil) {
        for (int i = 0; i < table.ducks.size(); i++) {
            Table.Duck duck = table.ducks.get(i);
            Vec2 p = Table.duckPosition(i, game.time);
            if (duck.flash > 0) Render.glow(g, p.x(), p.y(), 28, new Color(255, 240, 200), 180);
            if (duck.down) {
                // Сбитая утка лежит на полке
                Render.sprite(g, "duckDown:" + boil, p.x(), p.y() + 8, 16, 0, s ->
                        Toon.toon(s, new Ellipse2D.Double(-12, -4, 24, 8), new Color(200, 170, 90), 1.6f, boil));
                continue;
            }
            double bob = Math.sin(game.time * 6 + i * 2) * 1.5;
            Render.sprite(g, "duck:" + boil, p.x(), p.y() + bob, 18, 0, s -> {
                Toon.toon(s, new Ellipse2D.Double(-11, -3, 20, 13), new Color(252, 214, 70), 1.8f, boil);
                Toon.toon(s, Render.circle(5, -7, 6.5), new Color(252, 214, 70), 1.8f, boil + 1);
                Toon.toon(s, polygon(10, -8, 17, -6, 10, -4), new Color(236, 120, 40), 1.4f, boil + 2);
                Toon.eye(s, 6, -9, 4.5, 5.5, 0.6, 0, false);
                Toon.toon(s, polygon(-10, 0, -15, -6, -7, -3), new Color(252, 214, 70), 1.4f, boil + 3);
            });
        }
    }

    /** Волшебный цилиндр вокруг лунки: поля, лента, палочка. */
    private static void drawHat(Graphics2D g, Game game, int boil) {
        Vec2 c = Table.HAT_CENTER;
        boolean busy = game.table.hatSaucer.held != null;
        Render.sprite(g, "hat:" + busy + ":" + boil, c.x(), c.y(), 46, 0, s -> {
            s.setColor(new Color(60, 30, 10, 100));
            s.fill(new Ellipse2D.Double(-34, -10, 72, 36));
            Toon.toon(s, new Ellipse2D.Double(-38, -16, 76, 34), new Color(52, 40, 60), 2.5f, boil);
            Toon.toon(s, new BasicStroke(6f).createStrokedShape(Render.circle(0, 0, Table.SAUCER_RADIUS + 3)), RED, 1.6f, boil);
            s.setColor(new Color(20, 12, 16));
            s.fill(Render.circle(0, 0, Table.SAUCER_RADIUS));
            Toon.ink(s, Render.circle(0, 0, Table.SAUCER_RADIUS), 1.8f, boil);
            // Палочка
            AffineTransform old = s.getTransform();
            s.translate(26, -24);
            s.rotate(-0.7);
            Toon.toon(s, new RoundRectangle2D.Double(-3, -14, 6, 28, 3, 3), INK, 0, boil);
            Toon.toon(s, new RoundRectangle2D.Double(-3, -14, 6, 7, 3, 3), CREAM, 1.4f, boil);
            s.setTransform(old);
            Toon.toon(s, star(-30, -22, 6, 2.5, 4), MUSTARD, 1.2f, boil);
            Toon.toon(s, star(34, 6, 5, 2, 4), MUSTARD, 1.2f, boil + 1);
        });
    }

    /** Кролик выглядывает из цилиндра, пока шар в лунке и приз уже объявлен. */
    private static void drawRabbit(Graphics2D g, Game game, int boil) {
        if (game.table.hatSaucer.held == null || game.hatReveal > 0) return;
        Vec2 c = Table.HAT_CENTER;
        Render.sprite(g, "rabbit:" + boil, c.x(), c.y() - 16, 30, 0, s -> {
            Toon.toon(s, new Ellipse2D.Double(-12, -32, 9, 26), CREAM, 1.8f, boil);
            Toon.toon(s, new Ellipse2D.Double(3, -32, 9, 26), CREAM, 1.8f, boil + 1);
            s.setColor(new Color(240, 170, 170));
            s.fill(new Ellipse2D.Double(-9.5, -28, 4, 17));
            s.fill(new Ellipse2D.Double(5.5, -28, 4, 17));
            Toon.toon(s, Render.circle(0, 0, 13), CREAM, 2f, boil);
            Toon.eye(s, -5, -2, 5, 7, 0, 0.2, false);
            Toon.eye(s, 5, -2, 5, 7, 0, 0.2, false);
            s.setColor(new Color(230, 120, 130));
            s.fill(new Ellipse2D.Double(-2, 4, 4, 3));
        });
    }

    // ================================================================ мишени и механизмы

    private void drawCards(Graphics2D g, Game game, int boil) {
        String[] suits = {"♥", "♠", "♦", "♣", "♠"};
        for (Table.Wall w : table.cards) {
            double cy = (w.a.y() + w.b.y()) / 2;
            boolean down = !w.active;
            boolean attract = game.gameOver && ((int) (game.time * 6) + w.index) % 5 == 0;
            boolean lit = (down || attract) && !game.tilted;
            boolean red = w.index % 2 == 0;
            String name = Game.CARD_NAMES[w.index], suit = suits[w.index];
            Render.sprite(g, "card:" + w.index + ":" + lit + ":" + boil, 57, cy, 32, 0, s -> {
                RoundRectangle2D card = new RoundRectangle2D.Double(-11, -14, 22, 28, 5, 5);
                if (lit) Render.glow(s, 0, 0, 32, new Color(255, 240, 200), 150);
                Toon.toon(s, card, lit ? CREAM : TAN, 1.8f, boil);
                s.setColor(lit ? (red ? RED : INK) : new Color(120, 80, 50));
                s.fill(Render.centeredText(s, name, Toon.card(11), 0, 1));
                s.fill(Render.centeredText(s, suit, new Font(Font.SANS_SERIF, Font.BOLD, 9), 0, 11));
            });

            if (down) {
                g.setColor(new Color(40, 20, 10, 170));
                g.fill(new Rectangle2D.Double(26, w.a.y(), 8, w.b.y() - w.a.y()));
            } else {
                if (w.flash > 0) Render.glow(g, 30, cy, 30, new Color(255, 240, 200), 200);
                Render.sprite(g, "cardTarget:" + red + ":" + boil, 30, cy, 20, 0, s -> {
                    Toon.toon(s, new RoundRectangle2D.Double(-5, -15, 10, 30, 4, 4), CREAM, 1.8f, boil);
                    s.setColor(red ? RED : INK);
                    s.fill(new Rectangle2D.Double(2, -12, 2, 24));
                });
            }
        }
    }

    private void drawSpinner(Graphics2D g) {
        Table.Spinner sp = table.spinner;
        double half = 7 * Math.abs(Math.cos(sp.angle)) + 1;
        Rectangle2D plate = new Rectangle2D.Double(sp.left + 6, sp.y - half, sp.right - sp.left - 12, half * 2);
        g.setStroke(Render.round(2f));
        g.setColor(INK);
        g.draw(new Line2D.Double(sp.left + 2, sp.y, sp.right - 2, sp.y));
        g.setColor(MUSTARD);
        g.fill(plate);
        g.setColor(RED);
        g.fill(new Rectangle2D.Double(sp.left + 14, sp.y - half * 0.4, sp.right - sp.left - 28, half * 0.8));
        g.setStroke(Render.round(1.8f));
        g.setColor(INK);
        g.draw(plate);
    }

    /**
     * Кикбэк — боксёрская перчатка под флипперами. Заряжена — красная и светится;
     * ловит шар, падающий между флипперами, и выбивает его вверх.
     */
    private static void drawKickback(Graphics2D g, Game game, int boil) {
        double x = Table.CENTER_X, rest = 1016;
        double punch = game.kickbackFlash > 0 ? 46 * (game.kickbackFlash / 0.5) : 0;
        double y = rest - punch;
        if (punch > 0) {
            Path2D spring = new Path2D.Double();
            spring.moveTo(x, rest + 8);
            int coils = 5;
            for (int i = 1; i <= coils; i++) spring.lineTo(i % 2 == 0 ? x - 8 : x + 8, rest + 8 - (rest + 8 - y - 10) * i / coils);
            spring.lineTo(x, y + 10);
            g.setStroke(Render.round(4f));
            g.setColor(INK);
            g.draw(spring);
            g.setStroke(Render.round(2f));
            g.setColor(new Color(200, 190, 170));
            g.draw(spring);
        }
        boolean lit = game.kickbackLit && !game.tilted || game.kickbackFlash > 0;
        if (lit) Render.glow(g, x, y - 2, 34, new Color(255, 200, 120), 120);
        Render.sprite(g, "glove:" + lit + ":" + boil, x, y, 22, 0, s -> {
            Color glove = lit ? RED : new Color(140, 110, 90);
            Toon.toon(s, new RoundRectangle2D.Double(-8, 4, 16, 8, 3, 3), CREAM, 1.6f, boil);
            Toon.toon(s, new Ellipse2D.Double(-12, -12, 24, 19), glove, 2f, boil);
            Toon.toon(s, new Ellipse2D.Double(6, -5, 8, 9), glove, 1.6f, boil + 1);
            Toon.shine(s, -5, -7, 7, 3.5, -0.4);
        });
    }

    // ================================================================ бамперы-персонажи

    private static void drawChip(Graphics2D g, Game game, Table.Bumper b, int index, int boil) {
        double f = Math.max(0, b.flash / 0.2);
        double sx = 1 + 0.22 * f, sy = 1 - 0.18 * f;
        double x = b.center.x(), y = b.center.y(), r = b.radius;
        Render.sprite(g, "chipShadow:" + r, x + 5, y + 7, r + 3, 0, s -> {
            s.setColor(new Color(60, 30, 10, 100));
            s.fill(Render.circle(0, 0, r + 1));
        });
        boolean superOn = game.superBumpersTimer > 0;
        if (superOn) {
            double pulse = 0.85 + 0.15 * Math.sin(game.time * 10 + index);
            Render.glow(g, x, y, r * 2.4 * pulse, new Color(255, 214, 110), 190);
        }
        if (f > 0) Render.glow(g, x, y, r * 2.6, warm(b.color), 200);
        if (superOn || game.bumperLit[index]) {
            Render.sprite(g, "chipRing:" + r + ":" + boil, x, y, r + 9, 0, s ->
                    Toon.toon(s, new BasicStroke(5f).createStrokedShape(Render.circle(0, 0, r + 5)), MUSTARD, 1.5f, boil));
        }
        Render.sprite(g, "chipBody:" + b.color.getRGB() + ":" + r + ":" + boil, x, y, r + 3, b.rotation, sx, sy, s -> {
            Toon.toon(s, Render.circle(0, 0, r), b.color, 0, boil);
            for (int k = 0; k < 6; k++) {
                AffineTransform old = s.getTransform();
                s.rotate(k * Math.PI / 3);
                Toon.toon(s, new RoundRectangle2D.Double(r * 0.66, -r * 0.14, r * 0.3, r * 0.28, 4, 4), CREAM, 1.2f, boil + k);
                s.setTransform(old);
            }
            Toon.ink(s, Render.circle(0, 0, r), 2.6f, boil);
            Toon.toon(s, Render.circle(0, 0, r * 0.6), CREAM, 1.8f, boil + 3);
        });

        if (superOn) {
            for (int k = 0; k < 4; k++) {
                double a = game.time * 3 + k * Math.PI / 2 + index;
                double px = x + Math.cos(a) * (r + 10), py = y + Math.sin(a) * (r + 10);
                Shape spark = star(px, py, 5, 2, 4);
                g.setColor(CREAM);
                g.fill(spark);
                g.setStroke(Render.round(1.2f));
                g.setColor(INK);
                g.draw(spark);
            }
        }

        // Лицо остаётся вертикальным, пока фишка вращается
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.scale(sx, sy);
        double[] look = look(game, x, y);
        boolean hit = f > 0;
        boolean blink = !hit && blink(game.time, index + 2);
        double ew = r * 0.24, eh = r * 0.32;
        Toon.eye(g, -r * 0.2, -r * 0.12, ew, eh, look[0], look[1], blink);
        Toon.eye(g, r * 0.2, -r * 0.12, ew, eh, look[0], look[1], blink);
        g.setStroke(Render.round(1.8f));
        g.setColor(INK);
        double tilt = index % 2 == 0 ? 2 : -2;
        g.draw(new Line2D.Double(-r * 0.34, -r * 0.34 - tilt, -r * 0.1, -r * 0.34 + tilt));
        g.draw(new Line2D.Double(r * 0.1, -r * 0.34 + tilt, r * 0.34, -r * 0.34 - tilt));
        if (hit) {
            g.fill(new Ellipse2D.Double(-r * 0.12, r * 0.12, r * 0.24, r * 0.26));
            g.setColor(new Color(220, 90, 80));
            g.fill(new Ellipse2D.Double(-r * 0.07, r * 0.25, r * 0.14, r * 0.1));
        } else {
            Path2D smile = new Path2D.Double();
            smile.moveTo(-r * 0.22, r * 0.16);
            smile.quadTo(0, r * 0.4, r * 0.22, r * 0.16);
            g.draw(smile);
        }
        g.setTransform(old);
    }

    /** Направление взгляда на ближайший шар: вектор длиной до 1. */
    private static double[] look(Game game, double x, double y) {
        Ball best = null;
        double bestDist = Double.MAX_VALUE;
        for (Ball b : game.balls) {
            double d = Math.hypot(b.pos.x() - x, b.pos.y() - y);
            if (d < bestDist) {
                bestDist = d;
                best = b;
            }
        }
        if (best == null) return new double[]{0, 0.4};
        double dx = best.pos.x() - x, dy = best.pos.y() - y, len = Math.max(1, Math.hypot(dx, dy));
        double k = Math.min(1, len / 50);
        return new double[]{dx / len * k, dy / len * k};
    }

    /** Персонажи моргают раз в несколько секунд, каждый в свой момент. */
    private static boolean blink(double time, int who) {
        double period = 3.1 + who * 0.7;
        return (time + who * 1.3) % period < 0.12;
    }

    // ================================================================ флипперы, плунжер, шар

    private void drawFlipper(Graphics2D g, Flipper f, int boil) {
        double half = f.length + 12;
        Render.sprite(g, "flipperShadow", f.pivot.x() + 4, f.pivot.y() + 6, half, f.angle, s -> {
            s.setColor(new Color(60, 30, 10, 110));
            s.fill(flipperShape);
        });
        Render.sprite(g, "flipper:" + boil, f.pivot.x(), f.pivot.y(), half, f.angle, s -> {
            s.setColor(CREAM);
            s.fill(flipperShape);
            Area shade = new Area(flipperShape);
            shade.subtract(new Area(AffineTransform.getTranslateInstance(0, -2.5).createTransformedShape(flipperShape)));
            s.setColor(new Color(214, 184, 140));
            s.fill(shade);
            s.setStroke(Render.round(3f));
            s.setColor(RED);
            s.draw(new Line2D.Double(10, 0, f.length - 8, 0));
            Toon.ink(s, flipperShape, 2.6f, boil);
            Toon.toon(s, Render.circle(0, 0, 4.5), MUSTARD, 1.6f, boil);
        });
    }

    private static void drawPlunger(Graphics2D g, Game game, int boil) {
        double x = (Table.LANE_LEFT + Table.LANE_RIGHT) / 2;
        double top = game.plungerY();
        Path2D spring = new Path2D.Double();
        spring.moveTo(x, top + 8);
        int coils = 5;
        double step = (Table.HEIGHT - top - 8) / coils;
        for (int i = 0; i < coils; i++) {
            spring.lineTo(x + 9, top + 8 + (i + 0.5) * step);
            spring.lineTo(x - 9, top + 8 + (i + 1) * step);
        }
        g.setStroke(Render.round(3.5f));
        g.setColor(INK);
        g.draw(spring);
        g.setStroke(Render.round(1.8f));
        g.setColor(new Color(210, 200, 180));
        g.draw(spring);
        Render.sprite(g, "plungerHead:" + boil, x, top + 4, 16, 0, s ->
                Toon.toon(s, new RoundRectangle2D.Double(-11, -4, 22, 9, 5, 5), RED, 1.8f, boil));
    }

    /** Мультяшный шар с глазами, которые смотрят туда, куда он катится. */
    private static void drawBall(Graphics2D g, Ball ball, double scale, boolean alive, int boil) {
        double x = ball.pos.x(), y = ball.pos.y(), r = ball.radius * scale;
        double lift = scale > 1 ? 4 : 0;
        Render.sprite(g, "ballShadow:" + scale, x + 4 + lift, y + 6 + lift, r + 3, 0, s -> {
            s.setColor(new Color(60, 30, 10, 110));
            s.fill(Render.circle(0, 0, r));
        });
        Render.sprite(g, "ball:" + scale + ":" + alive + ":" + boil, x, y, r + 3, 0, s -> {
            Toon.toon(s, Render.circle(0, 0, r), alive ? CREAM : new Color(170, 150, 120), 2.2f, boil);
            Toon.shine(s, -r * 0.4, -r * 0.45, r * 0.5, r * 0.3, -0.7);
        });
        if (!alive) return;
        double speed = ball.vel.length();
        double lx = speed > 30 ? ball.vel.x() / speed : 0, ly = speed > 30 ? ball.vel.y() / speed : 0.3;
        double ex = lx * r * 0.18, ey = ly * r * 0.18;
        boolean scared = speed > 1400 || ball.pos.y() > 960;
        double ew = r * 0.36, eh = r * (scared ? 0.56 : 0.48);
        Toon.eye(g, x - r * 0.25 + ex, y - r * 0.08 + ey, ew, eh, lx, ly, false);
        Toon.eye(g, x + r * 0.25 + ex, y - r * 0.08 + ey, ew, eh, lx, ly, false);
    }

    // ================================================================ эффекты и оверлеи

    private static void drawParticles(Graphics2D g, Game game) {
        for (Game.Particle p : game.particles) {
            double fade = Math.min(1, p.life / p.maxLife * 2);
            int a = (int) (255 * fade);
            if (p.coin) {
                double w = 7 * Math.abs(Math.cos(p.spin)) + 1;
                Ellipse2D coin = new Ellipse2D.Double(p.x - w, p.y - 7, w * 2, 14);
                g.setColor(Render.alpha(MUSTARD, a));
                g.fill(coin);
                g.setStroke(Render.round(1.4f));
                g.setColor(Render.alpha(INK, a));
                g.draw(coin);
                g.setColor(Render.alpha(CREAM, (int) (a * 0.8)));
                g.fill(new Ellipse2D.Double(p.x - w * 0.4, p.y - 4, w * 0.5, 3.5));
            } else {
                Shape spark = star(p.x, p.y, 3.5, 1.4, 4);
                g.setColor(Render.alpha(CREAM, a));
                g.fill(spark);
                g.setStroke(Render.round(0.9f));
                g.setColor(Render.alpha(INK, a));
                g.draw(spark);
            }
        }
    }

    private static void drawPopups(Graphics2D g, Game game, int boil) {
        Composite old = g.getComposite();
        AffineTransform oldTx = g.getTransform();
        for (Game.Popup p : game.popups) {
            float alpha = (float) Math.max(0, Math.min(1, (1.2 - p.age) / 0.5));
            double pop = p.age < 0.12 ? 0.6 + p.age / 0.12 * 0.55 : 1.15 - Math.min(0.15, (p.age - 0.12));
            Font font = Toon.title(p.size * (float) pop);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.translate(p.x, p.y);
            g.rotate(((p.text.hashCode() & 7) - 3.5) * 0.025);
            Color fill = p.color.getRed() > 240 && p.color.getGreen() > 240 ? CREAM : MUSTARD;
            Toon.titleText(g, Render.centeredText(g, p.text, font, 0, 0), fill, RED, Math.max(1.5f, p.size / 10), boil);
            g.setTransform(oldTx);
        }
        g.setComposite(old);
    }

    private static void drawOverlay(Graphics2D g, Game game, int boil) {
        if (game.tilted) {
            AffineTransform old = g.getTransform();
            g.translate(Table.CENTER_X, 540);
            g.rotate(-0.12);
            Toon.titleText(g, Render.centeredText(g, "TILT!", Toon.title(120), 0, 40), RED, DARK_RED, 5f, boil);
            g.setTransform(old);
            return;
        }
        if (game.paused) {
            intertitle(g, "PAUSED", "P — resume", 540, boil);
            return;
        }
        if (!game.gameOver) return;

        // «Диафрагма»: круг сжимается вокруг подмигивающей слот-машины, как в конце старого мультфильма
        double t = game.time - game.gameOverTime;
        double k = Math.min(1, t / 1.4);
        double ease = 1 - Math.pow(1 - k, 3);
        double radius = 760 - (760 - 105) * ease;
        Area dark = new Area(FULL);
        dark.subtract(new Area(Render.circle(Table.CENTER_X, 528, radius)));
        g.setColor(new Color(20, 12, 8));
        g.fill(dark);
        if (t > 1.2) {
            Toon.titleText(g, Render.centeredText(g, "GAME OVER", Toon.title(46), Table.CENTER_X, 330), CREAM, RED, 3.5f, boil);
            Composite old = g.getComposite();
            if (((int) (game.time * 2)) % 2 == 1) g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            g.setColor(CREAM);
            g.fill(Render.centeredText(g, "Press Enter for a new game", Toon.card(24), Table.CENTER_X, 760));
            g.setComposite(old);
        }
    }

    /** Карточка-титр немого кино: тёмный фон, двойная рамка, курсив. */
    private static void intertitle(Graphics2D g, String title, String hint, double cy, int boil) {
        g.setColor(new Color(20, 12, 8, 150));
        g.fill(FULL);
        RoundRectangle2D card = new RoundRectangle2D.Double(70, cy - 110, Table.WIDTH - 140, 200, 18, 18);
        g.setColor(new Color(22, 16, 12));
        g.fill(card);
        g.setStroke(Render.round(3f));
        g.setColor(CREAM);
        g.draw(Toon.wobble(new RoundRectangle2D.Double(82, cy - 98, Table.WIDTH - 164, 176, 14, 14), 0.6, boil));
        g.setStroke(Render.round(1.2f));
        g.draw(Toon.wobble(new RoundRectangle2D.Double(90, cy - 90, Table.WIDTH - 180, 160, 10, 10), 0.6, boil));
        g.fill(Render.centeredText(g, title, Toon.card(52), Table.CENTER_X, cy));
        g.fill(Render.centeredText(g, hint, Toon.card(20), Table.CENTER_X, cy + 48));
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

    private static Path2D path(List<Vec2> pts) {
        Path2D p = new Path2D.Double();
        p.moveTo(pts.get(0).x(), pts.get(0).y());
        for (int i = 1; i < pts.size(); i++) p.lineTo(pts.get(i).x(), pts.get(i).y());
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
