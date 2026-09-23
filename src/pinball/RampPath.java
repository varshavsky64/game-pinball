package pinball;

import java.util.ArrayList;
import java.util.List;

/** Гладкая траектория рампы (сплайн Катмулла–Рома) с доступом по пройденному расстоянию. */
final class RampPath {
    private final List<Vec2> points = new ArrayList<>();
    private final double[] cumulative;
    final double length;

    RampPath(List<Vec2> control) {
        int steps = 10;
        for (int i = 0; i + 1 < control.size(); i++) {
            Vec2 p0 = control.get(Math.max(i - 1, 0));
            Vec2 p1 = control.get(i);
            Vec2 p2 = control.get(i + 1);
            Vec2 p3 = control.get(Math.min(i + 2, control.size() - 1));
            for (int s = 0; s < steps; s++) points.add(catmullRom(p0, p1, p2, p3, (double) s / steps));
        }
        points.add(control.get(control.size() - 1));

        cumulative = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cumulative[i] = cumulative[i - 1] + points.get(i).sub(points.get(i - 1)).length();
        }
        length = cumulative[cumulative.length - 1];
    }

    private static Vec2 catmullRom(Vec2 p0, Vec2 p1, Vec2 p2, Vec2 p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        double x = 0.5 * (2 * p1.x() + (-p0.x() + p2.x()) * t + (2 * p0.x() - 5 * p1.x() + 4 * p2.x() - p3.x()) * t2
                + (-p0.x() + 3 * p1.x() - 3 * p2.x() + p3.x()) * t3);
        double y = 0.5 * (2 * p1.y() + (-p0.y() + p2.y()) * t + (2 * p0.y() - 5 * p1.y() + 4 * p2.y() - p3.y()) * t2
                + (-p0.y() + 3 * p1.y() - 3 * p2.y() + p3.y()) * t3);
        return new Vec2(x, y);
    }

    List<Vec2> points() {
        return points;
    }

    Vec2 pointAt(double distance) {
        int i = segmentAt(distance);
        double segLen = cumulative[i + 1] - cumulative[i];
        double t = segLen < 1e-9 ? 0 : (distance - cumulative[i]) / segLen;
        return points.get(i).add(points.get(i + 1).sub(points.get(i)).scale(t));
    }

    Vec2 directionAt(double distance) {
        int i = segmentAt(distance);
        Vec2 d = points.get(i + 1).sub(points.get(i));
        double len = d.length();
        return len < 1e-9 ? new Vec2(0, 1) : d.scale(1 / len);
    }

    private int segmentAt(double distance) {
        double d = Math.max(0, Math.min(length, distance));
        int i = 0;
        while (i < cumulative.length - 2 && cumulative[i + 1] < d) i++;
        return i;
    }
}
