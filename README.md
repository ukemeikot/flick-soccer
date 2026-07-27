# Flick Soccer

A turn-based physics soccer game built with **Kotlin Multiplatform** and **Compose Multiplatform**, rendered in real-time 3D via **OpenGL**. Two teams of 5 discs flick a ball around a 3D pitch; chip the ball over defenders and under the crossbar to score.

Targets: **Android · iOS · Desktop (JVM)**.

> Status: **v1 feature-complete (M0–M7)** on Desktop + Android. Boots straight to the menu (no
> login/account gate). Local 2-player and vs-AI (Easy/Medium/Hard) are playable — slingshot aiming
> with a 3D-picked disc, Ground/Chip shots, real 2.5D physics, scoring, goal reset, match-over,
> procedural sound effects, haptics (Android), and settings (sound/haptics/difficulty/team colors).
>
> **Renderer note:** the match is drawn with a **Compose Canvas 2.5D renderer** (projected through a
> perspective camera) rather than the OpenGL path — the heavyweight `AWTGLCanvas` would not composite
> inside Compose Desktop's `SwingPanel`. The OpenGL renderer + `Gl` abstraction remain in the repo
> behind the interface for a future true-3D pass. Desktop unit tests: 35 green.
>
> **Try it:** `./gradlew :composeApp:run` (desktop) or run the `composeApp` config on an Android
> device/emulator. Drag back from one of your (blue) discs and release to flick; toggle ⚽ Ground /
> 🪁 Chip in the HUD.

---

## Tech stack

| Concern | Choice |
|---|---|
| Language | Kotlin 2.1 (multiplatform) |
| Platforms | Android, iOS, Desktop (JVM) |
| UI (menus / HUD) | Compose Multiplatform |
| Match rendering | Hand-rolled **OpenGL** renderer in `commonMain` behind a ~40-function `Gl` interface; one thin `actual` per platform (Android `GLES30`, iOS GLES cinterop, Desktop LWJGL 3 + `AWTGLCanvas`) |
| Architecture | MVVM, unidirectional data flow (UI → intents → ViewModel → immutable `StateFlow` → UI) |
| Physics | Custom deterministic 2.5D engine (pure Kotlin, `commonMain`) — discs on a 2D plane, ball as a sphere with a height axis |
| 3D math | Hand-written `Mat4` / `Vec3` (perspective, lookAt, plane raycast) — no dependency |
| DI | Koin (multiplatform) |
| Persistence | `multiplatform-settings` + `kotlinx.serialization` |
| Async | Coroutines + `StateFlow` |
| Audio | `expect/actual`: Android `SoundPool`, iOS `AVAudioPlayer`, Desktop `javax.sound.sampled` |
| Haptics | `expect/actual`: Android `Vibrator`, iOS `UIImpactFeedbackGenerator`, Desktop no-op |
| Navigation | Simple sealed-class screen state (3 screens) |
| Testing | `kotlin.test` in `commonTest` (physics, engine, AI, math) |

See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for the full architecture rationale and the phased build plan.

---

## Project layout

```
flick-soccer/
├── composeApp/                     # single shared KMP module + platform entry points
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/io/github/ukemeikot/flicksoccer/
│       │   ├── App.kt              # root composable + sealed-class navigation
│       │   ├── di/                 # Koin modules
│       │   ├── domain/             # model, physics, engine, ai  (pure Kotlin)
│       │   ├── data/               # settings & match-history repositories
│       │   ├── platform/           # expect: AudioPlayer, Haptics, gl/Gl, gl/GameGlSurface
│       │   ├── ui/                 # theme, menu, settings, game (screens + ViewModels + render/)
│       │   └── util/               # Mat4, Vec3, MathX, FixedTimestepClock
│       ├── commonTest/kotlin/...   # engine / physics / AI / math tests
│       ├── androidMain/kotlin/...  # MainActivity + actual impls
│       ├── iosMain/kotlin/...      # MainViewController + actual impls
│       └── desktopMain/kotlin/...  # main.kt + actual impls
├── iosApp/                         # Xcode wrapper (build iOS on macOS)
├── gradle/libs.versions.toml
└── settings.gradle.kts
```

---

## Prerequisites

- **JDK 17+**
- **Android SDK** (via Android Studio) with `local.properties` pointing to it, or `ANDROID_HOME` set — required to build the Android target and to configure the Gradle project.
- **macOS + Xcode** — required only to build/run the iOS target. iOS cannot be built on Windows/Linux.

---

## Running

### Desktop (JVM) — fastest iteration loop
```bash
./gradlew :composeApp:desktopRun -DmainClass=io.github.ukemeikot.flicksoccer.MainKt
# or the convenience task:
./gradlew :composeApp:run
```

### Android
Open in Android Studio and run the `composeApp` configuration on an emulator/device, or:
```bash
./gradlew :composeApp:installDebug
```

### iOS (macOS only)
Open `iosApp/iosApp.xcodeproj` in Xcode and run, or use the KMP run configuration in Android Studio / Fleet.

### Tests
```bash
./gradlew :composeApp:desktopTest        # JVM unit tests (physics, engine, AI, math)
```

---

## Notes

- **No authentication.** v1 has no accounts, login, or network — the app opens directly to the menu and into a match. Out of scope for v1: online multiplayer, monetization, accounts, tournaments.
- **iOS OpenGL ES is deprecated** by Apple (still functional, frozen at ES 3.0). The renderer depends only on the `Gl` interface, so a future Metal/ANGLE backend swaps one `actual` without touching game code. Deprecation warnings are expected and intentionally not suppressed — see the risks section of the implementation plan.

## License

TBD.
