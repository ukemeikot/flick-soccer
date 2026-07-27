# Stylized 3D Arcade Football (Godot 4) — Implementation Plan

A real-time, DLS-inspired **5-a-side arcade football** game in **stylized 3D**, built in **Godot 4**.
This supersedes the turn-based flick game (kept in git history). v1 goal: a fun, playable
**Exhibition Match**.

> **Confirmed decisions**
> - Engine: **Godot 4** (GDScript + text scenes). New project; the KMP/Compose flick game is archived.
> - Visuals: **stylized 3D primitives** (capsule/low-poly players, primitive stadium) — no external art.
> - Format: **5-a-side** (4 outfield + 1 GK per team) on a small pitch.
> - v1 scope: **playable Exhibition Match first** (arcade rules; no offside/fouls yet).
> - Mode name: **Exhibition Match** (not "vs AI"). 1P (you vs CPU-controlled side) or local 2P.

> **Working reality**
> - You need **Godot 4.x** installed to open/run/export the project.
> - I author `.gd` scripts, `.tscn` scenes, and `project.godot` as text. **I cannot run the Godot
>   editor or see the result**, so unlike the flick game there are no automated build/test gates from
>   me — you run each milestone and give feedback. Where possible I'll add GDScript unit tests
>   (GUT / Godot's built-in) and headless `--check-only` script validation you can run.

---

## 1. Why Godot fits this
Godot gives us, for free, what we hand-rolled before: **3D physics** (CharacterBody3D / RigidBody3D),
collision, a **perspective camera**, **input** (touch + keyboard + gamepad via InputMap), **UI** nodes
for HUD/menus, and **exporters** for Android / iOS / desktop / web. So the work is game logic and
feel, not engine plumbing.

## 2. Tech stack
| Concern | Choice |
|---|---|
| Engine | Godot 4.3+ (stable) |
| Language | GDScript (fast to iterate; C# optional later) |
| Physics | Godot 3D physics (Jolt backend) — players `CharacterBody3D`, ball `RigidBody3D` |
| Rendering | Stylized 3D primitives (CapsuleMesh players, sphere ball, box/plane pitch & stands), Forward+/Mobile renderer |
| Camera | `Camera3D` broadcast rig that follows the ball with smoothing |
| Input | Godot `InputMap`: on-screen virtual joystick + action buttons (touch), WASD/arrows + keys (desktop), gamepad |
| UI | `CanvasLayer` + `Control` nodes (menus, HUD, pause, radar) |
| Audio | Godot `AudioStreamPlayer` (procedural or CC0 SFX: kick, whistle, crowd, net) |
| Persistence | `user://` JSON (settings, last result) via `FileAccess` |
| Targets | Desktop (Windows/macOS/Linux), Android, iOS |

## 3. Project structure (new Godot project)
Lives in the repo under **`soccer3d/`** (open *that* folder in Godot). The old KMP flick game moves to
**`archive/flick-kmp/`**.

```
soccer3d/
├── project.godot
├── scenes/
│   ├── Main.tscn                 # root: menu ↔ match state
│   ├── Match.tscn                # pitch + teams + ball + camera + HUD
│   ├── Pitch.tscn                # ground, lines, goals, walls, stands (primitives)
│   ├── Player.tscn               # CharacterBody3D + mesh + label (number)
│   ├── Ball.tscn                 # RigidBody3D + sphere
│   └── ui/  Menu.tscn  Hud.tscn  Pause.tscn  Joystick.tscn
├── scripts/
│   ├── match/  MatchManager.gd  Team.gd  Formation.gd  Rules.gd  BallState.gd
│   ├── player/ Player.gd  PlayerFsm.gd  HumanController.gd  AiController.gd  Goalkeeper.gd
│   ├── input/  InputRouter.gd  VirtualJoystick.gd
│   ├── camera/ BroadcastCamera.gd
│   ├── ui/     Hud.gd  Menu.gd  Pause.gd  Radar.gd
│   └── util/   Steering.gd  MathX.gd  Save.gd
├── assets/ (generated materials, procedural SFX, kit colors)
└── tests/  (GUT specs for Rules/Formation/Steering)
```

## 4. Core systems

### 4.1 Ball & players (physics)
- **Ball:** `RigidBody3D`, sphere collider, tuned mass/friction/bounce; kicked by applying impulses
  (ground pass = mostly horizontal; lofted pass/shot/chip = + vertical). Continuous collision on.
- **Player:** `CharacterBody3D` moved via steering (desired velocity → `move_and_slide`), with a small
  **state machine** (`Idle`, `Chase`, `Dribble`, `Pass`, `Shoot`, `Tackle`, `Return`). "Possession" =
  the player closest to a slow ball within a control radius; dribbling nudges the ball ahead in the
  facing direction. Facing = movement/aim direction.
- **Kick model:** power from a tap (short) → hold (charged) with a HUD meter; direction from
  facing/joystick; recipient selection for passes (best teammate in a facing arc, weighted by distance
  and openness).

### 4.2 Teams, formations, roles
- 5-a-side per team: **GK + 4 outfield** in a 1-2-1 or 2-1-1 shape (data-driven, mirrored per side).
- Each outfield player has a **home zone** and a **role** (defender/mid/forward) driving off-ball
  positioning; the team shifts as a block toward the ball ("formation follows the ball").

### 4.3 AI (real-time behavior)
- **On-ball AI:** advance toward goal, pass under pressure, shoot in range, shield/dribble.
- **Off-ball AI:** the nearest defender pressures the ball; others **mark** opponents / cover zones;
  attackers make **support runs** into space and keep spacing.
- **Goalkeeper:** stay on the line between ball and goal, rush out for close 1v1s, save/parry, distribute.
- **Difficulty (Exhibition setting, not a mode):** tunes reaction time, pass accuracy, pressing
  intensity, decision noise — Casual / Normal / Pro.

### 4.4 Match flow & rules (arcade v1)
- Kickoff → play → **goal** (Area3D behind the line) → celebrate → restart at center.
- Ball **out of play** → **auto** throw-in / corner / goal-kick (no manual set-piece control in v1).
- Two short halves with a match **clock** (configurable, e.g. 2×3 min); score; **full-time** result.
- **No offside/fouls in v1** (arcade). Draws allowed in Exhibition; optional golden-goal toggle.

### 4.5 Controls
- **Mobile:** left **virtual joystick** = move; buttons = **Pass / Through / Shoot** (context-sensitive
  on defense: **Tackle / Switch player / Pressure**); hold Shoot to charge; auto **sprint** or a sprint
  toggle.
- **Desktop:** WASD/arrows move; keys for pass/through/shoot/tackle/switch/sprint; **gamepad** supported.
- **Player switching:** auto-switch to the player nearest the ball on defense; manual cycle button.

### 4.6 Camera & presentation
- `BroadcastCamera` follows the ball at a tilt with lookahead + smoothing and soft bounds.
- Stylized players: colored capsules with a team **kit** color, a small **number** label, a facing
  wedge, and a simple bob/lean while running; ball with a trail; goal net wobble; stadium as tiered
  boxes. HUD: score, clock, **radar minimap**, stamina, shot-power meter.

## 5. Phased milestones
Each milestone is something **you can run in Godot** and judge. (No auto CI from me — you run it.)

- **P0 — Project & pitch:** Godot project, `Pitch.tscn` (ground + lines + goals + walls), a ball you can
  nudge, a `BroadcastCamera` following it, and a debug free-move player. *You see a 3D pitch with a rolling ball.*
- **P1 — Control one player + ball:** joystick/keyboard movement, possession + dribble, basic **kick**
  (pass/shoot by direction+power), camera follow. *You can run around and knock the ball into the net.*
- **P2 — Actions:** charged shot with power meter, ground/through pass with recipient selection,
  sprint + stamina, tackle/slide, headers for aerial balls.
- **P3 — Teams, formations & AI:** 5v5 with formation positioning; opponent AI (press/mark/intercept),
  teammate AI (support/spacing), **goalkeeper AI**, player switching. *A full match plays itself if you don't touch it.*
- **P4 — Rules & flow:** kickoff, goals + restart, ball-out auto set-pieces, halves + clock, score,
  full-time. *A complete Exhibition Match start-to-finish.*
- **P5 — UX & presentation:** Menu (Exhibition setup: teams/kits, 1P/2P, difficulty, half length),
  HUD + radar + meters, pause/resume, goal celebration, SFX (kick/whistle/net/crowd), settings, save
  last result. *Feels like a game, not a tech demo.*
- **P6 — Platform bring-up & polish:** Android touch build + export, desktop build, iOS export notes;
  input/feel tuning; performance pass. *Installable on your phone and desktop.*
- **P7 (optional, large) — Career/Manager meta:** squads, transfers, upgrades, seasons — a separate epic.

## 6. Repo strategy (pivot)
- Add the Godot project under `soccer3d/`; move the KMP flick game to `archive/flick-kmp/` (kept, not
  deleted). Update root `README.md` to describe the new game and how to open it in Godot.
- Commit per milestone. `.gitignore` for Godot (`.godot/`, `export_presets.cfg` secrets, exports).

## 7. Risks & mitigations
1. **No agent-side visual verification.** *Mitigation:* small, runnable milestones; GDScript unit tests
   for pure logic (Rules/Formation/Steering); optional headless `godot --check-only` script validation;
   tight feedback loops with you.
2. **Game feel is iterative.** Movement/kick/AI "feel" needs playtesting. *Mitigation:* expose all feel
   constants in one `Tuning.gd`/resource; short tuning passes with your feedback.
3. **Mobile controls & export.** Touch feel and Android signing take iteration. *Mitigation:* build
   desktop-first (keyboard) to nail mechanics, then add touch; document Android keystore/export steps.
4. **AI complexity for 5v5.** Real-time team AI is the hardest part. *Mitigation:* start with simple,
   readable behaviors (pursue/mark/support) and layer sophistication; 5-a-side keeps agent counts low.
5. **iOS** still needs a Mac to export/sign (Godot exports the Xcode project; you build it there).

## 8. Open questions (defaults if unanswered)
1. Half length / clock speed? **Default:** 2 × 3 minutes, accelerated clock.
2. 1P vs 2P for the first Exhibition build? **Default:** 1P (you vs CPU) first, add local 2P in P5.
3. Team identities? **Default:** pick-two-kit-colors ("Home/Away"), no real clubs/branding.
4. Godot version? **Default:** latest stable 4.x, Forward+ on desktop / Mobile renderer on Android.
5. Manual sprint toggle vs auto-sprint? **Default:** hold-to-sprint (button/key), stamina-limited.
