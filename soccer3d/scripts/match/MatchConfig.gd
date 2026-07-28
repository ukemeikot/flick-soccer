extends Node
## Autoload singleton carrying Exhibition setup from the menu into the match, plus the saved result.

enum Difficulty { CASUAL, NORMAL, PRO }

var difficulty: int = Difficulty.NORMAL
var half_seconds: float = 120.0   # per half (accelerated clock)
var home_kit: int = 0
var away_kit: int = 1
var two_player: bool = false      # local 2P (P5+); 1P vs CPU for now

const KITS := [
	Color(0.20, 0.45, 0.95), # blue
	Color(0.92, 0.28, 0.28), # red
	Color(0.95, 0.75, 0.20), # gold
	Color(0.15, 0.75, 0.55), # teal
]

const SAVE_PATH := "user://last_result.json"

func kit_color(index: int) -> Color:
	return KITS[clampi(index, 0, KITS.size() - 1)]

## AI skill 0..1 by difficulty (accuracy / range / pressing).
func ai_skill() -> float:
	match difficulty:
		Difficulty.CASUAL: return 0.45
		Difficulty.PRO: return 1.0
		_: return 0.7

func save_result(home: int, away: int) -> void:
	var f := FileAccess.open(SAVE_PATH, FileAccess.WRITE)
	if f != null:
		f.store_string(JSON.stringify({"home": home, "away": away}))
		f.close()

func last_result() -> Dictionary:
	if not FileAccess.file_exists(SAVE_PATH):
		return {}
	var f := FileAccess.open(SAVE_PATH, FileAccess.READ)
	if f == null:
		return {}
	var txt := f.get_as_text()
	f.close()
	var data: Variant = JSON.parse_string(txt)
	return data if data is Dictionary else {}
