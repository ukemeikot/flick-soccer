# Soccer3D (Godot 4)

Stylized 3D arcade football (5-a-side) built in **Godot 4**. One app, with a **main menu** to pick a
game: **3D Football — Exhibition** (this new build) and **Flick Soccer** (being ported from the
Kotlin version). See [`../football-plan.md`](../football-plan.md) for the full plan.

> **Status: P0** — a 3D pitch with a rolling ball, a broadcast camera that follows it, and a
> keyboard-movable debug player, reached from the main menu.

## Install Godot (one-time)
This machine's sandbox can't download Godot (GitHub/TuxFamily are network-blocked here), so install it
yourself — any of:
- Download **Godot 4.x (Standard, not .NET)** from <https://godotengine.org/download> and unzip the
  single `Godot_v4.x-stable_win64.exe`. No installer needed.
- or `winget install -e --id GodotEngine.GodotEngine`
- or `scoop install godot`

(The project is authored for 4.3; newer 4.x will offer to upgrade it — that's fine.)

## Run it
1. Launch Godot → **Import** → select this **`soccer3d/`** folder (pick `project.godot`) → **Import & Edit**.
2. Press **F5** (Play). The main menu appears.
3. Click **3D Football — Exhibition**. You should see a green 3D pitch, a ball rolling and bouncing off
   the boundaries, cosmetic goals at both ends, and a blue capsule (the debug player).
4. **Arrow keys** move the player. **Esc** returns to the menu.

## Project layout
```
soccer3d/
├── project.godot            # main scene = scenes/MainMenu.tscn
├── scenes/  MainMenu.tscn  Match.tscn  FlickPlaceholder.tscn
└── scripts/
    ├── Match.gd             # builds the P0 3D world
    ├── Ball.gd              # RigidBody3D ball
    ├── player/DebugPlayer.gd
    ├── camera/BroadcastCamera.gd
    └── ui/  MainMenu.gd  FlickPlaceholder.gd
```

## Next (per the plan)
P1 real player control + dribble + kick → P2 actions → P3 teams/AI/GK → P4 rules → P5 UX → P6 mobile.
The **Flick Soccer** menu entry is a placeholder until that game is ported from Kotlin into Godot.

> Heads-up: because Godot can't be run in the build sandbox, each milestone is verified by **you**
> running it in the editor and reporting back. If something errors on load, paste the Godot output and
> I'll fix it.
