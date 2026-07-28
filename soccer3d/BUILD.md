# Building installable apps (Desktop + Android)

Godot packages games using **export templates** (a separate ~1 GB download for your exact engine
version). This dev sandbox can't download them (GitHub is network-blocked here), so **you install the
templates once**, then exporting is two clicks — or one CLI command I can run for you.

## 0. One-time: install export templates (the blocker)
In the Godot editor: top menu **Editor → Manage Export Templates → Download and Install** (needs
internet). If your network blocks that too:
1. On any machine/browser, download **`Godot_v4.7.1-stable_export_templates.tpz`** from
   <https://godotengine.org/download/> (the "Export templates" link for 4.7.1).
2. Godot → **Manage Export Templates → Install from File** → pick the `.tpz`.

Verify: the same dialog shows "4.7.1.stable ... installed".

## 1. Desktop (Windows) — installer-free .exe
Editor: **Project → Export → Add… → Windows Desktop**, set an export path (e.g. `build/Soccer3D.exe`),
then **Export Project**. You get `Soccer3D.exe` (+ `.pck`) you can double-click. No install needed.

CLI (once templates are installed):
```
"…/Godot_v4.7.1-stable_win64_console.exe" --headless --path soccer3d \
  --export-release "Windows Desktop" ../build/Soccer3D.exe
```

## 2. Android — .apk to sideload
One-time setup in **Editor → Editor Settings → Export → Android**:
- **Android Sdk Path** = `C:\Users\TechnologyDeveloper3\AppData\Local\Android\Sdk` (already installed)
- **Java Sdk Path** = your JDK 17
- **Debug Keystore** = `C:\Users\TechnologyDeveloper3\.android\debug.keystore`
  (user `androiddebugkey`, store/key pass `android` — already created)

Then **Project → Export → Add… → Android**, set a unique **Package → Unique Name** like
`com.ukemeikot.soccer3d`, choose an export path (`build/Soccer3D.apk`), **Export Project**.

Install on a connected device / running emulator:
```
adb install -r build/Soccer3D.apk
```

CLI (once templates + the Android setup above are in place):
```
"…/Godot_v4.7.1-stable_win64_console.exe" --headless --path soccer3d \
  --export-release "Android" ../build/Soccer3D.apk
```
> Touch controls aren't wired yet (keyboard-only so far) — an on-screen joystick + buttons come in a
> later mobile pass, so the APK is best driven with a Bluetooth keyboard/gamepad for now.

## 3. iOS
Requires a **Mac + Xcode**. Godot exports an Xcode project you build/sign there. Not buildable on Windows.

---

**TL;DR:** install the export templates (step 0) — that's the only piece blocked in the dev sandbox.
Then either export in-editor, or tell me it's done and I'll run the CLI exports here to produce
`Soccer3D.exe` and `Soccer3D.apk`.
