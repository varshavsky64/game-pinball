package pinball;

final class Physics {
    /** Результат столкновения: нормаль (от препятствия к шару) и скорость удара. */
    record Contact(Vec2 normal, double impactSpeed) {}

    private Physics() {}

    /**
     * Столкновение шара с «капсулой» — отрезком a-b с толщиной thickness.
     * Круг — частный случай, когда a == b.
     * Если препятствие вращается (флиппер), передаются pivot и omega,
     * чтобы учесть скорость поверхности в точке контакта.
     */
    static Contact collide(Ball ball, Vec2 a, Vec2 b, double thickness,
                           double restitution, Vec2 pivot, double omega) {
        Vec2 ab = b.sub(a);
        double len2 = ab.dot(ab);
        double t = len2 < 1e-9 ? 0 : clamp(ball.pos.sub(a).dot(ab) / len2, 0, 1);
        Vec2 closest = a.add(ab.scale(t));
        Vec2 d = ball.pos.sub(closest);
        double dist = d.length();
        double minDist = ball.radius + thickness;
        if (dist >= minDist || dist < 1e-9) return null;

        Vec2 n = d.scale(1 / dist);
        ball.pos = closest.add(n.scale(minDist));

        Vec2 surface = Vec2.ZERO;
        if (pivot != null) {
            Vec2 r = closest.sub(pivot);
            surface = new Vec2(-r.y() * omega, r.x() * omega);
        }
        Vec2 rel = ball.vel.sub(surface);
        double vn = rel.dot(n);
        if (vn >= 0) return new Contact(n, 0);

        rel = rel.sub(n.scale((1 + restitution) * vn));
        ball.vel = rel.add(surface);
        return new Contact(n, -vn);
    }

    static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
