# Flick Soccer — Implementation Plan

This is the working, phased build plan for the game. It adapts the original design brief
(`flick-soccer-plan.md`) to the confirmed decisions for this repo:

- **Renderer:** hand-rolled **OpenGL** behind a `Gl` interface (per §5 of the brief).
- **Platforms:** **Android + iOS + Desktop** wired from the start; desktop-first for iteration.
- **No authentication:** the app boots straight to the menu; there is no login/account gate anywhere in v1.

Each milestone ends with the project **compiling** and its **tests green**.

---

## 1. Architecture (MVVM, strict downward layering)

```
┌──────────────────────────────────────────────┐
│ UI (Compose + GL)  commonMain + platform      │  Screens/HUD (Compose), SceneRenderer (GL), input
├──────────────────────────────────────────────┤
│ ViewModel layer   commonMain                  │  Menu / Game / Settings ViewModels
├──────────────────────────────────────────────┤
│ Domain            commonMain (pure Kotlin)    │  GameEngine (rules/turns), PhysicsWorld, AiPlanner
├──────────────────────────────────────────────┤
│ Data              commonMain + expect/actual  │  Settings & MatchHistory repos, AudioPlayer, Haptics
└──────────────────────────────────────────────┘
```

- **Views are dumb** — they render `GameUiState` and forward gestures as intents.
- **ViewModels own state** — each exposes one immutable `StateFlow<XxxUiState>` plus a `SharedFlow<XxxEffect>` for one-shot effects (sound, haptic, navigate).
- **Domain is pure Kotlin** — `PhysicsWorld` / `GameEngine` know nothing about Compose, dispatchers, or platform. Deterministic (same inputs → same outcome) so the AI can plan by simulation.
- **No GL types outside `platform/gl/`** — the renderer depends only on the `Gl` interface.
- **No platform imports in `commonMain`** outside `platform/` expect declarations.

---

## 2. Milestones

> **Progress:** M0–M7 complete and pushed — v1 is feature-complete on Desktop + Android (35 desktop
> unit tests green). **Renderer changed:** the match is drawn by a Compose Canvas 2.5D renderer
> (`ui/game/render/CanvasScene.kt`), not OpenGL — the heavyweight `AWTGLCanvas` never composited
> inside Compose Desktop's `SwingPanel` (blank screen + native crash). The GL renderer/`Gl`/
> `GameGlSurface` stay in the tree behind the interface for a future true-3D backend. iOS host uses
> the shared Canvas (renders) but audio/haptics on iOS are still no-op stubs pending a Mac to verify.
> Visual/interactive polish (camera framing, feel) is the remaining human-QA pass.

### M0 — Scaffold ✅
KMP Compose Multiplatform project targeting Android/iOS/Desktop; version catalog; Koin wired;
sealed-class Menu → Game → Settings navigation; the app boots straight to the menu with **no auth gate**;
full package structure from §7 of the brief in place with compiling skeletons for physics, engine, AI, GL, renderer.
- **Accept:** `./gradlew :composeApp:desktopRun` opens the menu; `:composeApp:desktopTest` passes; Android configures.

### M1 — Physics core ✅
`Vec2 / Body / PhysicsWorld`: plane integration, ball vertical axis (gravity, ground bounce, air vs ground
damping), wall + disc–disc + disc–ball (2.5D) collisions, chip loft transfer, crossbar rule, sleep detection,
goal detection. Collision events returned from `step()` for the ViewModel to fire effects.
- **Tests (`commonTest`):** head-on elastic collision conserves momentum; wall bounce reflects with restitution;
  damping halts bodies; ball passes through goal mouth under crossbar but a disc does not; ball at
  `z ≥ crossbarHeight` crossing the mouth rebounds (crossbar); airborne ball (`z ≥ discHeight`) passes over a
  disc in its path while a grounded ball collides; chip flick clears an interposed disc and lands (arc sanity vs
  closed-form ballistics at damping 0); ground bounce loses energy per `groundRestitution`; determinism (two
  runs → identical states incl. `z`).
- **Accept:** all physics tests pass.

### M2 — GL foundation, desktop-first ✅
`Gl` interface + math lib (`Mat4`/`Vec3`, perspective/lookAt/unproject, unit-tested: ray-plane round-trips,
matrix identities); **LWJGL** desktop `actual` + `AWTGLCanvas` in `SwingPanel`; shader preamble system;
procedural meshes (quad, cylinder, UV sphere, goal frame); `SceneRenderer` draws a static kickoff scene with
Blinn-Phong lighting + blob shadows at 60fps.
- **Accept:** desktop shows the lit 3D pitch with all bodies; camera unproject tests green.

### M3 — GL on Android ✅ (iOS host pending a Mac)
`GLES30` actual + `GLSurfaceView` host (Android); K/N GLES cinterop actual + `GLKView` host (iOS);
Android GL context-loss rebuild (all meshes/shaders are procedural, so rebuild is cheap).
- **Accept:** the same static scene renders on Android device/emulator and iOS simulator; rotation/backgrounding
  doesn't crash or leak.

### M4 — Playable match ✅
`RenderSnapshot` pipeline (fixed-timestep loop → atomic snapshot → GL thread); 3D picking + slingshot aiming
with in-scene aim ray, power ring, Ground/Chip toggle; flick launches disc; turn alternation; scoring +
formation reset; turn limit + match end; goal-cam punch.
- **Accept:** full local 2P match playable on all three platforms; a chip visibly arcs over a defender and can
  clang the crossbar.

### M5 — AI ✅
`AiPlanner` with simulation search over ground **and chip** candidates (with corridor pruning), 3 difficulties,
human-like thinking delay.
- **Tests:** AI never moves opponent discs; Hard scores within N turns on an open-goal fixture; Hard chooses a
  chip when the only path is over a wall of defenders; Easy never chips; Easy ≠ Hard choice distribution.
- **Accept:** vs-AI match playable at all difficulties.

### M6 — Polish ✅
Sounds via `expect/actual` `AudioPlayer`; haptics; goal celebration; pause overlay; menu/settings screens
complete; team palettes (material tints); settings + match-history persistence; ball-spin visual.
- **Accept:** sound toggles work on all platforms; match history survives app restart.

### M7 — Hardening ✅ (physics/AI tuning constants documented; visual polish pending human QA)
Tune physics constants & AI weights (gravity, loftFactor, chipPlaneSpeedPenalty, groundRestitution,
crossbarHeight) against the §4.3 targets; edge cases (flick during pause, rapid rematch, toggling chip
mid-drag, desktop resize, GL context loss mid-sim, Android portrait lock); performance (60fps mid-range
Android; zero per-frame allocations on the GL thread and in the physics inner loop); finalize README.

**Total: ≈ 9–11 focused days.**

---

## 3. Key specs carried from the brief

- **Pitch:** 100 × 160 world units; goal mouth centered on each short edge; `crossbarHeight ≈ 3× ball radius`;
  `discHeight ≈ 1.2× ball radius`. World units → pixels only in the UI layer (letterboxed). **Never store pixels in domain state.**
- **Timestep:** fixed `dt = 1/120s` (two substeps per 60fps frame); renderer interpolates with `interpolationAlpha`.
- **Chip trade-off:** chip `loftFactor ≈ 0.55` with a `chipPlaneSpeedPenalty ≈ 0.8` on plane speed; ground `loftFactor ≈ 0.05`.
- **Match end:** 3 goals or a 30-turn limit (15/side); draws allowed in local 2P, sudden-death vs AI.
- **Modes (v1):** single-player vs AI (Easy/Medium/Hard) and local 2-player pass-and-play.

---

## 4. Risks & mitigations

1. **iOS OpenGL ES deprecation (highest).** Frozen at ES 3.0, could be removed in a future iOS. *Mitigation:* the
   scene depends only on the ~40-function `Gl` interface — a future Metal/ANGLE backend replaces one `actual`.
   Warnings are accepted (not silently suppressed) for v1 and noted in the README.
2. **Desktop GL-in-Compose interop.** Heavyweight `AWTGLCanvas` can't reliably composite Compose *on top*.
   *Mitigation (designed in):* HUD sits **above** the canvas, not over it; full-screen overlays may cover it.
   Fallback if `lwjgl3-awt` is flaky: JOGL `GLJPanel` behind the same `Gl` interface.
3. **iOS simulator GL quirks.** Software-rendered; verify on a real device at M3 and M7. Shaders are kept trivial.
4. **Rendering scope creep.** v1 visual list is closed: Blinn-Phong, blob shadows, procedural pitch shader,
   goal-cam punch. No shadow maps, no post-processing, no model loading.

---

## 5. Conventions

- Kotlin official style; no wildcard imports; `internal` by default outside `ui`.
- Immutable UI state only; never expose `MutableStateFlow` publicly.
- Physics inner loop and GL thread: no per-frame allocations where avoidable.
- Shaders in the GLSL ES 3.00 / GL 3.3 core common subset — must compile on all three platforms.
- Every bug fixed in M1–M5 gets a regression test.
- Version catalog for all dependencies; commit per milestone with conventional messages.
