# iosApp (Xcode wrapper)

This folder holds the SwiftUI host for the shared Compose Multiplatform UI. The `ComposeApp`
framework is produced by `:composeApp` (see the `iosX64 / iosArm64 / iosSimulatorArm64` targets in
`composeApp/build.gradle.kts`).

## Generating the Xcode project (macOS)

The `.xcodeproj` is intentionally **not** committed as a hand-authored file. On a Mac, create it once
with the KMP tooling and it will pick up the sources here:

1. Open the repo root in **Android Studio** (with the Kotlin Multiplatform plugin) or **JetBrains Fleet**.
2. Use *New → iOS Application* against this `iosApp/` folder, or run the KMP "iOS" run configuration —
   the wizard generates `iosApp.xcodeproj` wired to the `ComposeApp` framework and these Swift files
   (`iOSApp.swift`, `ContentView.swift`, `Info.plist`).
3. Run on the simulator or a device.

`ContentView.swift` bridges to Kotlin via `MainViewControllerKt.MainViewController()`.

> OpenGL ES is deprecated on iOS (still functional, frozen at ES 3.0). Expect Xcode deprecation
> warnings when the real GL host lands in M3 — see the risks section of `../IMPLEMENTATION_PLAN.md`.
