# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

Pinball game in pure Java Swing (JDK 21, no dependencies, no build tool). 1930s cartoon ("Cuphead-like") style.
All graphics are drawn in code, all sounds are synthesized in `SoundEngine` — there are no asset files.

## Build and run

```bash
javac -d out src/pinball/*.java          # compile (add -Xlint:all -Xlint:-serial to check warnings)
java -cp out pinball.Main                 # run
```

`out/` is git-ignored. There are no unit tests — see "Verifying changes".

## Architecture

- `GamePanel` — Swing `Timer` → fixed-step loop (`Game.update(1/240)`), key handling, draws everything into a
  logical canvas of `LOGICAL_WIDTH × LOGICAL_HEIGHT` scaled to the window.
- `Game` — all rules and the physics step (4 substeps per update). Point values, timings and balance live in
  constants at the top of the file. Package-private fields are read directly by the renderers.
- `Table` — static geometry. Coordinates are logical pixels, y points down, table is 600×1080.
  `Wall.kind` (`PLAIN`, `SLING`, `CARD`, `DICE`, `GATE`) decides how `Game` reacts to a hit.
- `Physics.collide` — ball vs. capsule (segment with thickness); circles are capsules with `a == b`.
  Pass `pivot` and `omega` for rotating objects (flippers).
- Rendering: `TableRenderer` (table), `HudRenderer` (right panel), `MarqueeRenderer` (bulb frame),
  `FilmFilter` (grain/vignette overlay), style helpers in `Toon`, low-level helpers and the sprite cache in `Render`.

## Conventions

- **Comments and javadoc are in Russian; all user-visible strings are in English.** Keep both that way.
- The user communicates in Russian — reply in Russian.
- Use the `Toon` palette and helpers (`Toon.toon`, `Toon.ink`, `Toon.titleText`, `Toon.eye`, `Toon.bulb`) for new art
  so it matches the ink-and-watercolor style.
- Static art goes into pre-rendered layers (`TableRenderer.paintBackground` etc.); there is one layer per
  `Toon.BOIL_FRAMES` variant, pass the variant as the wobble seed.
- Dynamic art uses `Render.sprite(key, ...)`. The key must encode every parameter that changes the picture
  (color, size, lit state, boil variant). Keys are cached forever — never put continuously changing values in them.
- Format points with `Game.formatPoints` (US grouping, `250,000`).

## Gotchas learned the hard way

- **No full-screen flashing.** The user found screen flicker painful. Never flash large areas or strobe faster
  than ~3 Hz; film "boil" and moving grain stay off by default (`Toon.animatedFilm`, key `F`).
- **Flat horizontal ledges trap the ball** forever (it happened on top of the ramp pocket). Give tops a slope or a peak.
- **Balls must not leave the playfield** except between the flippers. The side outlanes were removed on purpose.
- `Game.physicsStep` iterates `balls` by index: multiball adds balls during the step (a for-each crashed with
  `ConcurrentModificationException`).
- `Render.sprite` must not use `computeIfAbsent`: sprite painters draw nested sprites (glows) and modify the cache.
- Saucers need `cooldown` after ejecting, or the ejected ball is captured again immediately.
- The launch-lane gate is one-way (`Wall.oneWayNormal`).
- The high score is stored in `java.util.prefs` (node of package `pinball`). Any simulation or test that ends a game
  must save and restore `highScore`, otherwise it overwrites the player's real record.
- Performance budget: a frame should stay well under 16 ms on Retina. Measure on screen, not only headless —
  the Metal pipeline behaves differently from software rendering to a `BufferedImage`.

## Verifying changes

Write throwaway classes in `package pinball` outside the repo (e.g. the scratchpad), compile them against `out/`:

```bash
javac -cp out -d /tmp/sim/out Sim.java && java -Djava.awt.headless=true -cp out:/tmp/sim/out pinball.Sim
```

- **Look at it:** render a frame to PNG — `MarqueeRenderer`, then `TableRenderer.draw` translated by
  `MarqueeRenderer.FRAME`, then `HudRenderer.draw` at `FRAME + Table.WIDTH + GAP`, then `FilmFilter` — and view the image.
- **Play it:** a bot that launches the ball and flips when a ball is near the flippers, running several games.
  Check that no ball is ever outside the playfield outline and that no free ball stays within ~15 px for 8 s (stuck).
- After UI changes, run the app for a few seconds and check stderr for exceptions.
