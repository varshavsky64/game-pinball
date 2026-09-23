package pinball;

import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/** Геометрия стола: стены, бамперы, мишени, лунки, рампа, флипперы. */
final class Table {
    static final int WIDTH = 600;
    static final int HEIGHT = 1080;
    /** Ось симметрии игрового поля (между левой стеной x=20 и стенкой желоба x=560). */
    static final double CENTER_X = 290;

    static final double LANE_LEFT = 560;
    static final double LANE_RIGHT = 590;
    static final double PLUNGER_REST_Y = 1040;
    static final double PLUNGER_MAX_PULL = 30;

    static final Rectangle2D RAMP_MOUTH = new Rectangle2D.Double(452, 405, 56, 63);
    /** Кикбэк стоит под флипперами и выбивает шар, падающий в центральный дренаж. */
    static final Rectangle2D KICKBACK_ZONE = new Rectangle2D.Double(CENTER_X - 45, 985, 90, 45);
    static final Rectangle2D SLOT_BODY = new Rectangle2D.Double(225, 480, 130, 90);
    static final Vec2 ROULETTE_CENTER = new Vec2(475, 260);
    static final double ROULETTE_RADIUS = 50;
    static final double SAUCER_RADIUS = 14;
    /** Высота, на которой направляющие к флипперам примыкают к боковым стенам. */
    static final double GUIDE_TOP_Y = 800;
    static final double BELL_RADIUS = 13;
    static final double DUCK_RADIUS = 11;
    static final double DUCK_Y = 150;
    static final Vec2 VORTEX_CENTER = new Vec2(440, 660);
    static final double VORTEX_RADIUS = 42;
    static final Vec2 HAT_CENTER = new Vec2(145, 620);

    enum Kind { PLAIN, SLING, CARD, DICE, GATE }

    static final class Wall {
        final Vec2 a, b;
        final double thickness;
        final double restitution;
        final Kind kind;
        final int index;
        /** Для односторонних воротец: шар сталкивается, только находясь с этой стороны. */
        Vec2 oneWayNormal;
        boolean active = true;
        double flash;

        Wall(Vec2 a, Vec2 b, double thickness, double restitution, Kind kind, int index) {
            this.a = a;
            this.b = b;
            this.thickness = thickness;
            this.restitution = restitution;
            this.kind = kind;
            this.index = index;
        }
    }

    static final class Bumper {
        final Vec2 center;
        final double radius;
        final Color color;
        final String label;
        double flash;
        double rotation;

        Bumper(Vec2 center, double radius, Color color, String label) {
            this.center = center;
            this.radius = radius;
            this.color = color;
            this.label = label;
        }
    }

    static final class Rollover {
        final Vec2 center;
        final double radius = 12;
        boolean lit;
        boolean ballInside;

        Rollover(Vec2 center) {
            this.center = center;
        }
    }

    record Sling(Vec2 top, Vec2 bottom, Vec2 corner, Wall kicker) {}

    enum SaucerType { SLOT, ROULETTE, HAT }

    /** Колокольчик: неподвижная круглая мишень в левом верхнем углу. */
    static final class Bell {
        final Vec2 center;
        boolean lit;
        double flash;

        Bell(Vec2 center) {
            this.center = center;
        }
    }

    /** Утка из тира: ездит вместе с остальными влево-вправо, после попадания падает. */
    static final class Duck {
        boolean down;
        double flash;
    }

    static final class Saucer {
        final Vec2 center;
        final SaucerType type;
        Ball held;
        /** Сколько ещё держать шар после окончания розыгрыша. */
        double ejectTimer = -1;
        /** Пауза после выброса, чтобы шар не поймался снова. */
        double cooldown;

        Saucer(Vec2 center, SaucerType type) {
            this.center = center;
            this.type = type;
        }
    }

    /** Вертушка поперёк левой орбиты. */
    static final class Spinner {
        final double left = 20, right = 68, y = 330;
        double angle;
        double speed;
    }

    final List<Wall> walls = new ArrayList<>();
    /** Золотые рельсы — ломаные для отрисовки; из них же строятся стены. */
    final List<List<Vec2>> rails = new ArrayList<>();
    final List<Vec2> outline = new ArrayList<>();
    final List<Sling> slings = new ArrayList<>();
    final List<Bumper> bumpers = new ArrayList<>();
    final List<Rollover> rollovers = new ArrayList<>();
    final List<Wall> cards = new ArrayList<>();
    final Wall[] dice = new Wall[2];
    final Wall gate;
    final Spinner spinner = new Spinner();
    final Saucer slotSaucer = new Saucer(new Vec2(CENTER_X, 596), SaucerType.SLOT);
    final Saucer rouletteSaucer = new Saucer(ROULETTE_CENTER, SaucerType.ROULETTE);
    final Saucer hatSaucer = new Saucer(HAT_CENTER, SaucerType.HAT);
    final List<Saucer> saucers = List.of(slotSaucer, rouletteSaucer, hatSaucer);
    final List<Bell> bells = List.of(new Bell(new Vec2(95, 150)), new Bell(new Vec2(140, 105)), new Bell(new Vec2(185, 145)));
    final List<Duck> ducks = List.of(new Duck(), new Duck(), new Duck());
    /** Звёзды-перекаты слева вверху. */
    final List<Rollover> stars = List.of(new Rollover(new Vec2(108, 240)), new Rollover(new Vec2(108, 300)),
            new Rollover(new Vec2(108, 360)));
    final RampPath ramp;
    final Flipper leftFlipper;
    final Flipper rightFlipper;

    Table() {
        // Внешний контур: левая стена, скругления сверху, правая стена желоба запуска
        outline.add(new Vec2(20, 1078));
        addArc(outline, 140, 140, 120, 180, 270);
        addArc(outline, 460, 150, 130, 270, 360);
        outline.add(new Vec2(LANE_RIGHT, 1078));
        addRail(outline, 0.5);

        // Желоб запуска и односторонние воротца над ним
        addRail(List.of(new Vec2(LANE_LEFT, 300), new Vec2(LANE_LEFT, 1078)), 0.5);
        gate = new Wall(new Vec2(LANE_LEFT, 300), new Vec2(LANE_RIGHT, 280), 2, 0.3, Kind.GATE, 0);
        Vec2 d = gate.b.sub(gate.a);
        gate.oneWayNormal = new Vec2(d.y(), -d.x()).scale(1 / d.length());
        walls.add(gate);

        // Левая орбита
        addRail(List.of(new Vec2(68, 200), new Vec2(68, 470)), 0.5);

        // Направляющие от боковых стен к флипперам: боковых коридоров-аутлейнов нет,
        // шар теряется только между флипперами
        addRail(List.of(new Vec2(20, GUIDE_TOP_Y), new Vec2(193, 922)), 0.4);
        addRail(List.of(new Vec2(mirror(20), GUIDE_TOP_Y), new Vec2(mirror(193), 922)), 0.4);
        // Стенки дренажа под флипперами, чтобы шар не уходил под фартуки
        addRail(List.of(new Vec2(193, 942), new Vec2(193, 1078)), 0.3);
        addRail(List.of(new Vec2(mirror(193), 942), new Vec2(mirror(193), 1078)), 0.3);

        // Карман входа на рампу
        // Крыша домиком: на плоской кромке шар мог бы остановиться навсегда
        addRail(List.of(new Vec2(450, 470), new Vec2(450, 400), new Vec2(480, 384), new Vec2(510, 400),
                new Vec2(510, 470)), 0.35);

        // Столбики верхних дорожек 7-7-7
        for (double x : new double[]{215, 265, 315, 365}) {
            addRail(List.of(new Vec2(x, 50), new Vec2(x, 115)), 0.5);
        }
        for (double x : new double[]{240, 290, 340}) rollovers.add(new Rollover(new Vec2(x, 85)));

        addSlotMachine();
        addSling(false);
        addSling(true);

        // Карты-мишени «10 J Q K A» вдоль левой стены
        for (int i = 0; i < 5; i++) {
            double cy = 520 + 38 * i;
            Wall card = new Wall(new Vec2(30, cy - 15), new Vec2(30, cy + 15), 5, 0.3, Kind.CARD, i);
            cards.add(card);
            walls.add(card);
        }

        bumpers.add(new Bumper(new Vec2(230, 230), 28, new Color(220, 30, 50), "100"));
        bumpers.add(new Bumper(new Vec2(350, 230), 28, new Color(30, 90, 220), "500"));
        bumpers.add(new Bumper(new Vec2(CENTER_X, 320), 28, new Color(20, 150, 70), "25"));
        bumpers.add(new Bumper(new Vec2(150, 400), 24, new Color(120, 40, 200), "1K"));

        ramp = new RampPath(List.of(
                new Vec2(480, 455), new Vec2(480, 395), new Vec2(490, 360), new Vec2(515, 342),
                new Vec2(538, 356), new Vec2(545, 395), new Vec2(545, 600), new Vec2(538, 660),
                new Vec2(515, 700), new Vec2(497, 725)));

        leftFlipper = new Flipper(new Vec2(200, 930), 76, Math.toRadians(30), Math.toRadians(-30));
        rightFlipper = new Flipper(new Vec2(mirror(200), 930), 76, Math.toRadians(150), Math.toRadians(210));
    }

    /** Утки едут группой: центр качается влево-вправо над рулеткой. */
    static Vec2 duckPosition(int index, double time) {
        return new Vec2(412 + (index - 1) * 26 + 18 * Math.sin(time * 1.3), DUCK_Y);
    }

    /** Корпус слот-машины — препятствие; боковые стенки служат мишенями-кубиками. */
    private void addSlotMachine() {
        double x0 = SLOT_BODY.getMinX(), x1 = SLOT_BODY.getMaxX();
        double y0 = SLOT_BODY.getMinY(), y1 = SLOT_BODY.getMaxY();
        Vec2[] p = {
                new Vec2(x0 + 10, y0), new Vec2(x1 - 10, y0), new Vec2(x1, y0 + 10), new Vec2(x1, y1 - 10),
                new Vec2(x1 - 10, y1), new Vec2(x0 + 10, y1), new Vec2(x0, y1 - 10), new Vec2(x0, y0 + 10),
        };
        for (int i = 0; i < p.length; i++) {
            Vec2 a = p[i], b = p[(i + 1) % p.length];
            Kind kind = Kind.PLAIN;
            int index = 0;
            if (i == 2) kind = Kind.DICE;
            if (i == 6) {
                kind = Kind.DICE;
                index = 1;
            }
            Wall w = new Wall(a, b, 3, 0.45, kind, index);
            if (kind == Kind.DICE) dice[index] = w;
            walls.add(w);
        }
    }

    private void addSling(boolean right) {
        Vec2 top = new Vec2(sx(105, right), 740);
        Vec2 bottom = new Vec2(sx(105, right), 810);
        Vec2 corner = new Vec2(sx(160, right), 850);
        walls.add(new Wall(top, bottom, 4, 0.4, Kind.PLAIN, 0));
        walls.add(new Wall(bottom, corner, 4, 0.4, Kind.PLAIN, 0));
        Wall kicker = new Wall(corner, top, 4, 0.5, Kind.SLING, 0);
        walls.add(kicker);
        slings.add(new Sling(top, bottom, corner, kicker));
    }

    private static double sx(double x, boolean mirrored) {
        return mirrored ? mirror(x) : x;
    }

    private static double mirror(double x) {
        return 2 * CENTER_X - x;
    }

    private static void addArc(List<Vec2> pts, double cx, double cy, double r, double fromDeg, double toDeg) {
        int segments = 16;
        for (int i = 0; i <= segments; i++) {
            double a = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / segments);
            pts.add(new Vec2(cx + r * Math.cos(a), cy + r * Math.sin(a)));
        }
    }

    private void addRail(List<Vec2> pts, double restitution) {
        rails.add(pts);
        for (int i = 0; i + 1 < pts.size(); i++) {
            walls.add(new Wall(pts.get(i), pts.get(i + 1), 3, restitution, Kind.PLAIN, 0));
        }
    }
}
