# Jackpot Pinball

A pinball game in pure Java Swing, drawn in the style of 1930s rubber-hose cartoons: hand-inked outlines,
watercolor paper, pie-eyed characters and an old-film vignette. No libraries, no image or sound files —
every sprite is drawn in code and every sound is synthesized at startup.

## Running

Requires **JDK 21** or newer.

```bash
javac -d out src/pinball/*.java
java -cp out pinball.Main
```

In IntelliJ IDEA: open the folder, set the project SDK to JDK 21, mark `src` as *Sources Root*
(right-click → *Mark Directory as*) and run `pinball.Main`.

The window fits itself to the screen and can be resized — the picture scales. The high score is kept
between runs (`java.util.prefs`).

## Controls

| Key | Action |
|---|---|
| `Z` / `←` | left flipper |
| `/` / `→` | right flipper |
| `Space` / `↓` | hold to pull the plunger, release to launch |
| `↑` / `N` | nudge the table (too many nudges → TILT) |
| `P` | pause |
| `M` | sound on / off |
| `F` | "live film": line boil and moving grain (off by default) |
| `Enter` | new game after game over |

## What's on the table

| Target | What it does |
|---|---|
| **Chip bumpers** | Characters that watch the ball. Light all four for **Super Bumpers** (×5 for 20 s). |
| **7-7-7 lanes** | Light all three to raise the multiplier (up to ×5). Flippers rotate the lit lanes. |
| **Skill shot** | Launch the ball into the blinking lane for 150,000. |
| **Slot machine** | Sink the ball in front of it to spin the reels. 7-7-7 pays the **jackpot**. |
| **Roulette** | A real 37-pocket wheel. Zero gives an extra ball, black lights the kickback. |
| **High Roller ramp** | Three ramps start **multiball**; ramps in a row pay double and triple. |
| **Card targets 10-J-Q-K-A** | Drop all five for a **Royal Flush** and a *Hurry Up* prize. |
| **Dice** on the slot machine | Hit both for doubles: lights the kickback and a *Hurry Up* prize. |
| **Spinner orbit** | Every orbit raises the spinner value. |
| **Bells** | Ring all three for *Ring-a-ding*. |
| **Duck shooting gallery** | Three moving ducks; knock them all down for *Sharpshooter*. |
| **Magic hat** | Catches the ball, a rabbit pops out with a random prize. |
| **Whirlwind** | Spins the ball around; points for every full loop. |
| **Star rollovers** | Light all three for a 10-second ball save. |
| **Kickback** | A boxing glove under the flippers that punches a draining ball back. |

### Scoring

- **Combo** — hits in quick succession raise a combo multiplier up to ×5; it resets after 2.5 s without a hit.
- **Missions** — a timed task is always running (e.g. "Bumpers: 12", "Ramps: 2"); each completed mission is
  worth more than the last.
- **Hurry Up** — a 500,000 prize that melts every second until you sink the ball in the slot machine.
- **Jackpot** grows with bumper hits and ramps during multiball.
- **End-of-ball bonus** is multiplied by the lane multiplier.
- Ranks go from *Rookie* to *Legend*.

## Project structure

```
src/pinball/
  Main.java            window
  GamePanel.java       game loop, keyboard, scaling to the window
  Game.java            rules and physics step: scoring, missions, combo, multiball
  Table.java           table geometry: walls, bumpers, targets, saucers, ramp, flippers
  Physics.java         ball vs. capsule collision (walls, flippers, round targets)
  Ball, Flipper, Vec2  basic physics objects
  RampPath.java        Catmull-Rom path the ball follows on the ramp
  SlotMachine.java     reels
  Roulette.java        roulette wheel
  Mission.java         timed missions
  SoundEngine.java     synthesized sound effects and mixer
  TableRenderer.java   drawing the table
  HudRenderer.java     right-hand panel: odometer counters, mission card, messages
  MarqueeRenderer.java cabinet frame with chasing bulbs
  FilmFilter.java      film grain and vignette
  Toon.java            cartoon style: ink outlines, "boiling" lines, palette, fonts
  Render.java          sprite cache, glow, text helpers
```

## Technical notes

- Physics runs at a fixed 240 Hz with 4 substeps, so fast balls don't tunnel through walls or flippers.
- Everything static is pre-rendered into layers at 2× resolution; dynamic objects are cached sprites. A frame
  takes about 6 ms on a Retina Mac.
- Fonts: Rockwell, Copperplate and Georgia (bundled with macOS); other systems fall back to a serif font.
