# Soccer — 3D Football + Flick Soccer (Godot 4)

A stylized 3D arcade football game (Dream League Soccer–inspired, **5-a-side**) built in **Godot 4**,
plus a turn-based **Flick Soccer** table game — both behind one in-app menu. Runs on **Windows
desktop** and **Android** from a single Godot project in [`soccer3d/`](soccer3d/).

> The original **Kotlin/Compose Multiplatform** flick game lives on as a reference implementation —
> see [Legacy: Kotlin flick game](#legacy-kotlin-flick-game) at the bottom.

---

## The two games

Launch boots to a menu — **Choose a game**:

| Mode | What it is |
|---|---|
| **3D Football — Exhibition** | A single 5-a-side match. Pick your club, the opponent, difficulty, and half length, then kick off. |
| **Season** | A full round-robin league across 6 clubs — play your fixtures (you're always home), the rest are auto-simulated, live standings decide the champion. Progress is saved to disk. |
| **Flick Soccer** | Turn-based table football: drag from one of your discs and release to flick it at the ball. Physics pucks, goals, CPU opponent. |

---

## 3D Football — features

**Controls & ball feel**
- Momentum-based movement (acceleration, weight, finite turn rate) — no snapping.
- Curved/finesse shots via a real **Magnus** effect (push the stick across the shot to bend it).
- **Through-balls** played into space ahead of a runner.
- Cushioned **first touch** instead of an instant trap.
- Charged shot power (hold Shoot).

**Players & actions**
- Real **rigged, animated humanoid** players (RobotExpressive, CC0) in team kits — Idle / Running /
  Punch(kick) clips driven by game state; model auto-scaled to height.
- Per-position **attributes** (pace / shooting / passing / defending) — a striker ≠ a defender.
- **Slide tackle** that either wins the ball or concedes a foul.
- **Goalkeeper** shot-prediction and diving.
- Manual **player switching** with an active-player marker.

**Team AI & tactics**
- Possession-based team shape: push up in attack, drop and **mark** when defending.
- Forward off-ball runs, pressing, formation spread.

**Rules & set pieces**
- Out of play → **throw-in / corner / goal kick** (decided by last touch).
- **Fouls → free kicks**, fouls in the box → **penalties**.
- **Yellow / red cards** with send-offs.
- Two halves, match clock, and **added (stoppage) time**.

**Presentation**
- Pitch **mowing stripes**, goal frame + net, stands.
- **Goal celebration** hold with a crowd **roar**, looping crowd **ambience**.
- **Commentary** announcer lines on key events.
- Broadcast camera that leads the ball, **radar** minimap, stamina & shot-power meters, club-named scoreboard.
- Procedural audio (kick / whistle / goal / roar / crowd) — no audio files.

---

## Controls

**Desktop (keyboard)**

| Action | Key |
|---|---|
| Move | WASD / Arrow keys |
| Sprint | Shift |
| Pass | J / Space |
| Through-ball | I / U |
| Shoot (hold to charge) | K |
| Tackle / slide | L |
| Switch player | Q / Tab |
| Pause | Esc |

**Mobile (on-screen)** — left **virtual joystick** + right buttons: **SHOOT** (hold), **PASS**,
**THRU**, **SPRINT**, **TACKLE**, **SWITCH**. Shown automatically on touch devices.

---

## Running & building

Requires **Godot 4.7.1** (standard, not .NET).

**Play in the editor**
```
Open soccer3d/ in Godot 4.7.1 and press Play (boots res://scenes/MainMenu.tscn).
```

**Headless import / smoke test** (used in CI-style checks)
```bash
godot --headless --path soccer3d --import
godot --headless --path soccer3d res://scenes/Match.tscn --quit-after 600
```

**Export builds** (presets: `Windows Desktop`, `Android`)
```bash
godot --headless --path soccer3d --export-release "Windows Desktop" soccer3d/build/Soccer3D.exe
godot --headless --path soccer3d --export-debug   "Android"         soccer3d/build/Soccer3D.apk
```
Android needs the 4.7.1 export templates, a debug keystore, and ETC2 texture compression enabled
(already set in the project). Install to a device with `adb install -r soccer3d/build/Soccer3D.apk`.
Build outputs under `soccer3d/build/` are git-ignored.

---

## Project structure

```
soccer3d/
├── project.godot                 # autoloads, input map, landscape + touch settings
├── scenes/                       # MainMenu, Match, FlickMatch
├── scripts/
│   ├── Match.gd                  # match loop: teams, rules, set pieces, clock, presentation
│   ├── Ball.gd                   # ball physics + Magnus curve
│   ├── player/Player.gd          # movement, actions, AI, GK, model + animation state machine
│   ├── flick/FlickMatch.gd       # turn-based flick game
│   ├── input/                    # InputActions, Touch singleton, TouchControls, VirtualJoystick
│   ├── ui/                       # MainMenu (+ Season screen), Hud, Radar
│   ├── camera/BroadcastCamera.gd
│   ├── audio/Sfx.gd              # procedural SFX + crowd ambience
│   └── match/MatchConfig.gd      # config, clubs, Season league + persistence
├── assets/players/               # player.glb (RobotExpressive) + ATTRIBUTION.md
└── tools/                        # headless dev scripts (inspect/measure model, season test)
```

---

## Assets & attribution

- **player.glb** = **RobotExpressive** by Tomás Laulhé, modified by Don McCurdy — **CC0** (from the
  three.js examples). See [`soccer3d/assets/players/ATTRIBUTION.md`](soccer3d/assets/players/ATTRIBUTION.md).
- All audio is synthesized procedurally in code (no sound files).
- Pitch, stadium, goals, and UI are built from Godot primitives.

---

## Roadmap / ideas

- Football-specific kick/tackle/dive animations (current kick reuses a punch clip).
- Goal replays, richer commentary, more clubs, a formation editor.
- Separate skin vs kit materials on the player model; higher-fidelity models.

---

## Legacy: Kotlin flick game

The repo began as **Flick Soccer**, a turn-based physics game in **Kotlin Multiplatform + Compose
Multiplatform** (Android / iOS / Desktop), feature-complete through M0–M7 with a Compose Canvas 2.5D
renderer, AI, and procedural audio. It is retained as the reference for the Godot Flick Soccer port.
The `composeApp/`, `iosApp/`, and Gradle files belong to that version; see
[`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) for its architecture.

## License

Code: TBD. Bundled model asset: CC0 (see attribution above).
