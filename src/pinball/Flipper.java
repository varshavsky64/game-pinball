package pinball;

final class Flipper {
    static final double RADIUS = 7;
    static final double SPEED = 22; // рад/с

    final Vec2 pivot;
    final double length;
    final double restAngle;
    final double upAngle;

    double angle;
    double omega;
    boolean pressed;

    Flipper(Vec2 pivot, double length, double restAngle, double upAngle) {
        this.pivot = pivot;
        this.length = length;
        this.restAngle = restAngle;
        this.upAngle = upAngle;
        this.angle = restAngle;
    }

    void update(double dt) {
        double target = pressed ? upAngle : restAngle;
        double diff = target - angle;
        double step = SPEED * dt;
        if (Math.abs(diff) <= step) {
            angle = target;
            omega = 0;
        } else {
            omega = Math.signum(diff) * SPEED;
            angle += omega * dt;
        }
    }

    Vec2 tip() {
        return pivot.add(Vec2.polar(angle, length));
    }
}
