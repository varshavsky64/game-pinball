package pinball;

/** Неизменяемый 2D-вектор. Координаты экранные: ось Y направлена вниз. */
record Vec2(double x, double y) {
    static final Vec2 ZERO = new Vec2(0, 0);

    static Vec2 polar(double angle, double length) {
        return new Vec2(Math.cos(angle) * length, Math.sin(angle) * length);
    }

    Vec2 add(Vec2 o) { return new Vec2(x + o.x, y + o.y); }
    Vec2 sub(Vec2 o) { return new Vec2(x - o.x, y - o.y); }
    Vec2 scale(double k) { return new Vec2(x * k, y * k); }
    double dot(Vec2 o) { return x * o.x + y * o.y; }
    double length() { return Math.sqrt(x * x + y * y); }
}
