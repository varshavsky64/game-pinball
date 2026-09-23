package pinball;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/** Окно игры: игровой цикл, клавиатура и масштабирование картинки под размер окна. */
final class GamePanel extends JPanel {
    private static final int FRAME = MarqueeRenderer.FRAME;
    private static final int HUD_X = FRAME + Table.WIDTH + MarqueeRenderer.GAP;
    static final int LOGICAL_WIDTH = HUD_X + HudRenderer.WIDTH + FRAME;
    static final int LOGICAL_HEIGHT = FRAME + Table.HEIGHT + FRAME;
    private static final double STEP = 1.0 / 240;

    private final SoundEngine sound = new SoundEngine();
    private final Game game = new Game(sound);
    private final TableRenderer tableRenderer = new TableRenderer(game.table);
    private final HudRenderer hudRenderer = new HudRenderer();
    private final MarqueeRenderer marquee = new MarqueeRenderer(LOGICAL_WIDTH, LOGICAL_HEIGHT, FRAME + Table.WIDTH);
    private final FilmFilter film = new FilmFilter(LOGICAL_WIDTH, LOGICAL_HEIGHT);

    private long lastTime = System.nanoTime();
    private double accumulator;
    private boolean nudgeHeld;

    GamePanel() {
        setPreferredSize(initialSize());
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(new Keys());
        new Timer(10, e -> tick()).start();
    }

    /** Окно подгоняется под экран: стол высокий, на ноутбуке он будет уменьшен. */
    private static Dimension initialSize() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        double scale = Math.min(1.0, Math.min(
                (screen.height - 40) / (double) LOGICAL_HEIGHT,
                (screen.width - 40) / (double) LOGICAL_WIDTH));
        return new Dimension((int) (LOGICAL_WIDTH * scale), (int) (LOGICAL_HEIGHT * scale));
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min((now - lastTime) / 1e9, 0.05);
        lastTime = now;
        accumulator += dt;
        while (accumulator >= STEP) {
            game.update(STEP);
            accumulator -= STEP;
        }
        repaint();
        Toolkit.getDefaultToolkit().sync();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Render.quality(g2);
        double scale = Math.min(getWidth() / (double) LOGICAL_WIDTH, getHeight() / (double) LOGICAL_HEIGHT);
        g2.translate((getWidth() - LOGICAL_WIDTH * scale) / 2, (getHeight() - LOGICAL_HEIGHT * scale) / 2);
        g2.scale(scale, scale);
        g2.clipRect(0, 0, LOGICAL_WIDTH, LOGICAL_HEIGHT);

        marquee.draw(g2, game);

        Graphics2D table = (Graphics2D) g2.create();
        table.translate(FRAME, FRAME);
        table.clipRect(0, 0, Table.WIDTH, Table.HEIGHT);
        if (game.shake > 0) table.translate(Math.sin(game.time * 90) * 5 * game.shake / 0.15, 0);
        tableRenderer.draw(table, game);
        table.dispose();

        Graphics2D hud = (Graphics2D) g2.create();
        hud.translate(HUD_X, FRAME);
        hudRenderer.draw(hud, game, sound);
        hud.dispose();

        film.draw(g2, game.time);
        g2.dispose();
    }

    private final class Keys extends KeyAdapter {
        @Override
        public void keyPressed(KeyEvent e) {
            setKey(e.getKeyCode(), true);
        }

        @Override
        public void keyReleased(KeyEvent e) {
            setKey(e.getKeyCode(), false);
        }

        private void setKey(int code, boolean down) {
            switch (code) {
                case KeyEvent.VK_LEFT, KeyEvent.VK_Z, KeyEvent.VK_A -> game.setLeftFlipper(down);
                case KeyEvent.VK_RIGHT, KeyEvent.VK_SLASH, KeyEvent.VK_L -> game.setRightFlipper(down);
                case KeyEvent.VK_SPACE, KeyEvent.VK_DOWN -> game.setPlunger(down);
                case KeyEvent.VK_UP, KeyEvent.VK_N -> {
                    // Автоповтор клавиши не должен толкать стол много раз
                    if (down && !nudgeHeld) game.nudge();
                    nudgeHeld = down;
                }
                case KeyEvent.VK_ENTER -> {
                    if (down && game.gameOver) game.newGame();
                }
                case KeyEvent.VK_P -> {
                    if (down) game.togglePause();
                }
                case KeyEvent.VK_M -> {
                    if (down) sound.toggle();
                }
                case KeyEvent.VK_F -> {
                    if (down) Toon.animatedFilm = !Toon.animatedFilm;
                }
                default -> {
                }
            }
        }
    }
}
