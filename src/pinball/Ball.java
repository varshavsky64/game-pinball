package pinball;

final class Ball {
    final double radius = 10;
    Vec2 pos;
    Vec2 vel = Vec2.ZERO;
    /** Шар лежит в лунке (слот-машина, рулетка) и не участвует в физике. */
    boolean held;
    /** Пройденный путь по рампе; отрицательное значение — шар не на рампе. */
    double rampDistance = -1;
    /** Шар внутри смерча: угол прошлого шага и накопленный поворот вокруг центра. */
    boolean inVortex;
    double vortexPrevAngle;
    double vortexTurn;

    Ball(Vec2 pos) {
        this.pos = pos;
    }

    Ball(Vec2 pos, Vec2 vel) {
        this.pos = pos;
        this.vel = vel;
    }

    boolean onRamp() {
        return rampDistance >= 0;
    }

    /** Шар катится по полю и сталкивается с препятствиями. */
    boolean free() {
        return !held && !onRamp();
    }
}
