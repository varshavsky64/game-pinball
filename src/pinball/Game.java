package pinball;

import pinball.SoundEngine.Sfx;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.prefs.Preferences;

/** Состояние и правила игры: физика шаров, мишени, задания, комбо, мультибол, очки. */
final class Game {
    private static final double GRAVITY = 1000;
    private static final int SUBSTEPS = 4;
    private static final double MAX_SPEED = 2600;

    private static final double BUMPER_KICK = 380;
    private static final double SLING_KICK = 450;
    private static final double SLING_MIN_IMPACT = 80;
    private static final double HIT_SOUND_IMPACT = 250;
    private static final double CARD_MIN_IMPACT = 120;
    private static final double DICE_MIN_IMPACT = 100;
    private static final double SAUCER_MAX_SPEED = 1100;
    private static final double RAMP_MIN_SPEED = 250;
    private static final double RAMP_SPEED = 750;
    private static final double KICKBACK_SPEED = 1450;

    private static final double BALL_SAVE_SECONDS = 8;
    private static final double MULTIBALL_SAVE_SECONDS = 12;
    private static final double SKILL_SHOT_WINDOW = 5;
    private static final int BALLS_PER_GAME = 3;
    private static final int MAX_MULTIPLIER = 5;
    static final int RAMPS_FOR_MULTIBALL = 3;

    // Цены попаданий
    private static final int BUMPER_POINTS = 1_000;
    private static final int SUPER_BUMPER_FACTOR = 5;
    private static final int SLING_POINTS = 500;
    private static final int ROLLOVER_POINTS = 5_000;
    private static final int LANES_POINTS = 50_000;
    private static final int CARD_POINTS = 10_000;
    private static final int ROYAL_FLUSH_POINTS = 250_000;
    private static final int DICE_POINTS = 7_500;
    private static final int DICE_DOUBLE_POINTS = 50_000;
    private static final int RAMP_POINTS = 25_000;
    private static final int SPINNER_POINTS = 1_000;
    private static final int ORBIT_POINTS = 15_000;
    private static final int SKILL_SHOT_POINTS = 150_000;
    private static final int JACKPOT_BASE = 1_000_000;
    private static final int BELL_POINTS = 5_000;
    private static final int BELLS_ALL_POINTS = 100_000;
    private static final int DUCK_POINTS = 20_000;
    private static final int DUCKS_ALL_POINTS = 150_000;
    private static final int STAR_POINTS = 2_500;
    private static final int STARS_ALL_POINTS = 75_000;
    private static final int WHIRL_ENTER_POINTS = 5_000;
    private static final int WHIRL_LOOP_POINTS = 25_000;
    private static final double VORTEX_PULL = 1100;
    private static final double STARS_BALL_SAVE = 10;

    // Комбо: быстрые попадания подряд увеличивают множитель
    private static final double COMBO_WINDOW = 2.5;
    private static final int COMBO_STEP = 10;
    private static final int MAX_COMBO_MULTIPLIER = 5;
    private static final double SUPER_BUMPERS_SECONDS = 20;
    private static final double RAMP_CHAIN_WINDOW = 6;
    private static final int MAX_SPINNER_LEVEL = 5;
    private static final int HURRY_UP_START = 500_000;
    private static final int HURRY_UP_MIN = 100_000;
    private static final double HURRY_UP_DRAIN = 25_000;
    private static final double MISSION_PAUSE = 3;

    static final String[] RANKS = {"ROOKIE", "PLAYER", "LUCKY STAR", "HIGH ROLLER", "VIP", "TYCOON", "LEGEND"};
    private static final int[] RANK_SCORES = {0, 250_000, 750_000, 2_000_000, 5_000_000, 12_000_000, 25_000_000};
    static final String[] CARD_NAMES = {"10", "J", "Q", "K", "A"};

    static final Color GOLD = new Color(255, 205, 60);
    static final Color PINK = new Color(255, 80, 180);
    static final Color CYAN = new Color(80, 230, 255);

    /** Всплывающая надпись над местом события. */
    static final class Popup {
        final String text;
        final double x;
        double y;
        final Color color;
        final float size;
        double age;

        Popup(String text, double x, double y, Color color, float size) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
            this.size = size;
        }
    }

    /** Искра или монета. */
    static final class Particle {
        double x, y, vx, vy, life, spin;
        final double maxLife;
        final Color color;
        final boolean coin;

        Particle(double x, double y, double vx, double vy, double life, Color color, boolean coin) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.maxLife = life;
            this.color = color;
            this.coin = coin;
        }
    }

    final Table table = new Table();
    final SlotMachine slot = new SlotMachine();
    final Roulette roulette = new Roulette();
    final List<Ball> balls = new ArrayList<>();
    final List<Popup> popups = new ArrayList<>();
    final List<Particle> particles = new ArrayList<>();

    private final SoundEngine sound;
    private final Preferences prefs = Preferences.userNodeForPackage(Game.class);
    private final Random rnd = new Random();

    int score;
    int highScore;
    int ballsLeft;
    int ballNumber;
    int multiplier;
    int rank;
    int ramps;
    int jackpot;
    int bonus;
    int skillLane = -1;
    boolean gameOver;
    boolean paused;
    boolean plungerHeld;
    boolean kickbackLit;
    boolean multiball;
    boolean tilted;
    final boolean[] diceHit = new boolean[2];
    double plungerCharge;
    double lanesFlash;
    double ballSaveTimer;
    double time;
    double tiltLevel;
    double shake;
    double eventFlash;
    double kickbackFlash;
    /** Момент окончания игры — для анимации «диафрагмы». */
    double gameOverTime;

    /** Попадания подряд без паузы дольше COMBO_WINDOW. */
    int combo;
    double comboTimer;
    /** Какие бамперы уже зажжены на пути к супер-бамперам. */
    final boolean[] bumperLit = new boolean[4];
    double superBumpersTimer;
    int spinnerLevel = 1;
    /** Приз «Хватай!», который тает, пока его не заберут в лунке слот-машины. */
    double hurryUp;
    Mission mission;
    int missionLevel;

    /** Длительность текущего подшага физики. */
    private double substep;
    /** Пока > 0, шар в цилиндре и приз ещё не объявлен. */
    double hatReveal;
    private double bellsResetTimer;
    private double ducksResetTimer;
    private double starsFlash;
    private int rampChain;
    private double rampChainTimer;
    private double missionPause;
    private Mission.Type lastMissionType;
    private double cardsResetTimer;
    private double skillTimer;
    private boolean saveArmed;
    private int lastSpinnerHalfTurns;
    private String message = "";
    private double messageTimer;
    private double messageAge;

    Game(SoundEngine sound) {
        this.sound = sound;
        highScore = prefs.getInt("highScore", 0);
        newGame();
    }

    void newGame() {
        score = 0;
        ballsLeft = BALLS_PER_GAME;
        ballNumber = 1;
        multiplier = 1;
        rank = 0;
        ramps = 0;
        jackpot = JACKPOT_BASE;
        bonus = 0;
        gameOver = false;
        paused = false;
        tilted = false;
        tiltLevel = 0;
        multiball = false;
        kickbackLit = true;
        combo = 0;
        comboTimer = 0;
        superBumpersTimer = 0;
        spinnerLevel = 1;
        hurryUp = 0;
        rampChain = 0;
        missionLevel = 0;
        lastMissionType = null;
        mission = Mission.random(0, null, rnd);
        missionPause = 0;
        diceHit[0] = diceHit[1] = false;
        java.util.Arrays.fill(bumperLit, false);
        for (Table.Bell bell : table.bells) bell.lit = false;
        for (Table.Duck d : table.ducks) d.down = false;
        for (Table.Rollover r : table.stars) r.lit = false;
        hatReveal = 0;
        for (Table.Wall c : table.cards) c.active = true;
        for (Table.Rollover r : table.rollovers) r.lit = false;
        for (Table.Saucer s : table.saucers) {
            s.held = null;
            s.ejectTimer = -1;
            s.cooldown = 0;
        }
        slot.reset();
        roulette.reset();
        balls.clear();
        popups.clear();
        particles.clear();
        startBall();
        show("BALL 1 — GOOD LUCK!", 2.5);
        sound.play(Sfx.START, 0.8);
    }

    // ================================================================ управление

    void setLeftFlipper(boolean down) {
        flip(table.leftFlipper, down, -1);
    }

    void setRightFlipper(boolean down) {
        flip(table.rightFlipper, down, 1);
    }

    private void flip(Flipper f, boolean down, int laneShift) {
        if (f.pressed == down) return;
        if (down && (paused || tilted)) return;
        f.pressed = down;
        sound.play(down ? Sfx.FLIPPER_UP : Sfx.FLIPPER_DOWN, down ? 0.8 : 0.5);
        if (down && lanesFlash <= 0 && !gameOver) rotateLanes(laneShift);
    }

    /** Флипперы сдвигают горящие огни дорожек 7-7-7. */
    private void rotateLanes(int shift) {
        int n = table.rollovers.size();
        boolean[] lit = new boolean[n];
        for (int i = 0; i < n; i++) lit[i] = table.rollovers.get(i).lit;
        for (int i = 0; i < n; i++) table.rollovers.get(i).lit = lit[(i - shift + n) % n];
    }

    void setPlunger(boolean down) {
        if (down) {
            if (!paused && !gameOver) plungerHeld = true;
            return;
        }
        if (!plungerHeld) return;
        plungerHeld = false;
        Ball b = ballOnPlunger();
        if (b != null) launch(b, plungerCharge);
        plungerCharge = 0;
    }

    private void launch(Ball b, double power) {
        // Плунжер мгновенно возвращается вверх и выбрасывает шар
        b.pos = new Vec2(b.pos.x(), Table.PLUNGER_REST_Y - b.radius - 0.5);
        b.vel = new Vec2(0, -(800 + 1400 * power));
        sound.play(Sfx.LAUNCH, 0.3 + 0.7 * power);
    }

    /** Толчок стола: помогает спасти шар, но частые толчки приводят к TILT. */
    void nudge() {
        if (paused || gameOver || tilted) return;
        for (Ball b : balls) {
            if (b.free()) b.vel = b.vel.add(new Vec2((rnd.nextDouble() - 0.5) * 240, -200));
        }
        shake = 0.15;
        tiltLevel += 1;
        sound.play(Sfx.NUDGE, 0.8);
        if (tiltLevel > 3.2) tilt();
        else if (tiltLevel > 2.2) show("DANGER!", 1.5);
    }

    private void tilt() {
        tilted = true;
        kickbackLit = false;
        combo = 0;
        hurryUp = 0;
        table.leftFlipper.pressed = false;
        table.rightFlipper.pressed = false;
        show("TILT", 4);
        sound.play(Sfx.TILT, 1.0);
    }

    void togglePause() {
        if (!gameOver) paused = !paused;
    }

    // ================================================================ состояние для отрисовки

    Ball ballOnPlunger() {
        for (Ball b : balls) {
            if (b.free() && b.pos.x() > Table.LANE_LEFT && b.pos.y() > Table.PLUNGER_REST_Y - 40) return b;
        }
        return null;
    }

    double plungerY() {
        return Table.PLUNGER_REST_Y + plungerCharge * Table.PLUNGER_MAX_PULL;
    }

    String message() {
        if (paused) return "PAUSED";
        if (messageTimer > 0) return message;
        if (tilted) return "TILT";
        if (gameOver) return "GAME OVER — PRESS ENTER";
        if (ballOnPlunger() != null) {
            return skillLane >= 0 ? "SKILL SHOT: LANE " + (skillLane + 1) : "LAUNCH THE BALL";
        }
        if (hurryUp > 0) return "HURRY UP! SLOT: " + formatPoints((int) (hurryUp / 10_000) * 10_000);
        if (superBumpersTimer > 0) return "SUPER BUMPERS x" + SUPER_BUMPER_FACTOR + "!";
        if (multiball) return "MULTIBALL! SLOT = JACKPOT";
        return "";
    }

    double messageAge() {
        return messageTimer > 0 ? messageAge : Double.MAX_VALUE;
    }

    /** Текущий множитель комбо: растёт на 1 за каждые COMBO_STEP попаданий подряд. */
    int comboMultiplier() {
        return Math.min(MAX_COMBO_MULTIPLIER, 1 + combo / COMBO_STEP);
    }

    /** Доля окна комбо, которая ещё осталась (для полоски на панели). */
    double comboFraction() {
        return combo == 0 ? 0 : Math.max(0, comboTimer / COMBO_WINDOW);
    }

    static String formatPoints(int value) {
        return String.format(java.util.Locale.US, "%,d", value);
    }

    // ================================================================ игровой цикл

    void update(double dt) {
        time += dt;
        if (paused) return;
        messageTimer -= dt;
        messageAge += dt;
        tiltLevel = Math.max(0, tiltLevel - 0.6 * dt);
        shake = Math.max(0, shake - dt);
        eventFlash = Math.max(0, eventFlash - dt);
        kickbackFlash = Math.max(0, kickbackFlash - dt);

        if (plungerHeld) plungerCharge = Math.min(1, plungerCharge + dt * 1.2);

        double h = dt / SUBSTEPS;
        substep = h;
        for (int i = 0; i < SUBSTEPS; i++) {
            table.leftFlipper.update(h);
            table.rightFlipper.update(h);
            physicsStep(h);
        }

        for (Table.Wall w : table.walls) w.flash = Math.max(0, w.flash - dt);
        for (Table.Bumper b : table.bumpers) b.flash = Math.max(0, b.flash - dt);
        if (lanesFlash > 0) {
            lanesFlash -= dt;
            if (lanesFlash <= 0) for (Table.Rollover r : table.rollovers) r.lit = false;
        }
        if (cardsResetTimer > 0) {
            cardsResetTimer -= dt;
            if (cardsResetTimer <= 0) for (Table.Wall c : table.cards) c.active = true;
        }
        boolean inPlay = ballOnPlunger() == null && !balls.isEmpty();
        if (inPlay) ballSaveTimer = Math.max(0, ballSaveTimer - dt);
        if (skillLane >= 0 && skillTimer > 0) {
            skillTimer -= dt;
            if (skillTimer <= 0) skillLane = -1;
        }

        updateCombo(dt);
        updateTimedFeatures(dt, inPlay);
        updateSpinner(dt);
        updateSaucers(dt);
        updateEffects(dt);
    }

    private void physicsStep(double h) {
        List<Ball> drained = null;
        // Обход по индексу: мультибол может добавить шары прямо во время шага
        for (int i = 0; i < balls.size(); i++) {
            Ball b = balls.get(i);
            if (b.held) continue;
            if (b.onRamp()) {
                advanceRamp(b, h);
                continue;
            }
            Vec2 prev = b.pos;
            b.vel = b.vel.add(new Vec2(0, GRAVITY * h));
            double speed = b.vel.length();
            if (speed > MAX_SPEED) b.vel = b.vel.scale(MAX_SPEED / speed);
            b.pos = b.pos.add(b.vel.scale(h));

            collideStatic(b);
            checkSensors(b, prev);

            if (b.pos.y() > Table.HEIGHT + 30) {
                if (drained == null) drained = new ArrayList<>();
                drained.add(b);
            }
        }
        collideBalls();
        if (drained != null) for (Ball b : drained) drainBall(b);
    }

    private void collideStatic(Ball b) {
        for (Table.Wall w : table.walls) {
            if (!w.active) continue;
            if (w.oneWayNormal != null && b.pos.sub(w.a).dot(w.oneWayNormal) < 0) continue;
            Physics.Contact c = Physics.collide(b, w.a, w.b, w.thickness, w.restitution, null, 0);
            if (c == null) continue;
            switch (w.kind) {
                case SLING -> {
                    if (c.impactSpeed() > SLING_MIN_IMPACT) {
                        b.vel = b.vel.add(c.normal().scale(SLING_KICK));
                        w.flash = 0.15;
                        hit();
                        addScore(SLING_POINTS);
                        progressMission(Mission.Type.SLINGS);
                        sound.play(Sfx.SLING, 0.9);
                    }
                }
                case CARD -> hitCard(w, c);
                case DICE -> hitDice(w, c);
                default -> {
                    if (c.impactSpeed() > HIT_SOUND_IMPACT) sound.play(Sfx.WALL, hitVolume(c.impactSpeed()));
                }
            }
        }

        for (int i = 0; i < table.bumpers.size(); i++) {
            Table.Bumper bumper = table.bumpers.get(i);
            Physics.Contact c = Physics.collide(b, bumper.center, bumper.center, bumper.radius, 0.7, null, 0);
            if (c != null) hitBumper(b, bumper, i, c);
        }

        for (Table.Bell bell : table.bells) {
            Physics.Contact c = Physics.collide(b, bell.center, bell.center, Table.BELL_RADIUS, 0.6, null, 0);
            if (c != null && c.impactSpeed() > 60) hitBell(b, bell, c);
        }

        for (int i = 0; i < table.ducks.size(); i++) {
            Table.Duck duck = table.ducks.get(i);
            if (duck.down) continue;
            Vec2 at = Table.duckPosition(i, time);
            Physics.Contact c = Physics.collide(b, at, at, Table.DUCK_RADIUS, 0.5, null, 0);
            if (c != null && c.impactSpeed() > 80) hitDuck(duck, at);
        }

        for (Flipper f : new Flipper[]{table.leftFlipper, table.rightFlipper}) {
            Physics.Contact c = Physics.collide(b, f.pivot, f.tip(), Flipper.RADIUS, 0.25, f.pivot, f.omega);
            if (c != null && c.impactSpeed() > HIT_SOUND_IMPACT) sound.play(Sfx.WALL, hitVolume(c.impactSpeed()));
        }

        double py = plungerY();
        Physics.collide(b, new Vec2(Table.LANE_LEFT + 1, py), new Vec2(Table.LANE_RIGHT - 1, py), 0, 0.2, null, 0);
    }

    private void collideBalls() {
        for (int i = 0; i < balls.size(); i++) {
            Ball a = balls.get(i);
            if (!a.free()) continue;
            for (int j = i + 1; j < balls.size(); j++) {
                Ball b = balls.get(j);
                if (!b.free()) continue;
                Vec2 d = b.pos.sub(a.pos);
                double dist = d.length();
                double min = a.radius + b.radius;
                if (dist >= min || dist < 1e-6) continue;
                Vec2 n = d.scale(1 / dist);
                Vec2 push = n.scale((min - dist) / 2);
                a.pos = a.pos.sub(push);
                b.pos = b.pos.add(push);
                double rel = b.vel.sub(a.vel).dot(n);
                if (rel < 0) {
                    double impulse = -(1 + 0.9) * rel / 2;
                    a.vel = a.vel.sub(n.scale(impulse));
                    b.vel = b.vel.add(n.scale(impulse));
                    if (-rel > HIT_SOUND_IMPACT) sound.play(Sfx.WALL, hitVolume(-rel));
                }
            }
        }
    }

    private void checkSensors(Ball b, Vec2 prev) {
        // Шар вышел из желоба на поле — включается спасение шара и окно скилл-шота
        double laneEdge = Table.LANE_LEFT - b.radius;
        if (prev.x() > laneEdge && b.pos.x() <= laneEdge && b.pos.y() < 320) {
            if (saveArmed) {
                saveArmed = false;
                ballSaveTimer = BALL_SAVE_SECONDS;
            }
            if (skillLane >= 0) skillTimer = SKILL_SHOT_WINDOW;
        }

        if (Table.RAMP_MOUTH.contains(b.pos.x(), b.pos.y()) && b.vel.y() < -RAMP_MIN_SPEED) {
            b.rampDistance = 0;
            sound.play(Sfx.RAMP, 0.8);
            return;
        }

        if (kickbackLit && !tilted && b.vel.y() > 0 && Table.KICKBACK_ZONE.contains(b.pos.x(), b.pos.y())) {
            b.pos = new Vec2(Table.CENTER_X, b.pos.y());
            b.vel = new Vec2((rnd.nextDouble() - 0.5) * 120, -KICKBACK_SPEED);
            kickbackLit = false;
            kickbackFlash = 0.5;
            show("KICKBACK!", 1.5);
            sound.play(Sfx.KICKBACK, 1.0);
        }

        Table.Spinner sp = table.spinner;
        if (b.pos.x() > sp.left && b.pos.x() < sp.right && (prev.y() - sp.y) * (b.pos.y() - sp.y) < 0) {
            sp.speed = Math.min(25, Math.abs(b.vel.y()) / 35);
            if (b.vel.y() < -700) orbit();
        }

        checkRollovers(b);
        checkStars(b);
        applyVortex(b);

        for (Table.Saucer s : table.saucers) {
            if (s.held == null && s.cooldown <= 0 && b.pos.sub(s.center).length() < Table.SAUCER_RADIUS
                    && b.vel.length() < SAUCER_MAX_SPEED) {
                capture(s, b);
                return;
            }
        }
    }

    private static double hitVolume(double impact) {
        return Math.min(1, impact / 1500) * 0.8;
    }

    // ================================================================ комбо, задания, таймеры

    /** Любое «настоящее» попадание продлевает комбо. */
    private void hit() {
        if (tilted) return;
        int before = comboMultiplier();
        combo++;
        comboTimer = COMBO_WINDOW;
        int after = comboMultiplier();
        if (after > before) {
            show("COMBO x" + after + "!", 1.5);
            sound.play(Sfx.RANK, 0.6);
        }
    }

    private void updateCombo(double dt) {
        if (combo == 0) return;
        comboTimer -= dt;
        if (comboTimer > 0) return;
        if (combo >= COMBO_STEP) popup("COMBO " + combo, Table.CENTER_X, 760, CYAN, 20);
        combo = 0;
    }

    private void updateTimedFeatures(double dt, boolean inPlay) {
        if (superBumpersTimer > 0) {
            superBumpersTimer -= dt;
            if (superBumpersTimer <= 0) show("SUPER BUMPERS OVER", 1.5);
        }
        if (bellsResetTimer > 0) {
            bellsResetTimer -= dt;
            if (bellsResetTimer <= 0) for (Table.Bell bell : table.bells) bell.lit = false;
        }
        if (ducksResetTimer > 0) {
            ducksResetTimer -= dt;
            if (ducksResetTimer <= 0) for (Table.Duck d : table.ducks) d.down = false;
        }
        if (starsFlash > 0) {
            starsFlash -= dt;
            if (starsFlash <= 0) for (Table.Rollover r : table.stars) r.lit = false;
        }
        for (Table.Bell bell : table.bells) bell.flash = Math.max(0, bell.flash - dt);
        for (Table.Duck d : table.ducks) d.flash = Math.max(0, d.flash - dt);
        if (rampChainTimer > 0) {
            rampChainTimer -= dt;
            if (rampChainTimer <= 0) rampChain = 0;
        }
        if (hurryUp > 0 && inPlay) {
            hurryUp -= HURRY_UP_DRAIN * dt;
            if (hurryUp < HURRY_UP_MIN) {
                hurryUp = 0;
                show("TOO SLOW…", 1.5);
            }
        }
        if (mission != null) {
            if (inPlay && !tilted) {
                mission.timeLeft -= dt;
                if (mission.timeLeft <= 0) {
                    show("MISSION FAILED", 2);
                    sound.play(Sfx.DRAIN, 0.4);
                    lastMissionType = mission.type;
                    mission = null;
                    missionPause = MISSION_PAUSE;
                }
            }
        } else if (!gameOver) {
            missionPause -= dt;
            if (missionPause <= 0) {
                mission = Mission.random(missionLevel, lastMissionType, rnd);
                show("MISSION: " + mission.type.title.toUpperCase() + " x" + mission.target, 2.5);
                sound.play(Sfx.ROLLOVER, 0.7);
            }
        }
    }

    private void progressMission(Mission.Type type) {
        if (tilted || mission == null || mission.type != type) return;
        if (!mission.advance()) return;
        int reward = mission.reward;
        addRaw(reward);
        show("MISSION COMPLETE! +" + formatPoints(reward), 3);
        popup("+" + formatPoints(reward), Table.CENTER_X, 460, GOLD, 30);
        coins(Table.CENTER_X, 460, 40);
        eventFlash = 2;
        sound.play(Sfx.SKILL, 1.0);
        missionLevel++;
        lastMissionType = mission.type;
        mission = null;
        missionPause = MISSION_PAUSE;
    }

    private void startHurryUp() {
        if (tilted) return;
        hurryUp = HURRY_UP_START;
        sound.play(Sfx.MULTIBALL, 0.6);
    }

    // ================================================================ мишени

    private void hitBumper(Ball b, Table.Bumper bumper, int index, Physics.Contact c) {
        b.vel = b.vel.add(c.normal().scale(BUMPER_KICK));
        bumper.flash = 0.2;
        bumper.rotation += 0.7;
        hit();
        boolean superOn = superBumpersTimer > 0;
        int gained = addScore(BUMPER_POINTS * (superOn ? SUPER_BUMPER_FACTOR : 1));
        if (!tilted) jackpot += 2_500;
        Vec2 at = bumper.center.add(c.normal().scale(bumper.radius));
        sparks(at.x(), at.y(), bumper.color.brighter(), superOn ? 20 : 10);
        popup("+" + formatPoints(gained), bumper.center.x(), bumper.center.y() - bumper.radius, superOn ? CYAN : GOLD, superOn ? 18 : 14);
        sound.play(Sfx.BUMPER, 0.9);
        progressMission(Mission.Type.BUMPERS);

        if (superOn || tilted) return;
        bumperLit[index] = true;
        for (boolean lit : bumperLit) if (!lit) return;
        java.util.Arrays.fill(bumperLit, false);
        for (Table.Bell bell : table.bells) bell.lit = false;
        for (Table.Duck d : table.ducks) d.down = false;
        for (Table.Rollover r : table.stars) r.lit = false;
        hatReveal = 0;
        superBumpersTimer = SUPER_BUMPERS_SECONDS;
        show("SUPER BUMPERS x" + SUPER_BUMPER_FACTOR + "!", 2.5);
        eventFlash = 1.5;
        sound.play(Sfx.LANES, 0.9);
    }

    private void hitBell(Ball b, Table.Bell bell, Physics.Contact c) {
        b.vel = b.vel.add(c.normal().scale(150));
        bell.flash = 0.6;
        hit();
        sound.play(Sfx.BELL, 0.7);
        progressMission(Mission.Type.BELLS);
        if (bell.lit || bellsResetTimer > 0) {
            addScore(BELL_POINTS / 2);
            return;
        }
        bell.lit = true;
        int gained = addScore(BELL_POINTS);
        popup("DING! +" + formatPoints(gained), bell.center.x(), bell.center.y() - 18, GOLD, 14);
        for (Table.Bell other : table.bells) if (!other.lit) return;
        gained = addScore(BELLS_ALL_POINTS);
        if (!tilted) jackpot += 50_000;
        bellsResetTimer = 1.2;
        show("RING-A-DING! +" + formatPoints(gained), 2.5);
        coins(140, 130, 25);
        sound.play(Sfx.LANES, 0.8);
    }

    private void hitDuck(Table.Duck duck, Vec2 at) {
        duck.down = true;
        duck.flash = 0.4;
        hit();
        int gained = addScore(DUCK_POINTS);
        popup("QUACK! +" + formatPoints(gained), at.x(), at.y() - 20, Color.WHITE, 15);
        sparks(at.x(), at.y(), GOLD, 8);
        sound.play(Sfx.QUACK, 0.8);
        progressMission(Mission.Type.DUCKS);
        for (Table.Duck d : table.ducks) if (!d.down) return;
        gained = addScore(DUCKS_ALL_POINTS);
        ducksResetTimer = 2;
        eventFlash = 1.2;
        show("SHARPSHOOTER! +" + formatPoints(gained), 2.5);
        coins(412, 150, 30);
        sound.play(Sfx.SKILL, 0.8);
    }

    /** Звёзды слева вверху: все три включают спасение шара. */
    private void checkStars(Ball b) {
        boolean allLit = true;
        for (Table.Rollover r : table.stars) {
            boolean inside = b.pos.sub(r.center).length() < r.radius + b.radius;
            if (inside && !r.ballInside && !r.lit && starsFlash <= 0) {
                r.lit = true;
                hit();
                addScore(STAR_POINTS);
                bonus += 1_000;
                sound.play(Sfx.ROLLOVER, 0.6);
            }
            r.ballInside = inside;
            allLit &= r.lit;
        }
        if (!allLit || starsFlash > 0) return;
        starsFlash = 1.2;
        int gained = addScore(STARS_ALL_POINTS);
        if (!tilted) ballSaveTimer = Math.max(ballSaveTimer, STARS_BALL_SAVE);
        show("STARRY NIGHT! +" + formatPoints(gained) + " BALL SAVE", 2.5);
        sound.play(Sfx.MAGIC, 0.8);
    }

    /** Смерч закручивает шар вокруг центра; очки за вход и за каждый полный круг. */
    private void applyVortex(Ball b) {
        Vec2 d = b.pos.sub(Table.VORTEX_CENTER);
        double dist = d.length();
        if (dist >= Table.VORTEX_RADIUS || dist < 1) {
            b.inVortex = false;
            b.vortexTurn = 0;
            return;
        }
        double angle = Math.atan2(d.y(), d.x());
        if (!b.inVortex) {
            b.inVortex = true;
            b.vortexPrevAngle = angle;
            b.vortexTurn = 0;
            hit();
            addScore(WHIRL_ENTER_POINTS);
            sound.play(Sfx.WHOOSH, 0.6);
        }
        double step = angle - b.vortexPrevAngle;
        if (step > Math.PI) step -= 2 * Math.PI;
        if (step < -Math.PI) step += 2 * Math.PI;
        b.vortexTurn += step;
        b.vortexPrevAngle = angle;
        if (Math.abs(b.vortexTurn) >= 2 * Math.PI) {
            b.vortexTurn = 0;
            int gained = addScore(WHIRL_LOOP_POINTS);
            popup("WHIRLWIND! +" + formatPoints(gained), Table.VORTEX_CENTER.x(), Table.VORTEX_CENTER.y() - 50, CYAN, 16);
            progressMission(Mission.Type.WHIRL);
        }
        // Касательный толчок: сильнее у центра, по часовой стрелке на экране
        Vec2 tangent = new Vec2(-d.y() / dist, d.x() / dist);
        b.vel = b.vel.add(tangent.scale(VORTEX_PULL * (1 - dist / Table.VORTEX_RADIUS) * substep));
    }

    private void checkRollovers(Ball b) {
        List<Table.Rollover> lanes = table.rollovers;
        boolean allLit = true;
        for (int i = 0; i < lanes.size(); i++) {
            Table.Rollover r = lanes.get(i);
            boolean inside = b.pos.sub(r.center).length() < r.radius + b.radius;
            if (inside && !r.ballInside) {
                if (skillLane >= 0) {
                    if (i == skillLane) {
                        int gained = addScore(SKILL_SHOT_POINTS);
                        show("SKILL SHOT! +" + formatPoints(gained), 2.5);
                        coins(r.center.x(), r.center.y(), 25);
                        sound.play(Sfx.SKILL, 0.9);
                    }
                    skillLane = -1;
                }
                if (!r.lit) {
                    r.lit = true;
                    hit();
                    addScore(ROLLOVER_POINTS);
                    bonus += 2_500;
                    popup("7", r.center.x(), r.center.y() + 10, GOLD, 18);
                    sound.play(Sfx.ROLLOVER, 0.7);
                }
            }
            r.ballInside = inside;
            allLit &= r.lit;
        }
        if (allLit && lanesFlash <= 0) {
            lanesFlash = 1.2;
            multiplier = Math.min(MAX_MULTIPLIER, multiplier + 1);
            int gained = addScore(LANES_POINTS);
            show("7-7-7! MULTIPLIER x" + multiplier + " +" + formatPoints(gained), 2.5);
            coins(Table.CENTER_X, 90, 20);
            sound.play(Sfx.LANES, 0.8);
            progressMission(Mission.Type.LANES);
        }
    }

    private void hitCard(Table.Wall w, Physics.Contact c) {
        if (c.impactSpeed() < CARD_MIN_IMPACT) return;
        w.active = false;
        w.flash = 0.4;
        hit();
        int gained = addScore(CARD_POINTS);
        bonus += 5_000;
        popup(CARD_NAMES[w.index] + " +" + formatPoints(gained), 80, (w.a.y() + w.b.y()) / 2, Color.WHITE, 16);
        sound.play(Sfx.CARD, 0.9);
        progressMission(Mission.Type.CARDS);
        for (Table.Wall card : table.cards) if (card.active) return;
        gained = addScore(ROYAL_FLUSH_POINTS);
        if (!tilted) jackpot += 100_000;
        cardsResetTimer = 1.5;
        eventFlash = 1.5;
        show("ROYAL FLUSH! +" + formatPoints(gained) + " HURRY UP TO THE SLOT!", 3);
        coins(60, 600, 40);
        sound.play(Sfx.SLOT_WIN, 1.0);
        startHurryUp();
    }

    private void hitDice(Table.Wall w, Physics.Contact c) {
        if (c.impactSpeed() < DICE_MIN_IMPACT) return;
        w.flash = 0.3;
        if (diceHit[w.index]) return;
        diceHit[w.index] = true;
        hit();
        int gained = addScore(DICE_POINTS);
        sound.play(Sfx.DICE, 0.9);
        if (!diceHit[0] || !diceHit[1]) {
            popup("DIE +" + formatPoints(gained), (w.a.x() + w.b.x()) / 2, w.a.y(), Color.WHITE, 14);
            return;
        }
        diceHit[0] = diceHit[1] = false;
        gained = addScore(DICE_DOUBLE_POINTS);
        if (kickbackLit) {
            show("DOUBLES! +" + formatPoints(gained) + " HURRY UP!", 2.5);
        } else {
            kickbackLit = true;
            show("DOUBLES! KICKBACK LIT, HURRY UP!", 2.5);
        }
        startHurryUp();
    }

    private void orbit() {
        hit();
        int gained = addScore(ORBIT_POINTS * spinnerLevel);
        bonus += 2_500;
        String text = "ORBIT +" + formatPoints(gained);
        if (spinnerLevel < MAX_SPINNER_LEVEL && !tilted) {
            spinnerLevel++;
            text += " · SPINNER x" + spinnerLevel;
        }
        popup(text, 150, table.spinner.y - 20, CYAN, 15);
        progressMission(Mission.Type.ORBITS);
    }

    private void updateSpinner(double dt) {
        Table.Spinner sp = table.spinner;
        sp.angle += sp.speed * 2 * Math.PI * dt;
        sp.speed *= Math.exp(-1.8 * dt);
        int halfTurns = (int) (sp.angle / Math.PI);
        if (halfTurns != lastSpinnerHalfTurns) {
            lastSpinnerHalfTurns = halfTurns;
            addScore(SPINNER_POINTS * spinnerLevel);
            bonus += 250;
            sound.play(Sfx.SPINNER, 0.6);
        }
    }

    // ================================================================ рампа и мультибол

    private void advanceRamp(Ball b, double h) {
        RampPath ramp = table.ramp;
        b.rampDistance += RAMP_SPEED * h;
        if (b.rampDistance < ramp.length) {
            b.pos = ramp.pointAt(b.rampDistance);
            b.vel = ramp.directionAt(b.rampDistance).scale(RAMP_SPEED);
            return;
        }
        b.pos = ramp.pointAt(ramp.length);
        b.vel = ramp.directionAt(ramp.length).scale(380);
        b.rampDistance = -1;
        rampCompleted();
    }

    private void rampCompleted() {
        ramps++;
        hit();
        // Рампы подряд за короткое время стоят всё дороже
        rampChain = rampChainTimer > 0 ? rampChain + 1 : 1;
        rampChainTimer = RAMP_CHAIN_WINDOW;
        int gained = addScore(RAMP_POINTS * rampChain);
        bonus += 10_000;
        String chainName = switch (rampChain) {
            case 1 -> "RAMP";
            case 2 -> "DOUBLE RAMP!";
            case 3 -> "TRIPLE RAMP!";
            default -> "RAMP x" + rampChain + "!";
        };
        popup(chainName + " +" + formatPoints(gained), 470, 560, PINK, rampChain > 1 ? 20 : 17);
        sound.play(Sfx.RAMP_EXIT, 0.8);
        progressMission(Mission.Type.RAMPS);
        if (multiball) {
            if (!tilted) jackpot += 50_000;
            show("JACKPOT GROWS!", 1.5);
        } else if (ramps % RAMPS_FOR_MULTIBALL == 0) {
            startMultiball();
        } else if (rampChain > 1) {
            show(chainName + " +" + formatPoints(gained), 1.5);
        } else {
            show("RAMP " + ramps % RAMPS_FOR_MULTIBALL + " OF " + RAMPS_FOR_MULTIBALL, 1.5);
        }
    }

    private void startMultiball() {
        if (tilted) return;
        multiball = true;
        ballSaveTimer = MULTIBALL_SAVE_SECONDS;
        eventFlash = 2.5;
        balls.add(new Ball(Table.ROULETTE_CENTER.add(new Vec2(-10, 22)), new Vec2(-260, 200)));
        balls.add(new Ball(new Vec2(Table.CENTER_X + 25, 620), new Vec2(220, 380)));
        show("MULTIBALL!", 3);
        coins(Table.CENTER_X, 500, 30);
        sound.play(Sfx.MULTIBALL, 1.0);
    }

    // ================================================================ лунки: слот и рулетка

    private void capture(Table.Saucer s, Ball b) {
        b.held = true;
        b.vel = Vec2.ZERO;
        b.pos = s.center;
        s.held = b;
        hit();
        sound.play(Sfx.SAUCER, 0.9);
        if (s.type == Table.SaucerType.SLOT) {
            boolean collected = hurryUp > 0;
            if (collected) {
                int prize = (int) hurryUp;
                addRaw(prize);
                hurryUp = 0;
                show("COLLECTED! +" + formatPoints(prize), 2.5);
                popup("+" + formatPoints(prize), Table.CENTER_X, 450, GOLD, 30);
                coins(Table.CENTER_X, 520, 50);
                sound.play(Sfx.JACKPOT, 0.9);
            }
            if (!tilted) jackpot += 10_000;
            slot.spin(multiball);
            if (!collected) show(multiball ? "JACKPOT IS LIT!" : "SPINNING THE REELS…", 1.8);
            progressMission(Mission.Type.SLOT);
        } else if (s.type == Table.SaucerType.ROULETTE) {
            roulette.spin();
            show("NO MORE BETS…", 2.6);
            progressMission(Mission.Type.ROULETTE);
        } else {
            hatReveal = 0.9;
            s.ejectTimer = 1.8;
            show("MAGIC HAT…", 1.2);
            progressMission(Mission.Type.HAT);
        }
    }

    private void updateSaucers(double dt) {
        for (int i = slot.update(dt); i > 0; i--) sound.play(Sfx.REEL_STOP, 0.8);
        if (roulette.update(dt) > 0) sound.play(Sfx.ROULETTE_TICK, 0.5);

        if (slot.consumeFinished()) {
            awardSlot();
            table.slotSaucer.ejectTimer = 0.6;
        }
        if (roulette.consumeFinished()) {
            awardRoulette(roulette.result);
            table.rouletteSaucer.ejectTimer = 0.8;
        }
        if (hatReveal > 0) {
            hatReveal -= dt;
            if (hatReveal <= 0) mysteryAward();
        }
        for (Table.Saucer s : table.saucers) {
            s.cooldown -= dt;
            if (s.ejectTimer < 0) continue;
            s.ejectTimer -= dt;
            if (s.ejectTimer < 0) eject(s);
        }
    }

    private void eject(Table.Saucer s) {
        Ball b = s.held;
        s.held = null;
        s.cooldown = 0.7;
        if (b == null) return;
        b.held = false;
        if (s.type == Table.SaucerType.SLOT) {
            b.pos = s.center.add(new Vec2(0, 4));
            b.vel = new Vec2((rnd.nextDouble() - 0.5) * 500, 520);
        } else if (s.type == Table.SaucerType.ROULETTE) {
            roulette.hideBall();
            b.vel = new Vec2(-150 - rnd.nextDouble() * 270, 150 + rnd.nextDouble() * 200);
        } else {
            b.vel = new Vec2(180 + rnd.nextDouble() * 200, 120 + rnd.nextDouble() * 150);
        }
        sound.play(Sfx.EJECT, 0.9);
    }

    /** Приз из волшебного цилиндра — случайный, но всегда полезный. */
    private void mysteryAward() {
        if (tilted) return;
        String text;
        switch (rnd.nextInt(7)) {
            case 0 -> {
                kickbackLit = true;
                text = "KICKBACK LIT";
            }
            case 1 -> {
                if (multiplier < MAX_MULTIPLIER) {
                    multiplier++;
                    text = "MULTIPLIER x" + multiplier;
                } else {
                    text = "+" + formatPoints(addScore(100_000));
                }
            }
            case 2 -> {
                superBumpersTimer = SUPER_BUMPERS_SECONDS;
                text = "SUPER BUMPERS";
            }
            case 3 -> {
                ballSaveTimer = Math.max(ballSaveTimer, 15);
                text = "BALL SAVE 15s";
            }
            case 4 -> {
                jackpot += 250_000;
                text = "JACKPOT +250,000";
            }
            case 5 -> {
                spinnerLevel = MAX_SPINNER_LEVEL;
                text = "SPINNER x" + MAX_SPINNER_LEVEL;
            }
            default -> text = "+" + formatPoints(addScore(100_000 * (1 + missionLevel)));
        }
        show("MAGIC! " + text, 2.5);
        popup("MAGIC!", Table.HAT_CENTER.x(), Table.HAT_CENTER.y() - 40, CYAN, 22);
        coins(Table.HAT_CENTER.x(), Table.HAT_CENTER.y(), 15);
        sound.play(Sfx.MAGIC, 0.9);
    }

    private void awardSlot() {
        int[] r = slot.result;
        Vec2 at = new Vec2(Table.CENTER_X, Table.SLOT_BODY.getMinY());
        if (r[0] == SlotMachine.SEVEN && r[1] == SlotMachine.SEVEN && r[2] == SlotMachine.SEVEN) {
            awardJackpot(at);
            return;
        }
        int value;
        String text;
        if (r[0] == r[1] && r[1] == r[2]) {
            value = r[0] == SlotMachine.DIAMOND ? 150_000 : r[0] == SlotMachine.BAR ? 100_000 : 50_000;
            text = "THREE IN A ROW!";
        } else if (count(r, SlotMachine.SEVEN) == 2) {
            value = 40_000;
            text = "TWO SEVENS!";
        } else if (r[0] == r[1] || r[1] == r[2] || r[0] == r[2]) {
            value = 15_000;
            text = "A PAIR!";
        } else if (count(r, SlotMachine.CHERRY) > 0) {
            value = 7_500;
            text = "CHERRY";
        } else {
            value = 2_500;
            text = "SO CLOSE…";
        }
        int gained = addScore(value);
        show(text + " +" + formatPoints(gained), 2);
        popup("+" + formatPoints(gained), at.x(), at.y() - 10, GOLD, value >= 15_000 ? 24 : 16);
        if (value >= 15_000) {
            coins(at.x(), at.y(), value / 2_500);
            sound.play(Sfx.SLOT_WIN, 0.9);
        }
    }

    private void awardJackpot(Vec2 at) {
        if (!tilted) addRaw(jackpot);
        show("JACKPOT! +" + formatPoints(jackpot), 3.5);
        popup("JACKPOT!", at.x(), at.y() - 20, GOLD, 34);
        coins(at.x(), at.y(), 90);
        eventFlash = 3;
        jackpot = JACKPOT_BASE;
        sound.play(Sfx.JACKPOT, 1.0);
    }

    private void awardRoulette(int n) {
        Vec2 at = Table.ROULETTE_CENTER;
        if (n == 0) {
            if (!tilted) ballsLeft++;
            eventFlash = 2;
            show("ZERO! EXTRA BALL", 3);
            popup("ZERO!", at.x(), at.y() - 30, new Color(60, 255, 120), 28);
            coins(at.x(), at.y(), 40);
            sound.play(Sfx.EXTRA_BALL, 1.0);
            return;
        }
        boolean red = Roulette.isRed(n);
        int gained = addScore(n * 2_500);
        String color = red ? "RED" : "BLACK";
        if (!red && !kickbackLit && !tilted) {
            kickbackLit = true;
            show(color + " " + n + " — KICKBACK", 2.5);
        } else {
            show(color + " " + n + "  +" + formatPoints(gained), 2.5);
        }
        popup(String.valueOf(n), at.x(), at.y() - 30, red ? new Color(255, 70, 70) : Color.WHITE, 26);
        coins(at.x(), at.y(), 12);
        sound.play(Sfx.SLOT_WIN, 0.7);
    }

    private static int count(int[] values, int v) {
        int c = 0;
        for (int x : values) if (x == v) c++;
        return c;
    }

    // ================================================================ шары

    private void startBall() {
        spawnInLane();
        saveArmed = true;
        skillLane = rnd.nextInt(table.rollovers.size());
        skillTimer = 0;
    }

    private Ball spawnInLane() {
        double x = (Table.LANE_LEFT + Table.LANE_RIGHT) / 2;
        Ball b = new Ball(new Vec2(x, Table.PLUNGER_REST_Y - 11));
        balls.add(b);
        plungerCharge = 0;
        return b;
    }

    private void drainBall(Ball b) {
        balls.remove(b);
        if (ballSaveTimer > 0 && !tilted) {
            Ball saved = spawnInLane();
            if (multiball) launch(saved, 0.8);
            show("BALL SAVED!", 2);
            sound.play(Sfx.SAVE, 0.8);
            return;
        }
        if (!balls.isEmpty()) {
            if (multiball && balls.size() == 1) {
                multiball = false;
                show("MULTIBALL OVER", 2);
            }
            return;
        }
        endOfBall();
    }

    private void endOfBall() {
        multiball = false;
        combo = 0;
        superBumpersTimer = 0;
        hurryUp = 0;
        rampChain = 0;
        String bonusText = "";
        if (!tilted && bonus > 0) {
            addRaw(bonus * multiplier);
            bonusText = "BONUS " + formatPoints(bonus) + (multiplier > 1 ? " x" + multiplier : "") + " · ";
        }
        bonus = 0;
        multiplier = 1;
        tilted = false;
        tiltLevel = 0;
        ballsLeft--;
        if (ballsLeft > 0) {
            ballNumber++;
            startBall();
            show(bonusText + "BALL " + ballNumber, 3);
            sound.play(Sfx.DRAIN, 0.8);
            return;
        }
        gameOver = true;
        gameOverTime = time;
        plungerHeld = false;
        plungerCharge = 0;
        skillLane = -1;
        mission = null;
        if (score > highScore) {
            highScore = score;
            prefs.putInt("highScore", highScore);
            show(bonusText + "NEW HIGH SCORE!", 4);
        } else if (!bonusText.isEmpty()) {
            show(bonusText + "GAME OVER", 3);
        } else {
            messageTimer = 0;
        }
        sound.play(Sfx.GAME_OVER, 0.9);
    }

    // ================================================================ очки и эффекты

    /** Начисляет очки с учётом множителя дорожек и комбо; возвращает начисленное. */
    private int addScore(int base) {
        if (tilted) return 0;
        int gained = base * multiplier * comboMultiplier();
        addRaw(gained);
        return gained;
    }

    /** Начисляет очки как есть (награды, джекпот, бонус); счёт не переполняется. */
    private void addRaw(int points) {
        score = (int) Math.min(Integer.MAX_VALUE, (long) score + points);
        checkRank();
    }

    private void checkRank() {
        boolean promoted = false;
        while (rank + 1 < RANKS.length && score >= RANK_SCORES[rank + 1]) {
            rank++;
            promoted = true;
        }
        if (promoted) {
            show("NEW RANK: " + RANKS[rank], 2.5);
            sound.play(Sfx.RANK, 0.8);
        }
    }

    private void show(String text, double seconds) {
        message = text;
        messageTimer = seconds;
        messageAge = 0;
    }

    private void popup(String text, double x, double y, Color color, float size) {
        if (tilted) return;
        popups.add(new Popup(text, x, y, color, size));
    }

    private void sparks(double x, double y, Color color, int n) {
        for (int i = 0; i < n && particles.size() < 600; i++) {
            double a = rnd.nextDouble() * 2 * Math.PI, v = 80 + rnd.nextDouble() * 220;
            particles.add(new Particle(x, y, Math.cos(a) * v, Math.sin(a) * v, 0.3 + rnd.nextDouble() * 0.3, color, false));
        }
    }

    private void coins(double x, double y, int n) {
        for (int i = 0; i < n && particles.size() < 600; i++) {
            double a = -Math.PI / 2 + (rnd.nextDouble() - 0.5) * 2.2, v = 250 + rnd.nextDouble() * 450;
            Particle p = new Particle(x, y, Math.cos(a) * v, Math.sin(a) * v, 1.2 + rnd.nextDouble() * 0.8, GOLD, true);
            p.spin = rnd.nextDouble() * 6;
            particles.add(p);
        }
    }

    private void updateEffects(double dt) {
        for (Iterator<Popup> it = popups.iterator(); it.hasNext(); ) {
            Popup p = it.next();
            p.age += dt;
            p.y -= 45 * dt;
            if (p.age > 1.2) it.remove();
        }
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.vy += (p.coin ? 900 : 250) * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.spin += 12 * dt;
            p.life -= dt;
            if (p.life <= 0) it.remove();
        }
    }
}
