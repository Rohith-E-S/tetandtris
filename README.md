# Tetandris

Minimal Android Tetris ported 1:1 from the [abusch8/Tetris](https://github.com/abusch8/Tetris) terminal UI, then built out: game modes, guideline back-to-back scoring, run stats, and an adaptive layout that gives the board all the space the screen can spare.

## Build

```
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Modes

- **Marathon** — classic endless climb, start level 1–15
- **Sprint** — clear 40 lines as fast as you can (best time is kept)
- **Ultra** — score as much as possible in 2:00

## Controls

- tap right 2/3 — rotate clockwise, tap left 1/3 — rotate counter-clockwise
- double tap or fast flick down — hard drop
- long press — hold
- horizontal drag — move (smooth DAS)
- vertical drag — soft drop while held
- `II` (top-left corner) pauses, `?` (top-right) explains
- resuming runs a 3-2-1 countdown; back button pauses
- controls mode (Gestures / Both / Buttons) is picked from the start & pause menus

## Layout

The canvas is the UI. Portrait: a tight HUD band (hold | score | next) above a near-full-width board. Landscape/tablet: the board takes the left and a stats column (hold, next queue, score, clock, records) sits on the right. Everything is drawn on one canvas; menus are the only composables on top.

## Mechanics (faithful to the TUI, plus guideline extras)

- 7-bag randomizer, 3-piece next queue, hold, ghost piece
- SRS wall kicks, guideline gravity `(0.8-(lvl-1)*0.007)^(lvl-1)`
- 500ms lock delay with 15 resets, 125ms line-clear flash
- Scoring: 100/300/500/800 (single/double/triple/tetris) x level,
  T-spins 800/1200/1600 x level, combos +50 x combo x level,
  back-to-back Tetris/T-spin chains x1.5
- xterm-256 piece colors (I cyan, J blue, L orange, O yellow, S green, T magenta, Z red)
- per-mode records, run stats (time, pieces, tetrises, T-spins, max combo, B2B chains)
