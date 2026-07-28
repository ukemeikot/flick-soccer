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
const SEASON_PATH := "user://season.json"

# --- Teams / Season (Phase F) ----------------------------------------------------------------

const TEAMS := [
	{"name": "Azure FC", "kit": 0, "rating": 0.70},
	{"name": "Crimson United", "kit": 1, "rating": 0.82},
	{"name": "Gold Rovers", "kit": 2, "rating": 0.64},
	{"name": "Teal City", "kit": 3, "rating": 0.74},
]

var season_active := false
var season_user := 0                    # index into TEAMS (the player's club)
var season_round := 0                   # 0..2 (round-robin rounds remaining)
var season_fixtures: Array = []         # [{round, h, a, hs, as, played, user}]
var season_return := false              # set true when a match is launched from the season
var season_just_played := false         # set true after recording, so the menu reopens standings

func kit_color(index: int) -> Color:
	return KITS[clampi(index, 0, KITS.size() - 1)]

func team_names() -> Array:
	var out: Array = []
	for t in TEAMS:
		out.append(t["name"])
	return out

## Build a fresh round-robin season with the player controlling team `user_idx`.
func start_season(user_idx: int) -> void:
	season_user = clampi(user_idx, 0, TEAMS.size() - 1)
	season_round = 0
	season_fixtures = []
	var others: Array = []
	for i in TEAMS.size():
		if i != season_user:
			others.append(i)
	# 3 rounds; each round = the player's match (they are home) + the other pair's match.
	for r in others.size():
		season_fixtures.append({"round": r, "h": season_user, "a": others[r], "hs": -1, "as": -1, "played": false, "user": true})
		var rest: Array = []
		for o in others:
			if o != others[r]:
				rest.append(o)
		season_fixtures.append({"round": r, "h": rest[0], "a": rest[1], "hs": -1, "as": -1, "played": false, "user": false})
	season_active = true
	save_season()

## The player's next unplayed fixture (they are always the home side for control).
func next_user_fixture() -> Dictionary:
	for f in season_fixtures:
		if f["user"] and not f["played"]:
			return f
	return {}

## Configure this match from the player's next fixture and mark that we should record its result.
func prepare_season_match() -> bool:
	var f := next_user_fixture()
	if f.is_empty():
		return false
	home_kit = TEAMS[f["h"]]["kit"]
	away_kit = TEAMS[f["a"]]["kit"]
	if away_kit == home_kit: # avoid a kit clash
		away_kit = (home_kit + 1) % KITS.size()
	# Opponent strength drives difficulty.
	var rating: float = TEAMS[f["a"]]["rating"]
	difficulty = Difficulty.PRO if rating >= 0.78 else (Difficulty.CASUAL if rating < 0.66 else Difficulty.NORMAL)
	season_return = true
	return true

## Record the player's match, auto-simulate the other fixture in that round, then advance.
func record_user_result(home_goals: int, away_goals: int) -> void:
	season_return = false
	var f := next_user_fixture()
	if f.is_empty():
		return
	f["hs"] = home_goals
	f["as"] = away_goals
	f["played"] = true
	var r: int = f["round"]
	for other in season_fixtures:
		if other["round"] == r and not other["user"] and not other["played"]:
			var res := _sim_match(other["h"], other["a"])
			other["hs"] = res[0]
			other["as"] = res[1]
			other["played"] = true
	season_round = mini(season_round + 1, 3)
	season_just_played = true
	save_season()

func season_over() -> bool:
	for f in season_fixtures:
		if not f["played"]:
			return false
	return season_active and not season_fixtures.is_empty()

## Standings: [{name, p, w, d, l, gf, ga, gd, pts}] sorted by pts, gd, gf.
func standings() -> Array:
	var table := {}
	for i in TEAMS.size():
		table[i] = {"name": TEAMS[i]["name"], "p": 0, "w": 0, "d": 0, "l": 0, "gf": 0, "ga": 0, "gd": 0, "pts": 0}
	for f in season_fixtures:
		if not f["played"]:
			continue
		_apply(table[f["h"]], f["hs"], f["as"])
		_apply(table[f["a"]], f["as"], f["hs"])
	var rows: Array = table.values()
	rows.sort_custom(func(a, b):
		if a["pts"] != b["pts"]: return a["pts"] > b["pts"]
		if a["gd"] != b["gd"]: return a["gd"] > b["gd"]
		return a["gf"] > b["gf"])
	return rows

func _apply(row: Dictionary, gf: int, ga: int) -> void:
	row["p"] += 1
	row["gf"] += gf
	row["ga"] += ga
	row["gd"] = row["gf"] - row["ga"]
	if gf > ga:
		row["w"] += 1
		row["pts"] += 3
	elif gf == ga:
		row["d"] += 1
		row["pts"] += 1
	else:
		row["l"] += 1

func _sim_match(h: int, a: int) -> Array:
	var eh: float = TEAMS[h]["rating"] - TEAMS[a]["rating"] + 0.15 # home edge
	return [_sim_goals(eh), _sim_goals(-eh + 0.15)]

func _sim_goals(edge: float) -> int:
	var lam := clampf(1.2 + edge * 1.8, 0.25, 4.0)
	var g := 0
	for i in 6:
		if randf() < lam / 6.0:
			g += 1
	return g

func save_season() -> void:
	var f := FileAccess.open(SEASON_PATH, FileAccess.WRITE)
	if f != null:
		f.store_string(JSON.stringify({
			"active": season_active, "user": season_user, "round": season_round, "fixtures": season_fixtures}))
		f.close()

func load_season() -> void:
	if not FileAccess.file_exists(SEASON_PATH):
		return
	var f := FileAccess.open(SEASON_PATH, FileAccess.READ)
	if f == null:
		return
	var data: Variant = JSON.parse_string(f.get_as_text())
	f.close()
	if data is Dictionary and data.get("active", false):
		season_active = true
		season_user = int(data.get("user", 0))
		season_round = int(data.get("round", 0))
		season_fixtures = []
		for rec in data.get("fixtures", []):
			# JSON numbers load as floats — normalize back to ints/bools.
			season_fixtures.append({
				"round": int(rec["round"]), "h": int(rec["h"]), "a": int(rec["a"]),
				"hs": int(rec["hs"]), "as": int(rec["as"]),
				"played": bool(rec["played"]), "user": bool(rec["user"])})

func _ready() -> void:
	load_season()

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
