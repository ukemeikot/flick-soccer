# Flick Soccer — Design Brief (source of truth)

> This is the original design brief. The adapted, phased build plan lives in
> [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md); a summary is in [README.md](README.md).

## 1. Concept
Turn-based physics soccer rendered in real-time 3D (OpenGL). Two teams of **5 discs** plus one ball on a
100 × 160 pitch. On your turn, drag back from one of your discs (slingshot) and release to flick it. A
**Ground / Chip** toggle selects the shot type; a chip lofts the ball over defenders and must pass **under the
crossbar** of a 3D goal to score. Discs move under 2D plane physics; the ball is a true sphere with a height
axis (gravity, bounce, air drag). Turn passes when everything is at rest.

- Score when the ball fully crosses the opponent's goal line inside the mouth **and below crossbar height**;
  too-high lofted shots clang off the bar or rebound.
- Match ends at **3 goals** or a **30-turn limit** (15/side). Draws allowed in local 2P; sudden-death vs AI.
- **Modes (v1):** single-player vs AI (Easy/Medium/Hard); local 2-player pass-and-play.
- **In scope:** menus, settings, SFX, haptics (mobile), difficulty, high-score/history persistence, simple
  animations, pause/resume.
- **Out of scope (v1):** online multiplayer, monetization, accounts, tournaments, disc customization beyond team color.
- **No authentication:** the app boots directly to the menu; there is no login/account gate.

## 2. Targets & stack
Kotlin (latest stable); Android / iOS / Desktop (JVM). Compose Multiplatform for menus/HUD. Match rendering via
hand-rolled OpenGL (ES 3.0 feature subset) with a shared renderer in `commonMain` behind a ~40-function `Gl`
interface — one thin `actual` per platform (Android `GLES30`, iOS GLES cinterop via K/N, Desktop LWJGL 3 +
`lwjgl3-awt` `AWTGLCanvas`). Hand-written 3D math (`Mat4`/`Vec3`). MVVM with unidirectional data flow. Custom
2.5D physics engine (pure Kotlin, deterministic, unit-testable). Koin DI. `multiplatform-settings` +
`kotlinx.serialization` persistence. Coroutines + `StateFlow`. `expect/actual` audio and haptics. Sealed-class
navigation. `kotlin.test` for engine/ViewModel tests.

## 3. Architecture (MVVM)
Strict downward layering: UI (Compose + GL) → ViewModel (commonMain) → Domain (pure Kotlin) → Data
(commonMain + expect/actual). Views are dumb; ViewModels own immutable `StateFlow` state + `SharedFlow`
effects; domain is platform-agnostic and deterministic; repositories abstract persistence behind Koin interfaces.

Core domain state: `Vec2`; `Body(id, kind, position, velocity, z, vz, radius, mass)`;
`PitchSpec(width, height, goalMouthWidth, crossbarHeight, discHeight, gravity, groundRestitution,
wallRestitution, discRestitution, linearDampingPerSec, airDampingPerSec, stopSpeedEpsilon)`;
`MatchState(bodies, scoreA, scoreB, turn, turnNumber, phase, winner)`. World units only in domain; pixels only in UI.

`GameUiState(match, aim, vsAi, difficulty, isPaused, interpolationAlpha)`.
`GameEffect`: KickSound, WallBounceSound, GroundBounceSound, CrossbarClangSound, GoalScored(by), WhistleEnd, HapticTick.

## 4. Physics
Fixed timestep `dt = 1/120s`. Discs constrained to plane (`z=0, vz=0`); ball simulated in 3D.
Per-step pipeline: integrate plane motion (state-dependent damping: ground vs air); integrate ball vertical
motion (gravity, ground bounce with `groundRestitution`, kill bounce below `vzEpsilon`); wall collisions
(reflect with `wallRestitution`; walls infinitely tall except goal mouths); goal-mouth crossbar rule; disc–disc
2D collisions (positional correction + elastic impulse, `discRestitution`); disc–ball 2.5D collisions (only when
`z < discHeight`; 2D resolve + loft transfer `vz += loftFactor * impactSpeed`); sleep check; goal check.
Collision events returned from `step()` for throttled SFX/haptics. **Deterministic** — no randomness in the engine.

- **Flick → impulse:** drag clamped to `maxDragLen = 22`; `impulse = dir * power * maxLaunchSpeed`
  (`maxLaunchSpeed ≈ 140`); `power = dragLen/maxDragLen`.
- **Chip shots:** flag rides the flicked disc until first ball contact, then clears. Ground `loftFactor = 0.05`;
  Chip `loftFactor = 0.55` with `chipPlaneSpeedPenalty = 0.8`. Targets: a mid-power chip clears a disc 12 units
  ahead and lands within ~35; a full-power ground shot from midfield reaches goal.
- **Formation:** 2-1-2 mirrored about halfway, ball at center, stored as fractions of pitch dimensions.

Constant seeds (tune in M7): `gravity ≈ 260`, `groundRestitution ≈ 0.55`, `wallRestitution ≈ 0.75`,
`discRestitution ≈ 0.9`, `linearDampingPerSec ≈ 1.5`, `airDampingPerSec ≈ 0.25`, `crossbarHeight ≈ 3× ballR`,
`discHeight ≈ 1.2× ballR`, crossbar rebound restitution `0.4`.

## 5. 3D rendering (OpenGL, renderer in commonMain)
- **`Gl` abstraction** (`platform/gl/`): shader compile/link, buffers, VAOs, textures, uniforms, draw calls,
  state — ES 3.0 subset. No GL types leak past the interface (only `Int` handles + `FloatArray`/`Buffer`).
  Shaders written once in GLSL ES 3.00; per-platform preamble prepends `#version 300 es` (mobile) or
  `#version 330 core` (desktop).
- **Surfaces (expect/actual):** Android `GLSurfaceView` (ES 3.0, continuous); iOS `GLKView` + `CADisplayLink`;
  Desktop `AWTGLCanvas` in `SwingPanel` on a dedicated GL thread.
- **Threading:** GL calls only on the GL thread. The game loop publishes an immutable `RenderSnapshot` into an
  atomic reference; the GL thread reads the latest each frame. No locks, no GL from the ViewModel.
- **Scene (all procedural):** fixed perspective camera, FOV 45°, ~35° tilt, goal-cam dolly punch on goals;
  pitch = procedural stripe/line fragment shader; discs = 24-seg cylinders; ball = 16×24 UV sphere with spin;
  goals = post/crossbar cylinders + translucent net quad; walls = low tilted boxes. Blinn-Phong directional +
  ambient light. **Blob shadows** (no shadow maps). Aim overlay drawn in-scene (dotted ray on the plane, power
  ring; chip = dashed ballistic arc preview).
- **Input:** unproject pointer → ray → intersect pitch plane `z=0`; disc picking = nearest disc containing the
  point (+25% touch radius on mobile). All aim semantics stay in the ViewModel.
- **Budget:** 11 bodies, ~8 trivial draw calls, one light → easily 60fps. Single VAO per mesh; zero per-frame
  allocations on the GL thread.

## 6. AI (`AiPlanner`, commonMain)
Simulation-based candidate search. Enumerate shots (up to 5 discs × 24 dirs × 3 powers × 2 shot types = 720 on
Hard), prune chips when no defender lies in the striker→ball→goal corridor. Clone the world, simulate ≤4s, score
the terminal state (+1000 goal, −2000 own goal, + progress toward goal, + distance-to-mouth decrease, − out of
defensive shape, −30 crossbar hit). **Easy:** 60 ground-only candidates, top 50% random, ±15° / ±20% noise, never
chips. **Medium:** 200 incl. chips, top 10%, ±6°. **Hard:** full pruned set, best, no noise. ~1.5s budget on
`Dispatchers.Default`; 0.6–1.2s human-like pre-flick delay.

## 7. Project structure
See [README.md](README.md#project-layout) for the directory tree.

## 8. Screens & UX
- **Menu:** title, idly bouncing ball (Compose animation), Play vs AI (difficulty picker), 2 Players, Settings;
  shows last result + win/loss tally.
- **Game:** Compose HUD bar **above** the GL surface (not overlaid — sidesteps desktop compositing); score, turn
  chevron, turn counter, pause. Slingshot aiming with in-scene aim ray + power ring; Ground/Chip pill (also `C`
  key); release <8% power cancels. Blob shadows give height readability; crossbar hits flash the frame. Goal:
  "GOAL!" scale-in + goal-cam punch + sound, 1.5s pause, formation reset. Match over: Rematch / Menu overlay.
  Pause: Resume / Restart / Quit. 2P: fixed ends, clear turn indicator.
- **Settings:** sound, haptics (hidden on desktop), default difficulty, team color (4 palettes), reset history —
  persisted instantly.

## 9. Game loop (GameViewModel)
Fixed-timestep accumulator in `viewModelScope`; steps the engine while `hasStep()`, updates `GameUiState` with
`interpolationAlpha`, dispatches throttled effects, ends the turn at rest (triggers AI if vs AI). Publishes
`RenderSnapshot` to the atomic ref for the GL thread. Auto-pause on lifecycle stop; rebuild GL resources on
Android context loss (all procedural). Full process-death restoration not required for v1.

## 10. Milestones
M0 scaffold → M1 physics → M2 GL desktop → M3 GL Android/iOS → M4 playable match → M5 AI → M6 polish →
M7 hardening. ≈9–11 days. (Detailed acceptance criteria in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).)

## 11. Conventions
Kotlin official style; no wildcard imports; `internal` default outside `ui`; immutable UI state; no GL types
outside `platform/gl/`; no platform imports in `commonMain` outside `platform/`; GL-thread discipline;
no per-frame allocations; shaders in the common GLSL subset; regression test per fixed bug; version catalog;
conventional commits per milestone.

## 12. Risks
(1) iOS OpenGL ES deprecation — mitigated by the `Gl` interface (future Metal/ANGLE backend). (2) Desktop
GL-in-Compose compositing — HUD above canvas; JOGL fallback. (3) iOS simulator GL quirks — verify on device.
(4) Rendering scope creep — closed visual feature list.

## 13. Defaults for open questions
Teams "Blue vs Red" (palettes in settings). Turn limit 30. Desktop keyboard shortcuts P/R/C. Portrait-only on
mobile. Camera tilt 35° and goal-cam punch exposed as constants in `Camera.kt`.
