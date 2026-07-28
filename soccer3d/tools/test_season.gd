extends SceneTree
## Headless test of the Season engine (Phase F). Run:
##   godot --headless --path soccer3d --script res://tools/test_season.gd

func _init() -> void:
	var mc = load("res://scripts/match/MatchConfig.gd").new()
	mc.start_season(0) # play as Azure FC
	print("=== season started, user=", mc.season_user, " fixtures=", mc.season_fixtures.size())
	var guard := 0
	while not mc.season_over() and guard < 10:
		guard += 1
		var ok: bool = mc.prepare_season_match()
		var f: Dictionary = mc.next_user_fixture()
		if f.is_empty():
			print("no more user fixtures")
			break
		print("round ", f["round"], ": prepare_ok=", ok, " home_kit=", mc.home_kit, " away_kit=", mc.away_kit, " diff=", mc.difficulty)
		mc.record_user_result(2, 1) # user wins 2-1 every time
	print("=== season_over=", mc.season_over())
	print("=== STANDINGS ===")
	for r in mc.standings():
		print("%-15s P%d W%d D%d L%d GF%d GA%d GD%+d PTS%d" % [r["name"], r["p"], r["w"], r["d"], r["l"], r["gf"], r["ga"], r["gd"], r["pts"]])
	if mc.season_over():
		print("CHAMPION: ", mc.standings()[0]["name"])
	quit()
