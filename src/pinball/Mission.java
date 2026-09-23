package pinball;

import java.util.Random;

/** Задание на время: сделать N действий одного типа. С каждым уровнем — больше целей и выше награда. */
final class Mission {
    enum Type {
        BUMPERS("Bumpers"),
        SLINGS("Slingshots"),
        RAMPS("Ramps"),
        ORBITS("Orbits"),
        CARDS("Cards"),
        SLOT("Slot machine"),
        ROULETTE("Roulette"),
        LANES("7-7-7 lanes"),
        BELLS("Bells"),
        DUCKS("Ducks"),
        HAT("Magic hat"),
        WHIRL("Whirlwind loops");

        final String title;

        Type(String title) {
            this.title = title;
        }
    }

    private static final int BASE_REWARD = 250_000;

    final Type type;
    final int target;
    final int reward;
    final double duration;
    int progress;
    double timeLeft;

    private Mission(Type type, int target, int reward, double duration) {
        this.type = type;
        this.target = target;
        this.reward = reward;
        this.duration = duration;
        this.timeLeft = duration;
    }

    /** Новое задание уровня level, по возможности не такое, как предыдущее. */
    static Mission random(int level, Type previous, Random rnd) {
        Type type;
        do {
            type = Type.values()[rnd.nextInt(Type.values().length)];
        } while (type == previous);
        int target = switch (type) {
            case BUMPERS -> 12 + 4 * level;
            case SLINGS -> 8 + 3 * level;
            case RAMPS, ORBITS -> 2 + level / 2;
            case CARDS -> 3 + level;
            case SLOT, ROULETTE -> 1 + level / 3;
            case LANES -> 1 + level / 4;
            case BELLS -> 4 + 2 * level;
            case DUCKS -> 3 + level;
            case HAT -> 1 + level / 3;
            case WHIRL -> 2 + level / 2;
        };
        double duration = switch (type) {
            case BUMPERS, SLINGS -> 40;
            case CARDS, LANES, BELLS, DUCKS -> 50;
            default -> 60;
        };
        return new Mission(type, target, BASE_REWARD * (level + 1), duration);
    }

    /** Засчитывает действие; true — задание выполнено. */
    boolean advance() {
        progress++;
        return progress >= target;
    }

    String describe() {
        return type.title + ": " + progress + " / " + target;
    }
}
