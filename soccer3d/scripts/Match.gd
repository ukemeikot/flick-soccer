extends Node3D
class_name GameMatch
## The 5-a-side exhibition match: builds the pitch, spawns two teams, assigns human/AI roles and
## the active player each frame, detects goals, runs the clock/halves, and handles kickoff, pause and
## full-time. Human controls the active Home player; everyone else is AI.

const MENU_SCENE := "res://scenes/MainMenu.tscn"
const PITCH_LENGTH := 40.0
const PITCH_WIDTH := 25.0
const HALF_W := 12.5
const HALF_L := 20.0
const MOUTH := 6.0
const KICKOFF_FREEZE := 1.1

enum Phase { KICKOFF, PLAYING, PAUSED, FULLTIME, DEADBALL, GOAL }

# Formation slots for HOME (defends -Z, attacks +Z). {pos, gk}. AWAY is mirrored in Z.
const FORMATION := [
	{"pos": Vector3(0, 0, -18), "gk": true},
	{"pos": Vector3(-6, 0, -12), "gk": false},
	{"pos": Vector3(6, 0, -12), "gk": false},
	{"pos": Vector3(0, 0, -8), "gk": false},
	{"pos": Vector3(0, 0, -3), "gk": false},
]

var ball: Ball
var players: Array[Player] = []
var camera: BroadcastCamera
var hud: Hud
var _marker: MeshInstance3D
var _forced_active: Player = null # manual player-switch override (Home)
var _ball_stuck_time := 0.0

var phase := Phase.KICKOFF
var half := 1
var time_left := 120.0
var score_home := 0
var score_away := 0
var _kickoff_timer := KICKOFF_FREEZE

# Phase C: rules / set pieces state.
var _last_touch_team := -1        # team that last played the ball (for corner vs goal kick)
var _home_fouls := 0
var _away_fouls := 0
var _home_cards := 0              # yellow cards shown (cosmetic tally)
var _away_cards := 0
var _stoppage := 0.0              # accumulated added time
var _restart_timer := 0.0         # dead-ball settle before play resumes
var _restart_taker: Player = null
var _celebrate_timer := 0.0       # goal-celebration hold before kickoff

func _ready() -> void:
	time_left = MatchConfig.half_seconds
	_build_environment()
	_build_pitch()

	ball = Ball.new()
	ball.position = Vector3(0, 0.3, 0)
	add_child(ball)

	_spawn_team(Player.HOME, MatchConfig.kit_color(MatchConfig.home_kit))
	_spawn_team(Player.AWAY, MatchConfig.kit_color(MatchConfig.away_kit))

	camera = BroadcastCamera.new()
	camera.fov = 62.0
	add_child(camera)
	camera.target = ball

	var layer := CanvasLayer.new()
	add_child(layer)
	hud = Hud.new()
	hud.world = self
	layer.add_child(hud)
	hud.set_score(0, 0)
	layer.add_child(TouchControls.new()) # on-screen controls (mobile only)

	_marker = _make_marker()
	add_child(_marker)

	Sfx.start_ambience()
	_kickoff()

func _physics_process(delta: float) -> void:
	match phase:
		Phase.KICKOFF:
			_update_roles()
			_kickoff_timer -= delta
			if _kickoff_timer <= 0.0:
				_set_frozen(false)
				phase = Phase.PLAYING
		Phase.PLAYING:
			if Input.is_action_just_pressed("switch_player") or Touch.consume_switch():
				_cycle_active()
			_update_roles()
			_unstick_ball(delta)
			_check_bounds()
			if phase != Phase.PLAYING:
				return # a set piece took over this frame
			time_left = maxf(time_left - delta, 0.0)
			if time_left <= 0.0:
				# Play added time before the half actually ends.
				if _stoppage > 0.0:
					_stoppage = maxf(_stoppage - delta, 0.0)
					hud.set_clock(0.0, half, _stoppage)
				else:
					_end_of_half()
			else:
				hud.set_clock(time_left, half, _stoppage)
		Phase.DEADBALL:
			_update_roles()
			_stoppage += delta * 0.5 # dead-ball time is partly added on
			_restart_timer -= delta
			if _restart_timer <= 0.0:
				_set_frozen(false)
				phase = Phase.PLAYING
		Phase.GOAL:
			_celebrate_timer -= delta
			if _celebrate_timer <= 0.0:
				_kickoff()

func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel"):
		if phase == Phase.PLAYING:
			_pause()
		elif phase == Phase.PAUSED:
			resume()

# --- Roles / active player ------------------------------------------------------------------

func _update_roles() -> void:
	for t in [Player.HOME, Player.AWAY]:
		var chaser := _nearest_outfielder(t)
		var active := _pick_active(t)
		for p in players:
			if p.team != t or p.is_gk or p.sent_off:
				continue
			p.role_chase = (p == chaser)
			p.is_human = (t == Player.HOME and p == active)
	var human := _pick_active(Player.HOME)
	if hud != null:
		hud.player = human
	if _marker != null and human != null:
		_marker.global_position = human.global_position + Vector3(0, 2.35, 0)

func _unstick_ball(delta: float) -> void:
	# Safety net: if the ball hangs in the air nearly still for too long, knock it down.
	if ball.global_position.y > 0.8 and ball.linear_velocity.length() < 1.0:
		_ball_stuck_time += delta
		if _ball_stuck_time > 1.5:
			ball.sleeping = false
			ball.linear_velocity = Vector3(ball.linear_velocity.x, -4.0, ball.linear_velocity.z)
			_ball_stuck_time = 0.0
	else:
		_ball_stuck_time = 0.0

func _pick_active(team: int) -> Player:
	# A player in possession always takes control (and clears any manual pick for Home).
	for p in players:
		if p.team == team and not p.is_gk and not p.sent_off and p.has_possession():
			if team == Player.HOME:
				_forced_active = null
			return p
	if team == Player.HOME and _forced_active != null:
		return _forced_active
	return _nearest_outfielder(team)

func _home_outfielders() -> Array[Player]:
	var a: Array[Player] = []
	for p in players:
		if p.team == Player.HOME and not p.is_gk and not p.sent_off:
			a.append(p)
	return a

func _cycle_active() -> void:
	var outs := _home_outfielders()
	if outs.is_empty():
		return
	outs.sort_custom(func(a, b): return a.global_position.distance_to(ball.global_position) < b.global_position.distance_to(ball.global_position))
	var cur := _forced_active if _forced_active != null else _pick_active(Player.HOME)
	var idx := outs.find(cur)
	_forced_active = outs[(idx + 1) % outs.size()]

func _nearest_outfielder(team: int) -> Player:
	var best: Player = null
	var best_d := INF
	for p in players:
		if p.team != team or p.is_gk or p.sent_off:
			continue
		var d := p.global_position.distance_to(ball.global_position)
		if d < best_d:
			best_d = d
			best = p
	return best

# --- Queries used by Player AI --------------------------------------------------------------

func opp_goal(team: int) -> Vector3:
	return Vector3(0, 0, HALF_L if team == Player.HOME else -HALF_L)

func own_goal(team: int) -> Vector3:
	return Vector3(0, 0, -HALF_L if team == Player.HOME else HALF_L)

func best_pass_target(from: Player) -> Player:
	var best: Player = null
	var best_score := -INF
	for p in players:
		if p.team != from.team or p == from or p.is_gk or p.sent_off:
			continue
		var to_p := p.global_position - from.global_position
		to_p.y = 0.0
		var dist := to_p.length()
		if dist < 2.0 or dist > 26.0:
			continue
		var forwardness := (p.global_position.z - from.global_position.z) * from.attack_sign()
		# Strongly prefer a teammate in the direction the player is facing (where you're aiming).
		var align := from.facing.dot(to_p.normalized())
		var s := align * 5.0 + forwardness - dist * 0.15
		if s > best_score:
			best_score = s
			best = p
	return best

## Best target for a through-ball: the most advanced teammate who is ahead of the passer
## (toward the opponent goal) and not too far out wide — someone to run onto the ball.
func through_ball_target(from: Player) -> Player:
	var best: Player = null
	var best_score := -INF
	for p in players:
		if p.team != from.team or p == from or p.is_gk or p.sent_off:
			continue
		var forwardness := (p.global_position.z - from.global_position.z) * from.attack_sign()
		if forwardness < 1.0: # must be ahead of the passer
			continue
		var wide := absf(p.global_position.x - from.global_position.x)
		var s := forwardness - wide * 0.4
		if s > best_score:
			best_score = s
			best = p
	return best

## Does the given team currently have the ball (for tactical shape)?
func team_has_ball(team: int) -> bool:
	var h := _ball_holder()
	if h != null:
		return h.team == team
	return _last_touch_team == team

## Nearest opposing outfielder to p (used for marking).
func nearest_opponent_forward(p: Player) -> Player:
	var best: Player = null
	var best_d := INF
	for o in players:
		if o.team == p.team or o.is_gk or o.sent_off:
			continue
		var d := o.global_position.distance_to(p.global_position)
		if d < best_d:
			best_d = d
			best = o
	return best

func opponent_within(p: Player, r: float) -> bool:
	for o in players:
		if o.team == p.team:
			continue
		if o.global_position.distance_to(p.global_position) < r:
			return true
	return false

# --- Phase C: touches, tackles, fouls, set pieces -------------------------------------------

## Called by players whenever they kick or control the ball (for corner-vs-goal-kick decisions).
func note_touch(p: Player) -> void:
	_last_touch_team = p.team

func _ball_holder() -> Player:
	for p in players:
		if not p.sent_off and p.has_possession():
			return p
	return null

## Resolve a tackle attempt: either win the ball cleanly, or concede a foul.
func resolve_tackle(t: Player) -> void:
	if phase != Phase.PLAYING:
		return
	var holder := _ball_holder()
	if holder == null or holder.team == t.team:
		return # nobody to tackle — the lunge is just a lunge
	if holder.global_position.distance_to(t.global_position) > 2.4:
		return
	var facing_ok := t.facing.dot((holder.global_position - t.global_position).normalized()) > 0.2
	var win := clampf(0.45 + t.attr_defend * 0.25 + (0.15 if facing_ok else -0.2), 0.05, 0.9)
	if randf() < win:
		ball.kick(t.facing * 5.0) # poke the ball away
		note_touch(t)
	else:
		_award_foul(t, holder)

func _award_foul(offender: Player, victim: Player) -> void:
	var spot := Vector3(victim.global_position.x, 0.0, victim.global_position.z)
	if offender.team == Player.HOME:
		_home_fouls += 1
	else:
		_away_fouls += 1

	# A foul inside the offender's own box is a penalty.
	if _in_penalty_box(spot, offender.team):
		Sfx.play("whistle")
		hud.show_banner("PENALTY!", 1.6)
		_maybe_card(offender, true)
		_start_penalty(victim.team)
		_update_info()
		return

	Sfx.play("whistle")
	hud.show_banner("FREE KICK", 1.3)
	_maybe_card(offender, false)
	_start_restart(victim.team, spot)
	_update_info()

func _maybe_card(offender: Player, dangerous: bool) -> void:
	var chance := 0.4 if dangerous else 0.2
	if randf() >= chance:
		return
	offender.yellows += 1
	if offender.team == Player.HOME:
		_home_cards += 1
	else:
		_away_cards += 1
	# Second yellow (or a rare straight red) → sent off, if the team can spare a player.
	if (offender.yellows >= 2 or randf() < 0.08) and _outfield_count(offender.team) > 2:
		offender.sent_off = true
		offender.set_frozen(true)
		offender.visible = false
		hud.show_banner("RED CARD", 1.8)
	else:
		hud.show_banner("YELLOW CARD", 1.3)

func _outfield_count(team: int) -> int:
	var n := 0
	for p in players:
		if p.team == team and not p.is_gk and not p.sent_off:
			n += 1
	return n

## Generic dead-ball restart: place the ball, stand the taker behind it, then resume shortly.
func _start_restart(team: int, spot: Vector3) -> void:
	phase = Phase.DEADBALL
	_set_frozen(true)
	ball.linear_velocity = Vector3.ZERO
	ball.angular_velocity = Vector3.ZERO
	ball.spin = Vector3.ZERO
	ball.global_position = Vector3(
		clampf(spot.x, -HALF_W + 0.6, HALF_W - 0.6), 0.3,
		clampf(spot.z, -HALF_L + 0.6, HALF_L - 0.6))
	var taker := _nearest_to(team, ball.global_position)
	if taker != null:
		taker.global_position = ball.global_position - _forward_dir(team) * 1.2
		taker.velocity = Vector3.ZERO
		_restart_taker = taker
		_forced_active = taker if team == Player.HOME else null
	_restart_timer = 0.7

func _start_penalty(team: int) -> void:
	phase = Phase.DEADBALL
	_set_frozen(true)
	var goal := opp_goal(team)
	var spot := Vector3(0.0, 0.3, goal.z - signf(goal.z) * 6.0)
	ball.linear_velocity = Vector3.ZERO
	ball.angular_velocity = Vector3.ZERO
	ball.spin = Vector3.ZERO
	ball.global_position = spot
	var taker := _nearest_to(team, spot)
	if taker != null:
		taker.global_position = spot - _forward_dir(team) * 1.2
		taker.velocity = Vector3.ZERO
		_restart_taker = taker
		_forced_active = taker if team == Player.HOME else null
	_restart_timer = 0.9

## Detect the ball leaving the pitch → throw-in (touchline) or corner / goal kick (byline).
func _check_bounds() -> void:
	var bp := ball.global_position
	if absf(bp.x) > HALF_W:
		var team := _other_team(_last_touch_team)
		Sfx.play("whistle")
		hud.show_banner("THROW-IN", 1.1)
		_start_restart(team, Vector3(bp.x, 0.0, bp.z))
	elif absf(bp.z) > HALF_L:
		# A ball heading into the goal mouth is a goal (handled by the sensor) — ignore it here.
		if absf(bp.x) < MOUTH / 2.0 + 0.3 and bp.y < 2.4:
			return
		var defending_team := Player.AWAY if bp.z > 0.0 else Player.HOME
		Sfx.play("whistle")
		if _last_touch_team == defending_team:
			var cx := signf(bp.x) * (HALF_W - 1.0)
			var cz := signf(bp.z) * (HALF_L - 1.0)
			hud.show_banner("CORNER", 1.1)
			_start_restart(_other_team(defending_team), Vector3(cx, 0.0, cz))
		else:
			hud.show_banner("GOAL KICK", 1.1)
			_start_restart(defending_team, Vector3(0.0, 0.0, signf(bp.z) * (HALF_L - 5.0)))

func _in_penalty_box(pos: Vector3, team: int) -> bool:
	var g := own_goal(team)
	return absf(pos.x) < 6.0 and absf(pos.z - g.z) < 6.0

# Direction a team attacks in (unit vector along Z).
func _forward_dir(team: int) -> Vector3:
	return Vector3(0, 0, 1.0 if team == Player.HOME else -1.0)

func _other_team(team: int) -> int:
	return Player.HOME if team == Player.AWAY else Player.AWAY

func _nearest_to(team: int, pos: Vector3) -> Player:
	var best: Player = null
	var best_d := INF
	for p in players:
		if p.team != team or p.is_gk or p.sent_off:
			continue
		var d := p.global_position.distance_to(pos)
		if d < best_d:
			best_d = d
			best = p
	return best

func _update_info() -> void:
	if hud != null:
		hud.set_info("Fouls  %d-%d   Cards  %d-%d" % [_home_fouls, _away_fouls, _home_cards, _away_cards])

# --- Match flow -----------------------------------------------------------------------------

func _kickoff() -> void:
	_forced_active = null
	_reset_positions()
	ball.linear_velocity = Vector3.ZERO
	ball.angular_velocity = Vector3.ZERO
	ball.global_position = Vector3(0, 0.3, 0)
	_set_frozen(true)
	_kickoff_timer = KICKOFF_FREEZE
	phase = Phase.KICKOFF
	Sfx.play("whistle")

func _reset_positions() -> void:
	for p in players:
		p.global_position = p.home_pos
		p.velocity = Vector3.ZERO

func _on_goal_scored(body: Node, scoring_team: int) -> void:
	if phase != Phase.PLAYING or not (body is Ball):
		return
	if scoring_team == Player.HOME:
		score_home += 1
	else:
		score_away += 1
	hud.set_score(score_home, score_away)
	hud.show_goal("HOME" if scoring_team == Player.HOME else "AWAY")
	Sfx.play("goal")
	Sfx.play("roar")
	# Celebration hold: freeze the players and let the camera linger before kickoff.
	_set_frozen(true)
	_celebrate_timer = 2.0
	phase = Phase.GOAL

func _end_of_half() -> void:
	if half == 1:
		half = 2
		time_left = MatchConfig.half_seconds
		_stoppage = 0.0
		hud.set_clock(time_left, half)
		_kickoff()
	else:
		phase = Phase.FULLTIME
		_set_frozen(true)
		Sfx.stop_ambience()
		MatchConfig.save_result(score_home, score_away)
		if MatchConfig.season_return:
			MatchConfig.record_user_result(score_home, score_away)
		var result := "DRAW"
		if score_home > score_away:
			result = "HOME WINS"
		elif score_away > score_home:
			result = "AWAY WINS"
		hud.show_result("%s\n%d - %d" % [result, score_home, score_away])

func _pause() -> void:
	phase = Phase.PAUSED
	_set_frozen(true)
	hud.show_pause(true)

func resume() -> void:
	if phase != Phase.PAUSED:
		return
	hud.show_pause(false)
	_set_frozen(false)
	phase = Phase.PLAYING

func rematch() -> void:
	score_home = 0
	score_away = 0
	half = 1
	time_left = MatchConfig.half_seconds
	_stoppage = 0.0
	_home_fouls = 0
	_away_fouls = 0
	_home_cards = 0
	_away_cards = 0
	for p in players:
		p.sent_off = false
		p.yellows = 0
		p.visible = true
	hud.set_score(0, 0)
	hud.set_info("")
	hud.hide_overlays()
	_kickoff()

func quit_to_menu() -> void:
	Sfx.stop_ambience()
	get_tree().change_scene_to_file(MENU_SCENE)

func _set_frozen(frozen: bool) -> void:
	for p in players:
		p.set_frozen(frozen)

# --- Spawning -------------------------------------------------------------------------------

func _spawn_team(team: int, kit: Color) -> void:
	for i in FORMATION.size():
		var slot: Dictionary = FORMATION[i]
		var pos: Vector3 = slot["pos"]
		if team == Player.AWAY:
			pos = Vector3(pos.x, pos.y, -pos.z) # mirror to +Z half
		var p := Player.new()
		p.team = team
		p.is_gk = slot["gk"]
		p.number = i + 1
		p.home_pos = Vector3(pos.x, 0.0, pos.z)
		p.kit_color = kit.lightened(0.35) if slot["gk"] else kit
		p.skill = MatchConfig.ai_skill()
		_assign_attrs(p, i)
		p.ball = ball
		p.world = self
		p.position = Vector3(pos.x, 0.0, pos.z)
		add_child(p)
		players.append(p)

## Per-position attribute profiles (defenders defend, forwards are quick & finish).
func _assign_attrs(p: Player, idx: int) -> void:
	match idx:
		0: # goalkeeper
			p.attr_pace = 0.90; p.attr_defend = 1.20; p.attr_shoot = 0.80; p.attr_pass = 0.95
		1, 2: # defenders
			p.attr_pace = 0.97; p.attr_defend = 1.15; p.attr_shoot = 0.85; p.attr_pass = 1.00
		3: # midfielder
			p.attr_pace = 1.03; p.attr_defend = 1.00; p.attr_shoot = 1.05; p.attr_pass = 1.10
		_: # forward
			p.attr_pace = 1.10; p.attr_defend = 0.85; p.attr_shoot = 1.18; p.attr_pass = 1.00

# --- World build ----------------------------------------------------------------------------

func _build_environment() -> void:
	var world_env := WorldEnvironment.new()
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.53, 0.81, 0.92)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.6, 0.6, 0.6)
	env.ambient_light_energy = 1.0
	world_env.environment = env
	add_child(world_env)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-55, -40, 0)
	sun.shadow_enabled = true
	add_child(sun)

func _build_pitch() -> void:
	# Ground extends a bit past the goals so the ball doesn't fall into the void behind the net.
	# Ground extends well past the lines (with a darker surround) so the ball can go out of
	# play for throw-ins / corners without falling into the void.
	var gw := PITCH_WIDTH + 16.0
	var gl := PITCH_LENGTH + 16.0
	var ground := StaticBody3D.new()
	var gmesh := MeshInstance3D.new()
	var gbox := BoxMesh.new()
	gbox.size = Vector3(gw, 0.2, gl)
	gmesh.mesh = gbox
	gmesh.material_override = _mat(Color(0.12, 0.42, 0.18))
	gmesh.position = Vector3(0, -0.1, 0)
	ground.add_child(gmesh)
	var gcol := CollisionShape3D.new()
	var gshape := BoxShape3D.new()
	gshape.size = Vector3(gw, 0.2, gl)
	gcol.shape = gshape
	gcol.position = Vector3(0, -0.1, 0)
	ground.add_child(gcol)
	add_child(ground)

	# Mowing stripes: alternating light/dark green bands across the pitch, sitting clearly ABOVE
	# the ground box so the two don't Z-fight (that coplanar overlap was the on-screen flicker).
	var stripes := 14
	var band := PITCH_LENGTH / stripes
	for i in stripes:
		var strip := MeshInstance3D.new()
		var pl := PlaneMesh.new()
		pl.size = Vector2(PITCH_WIDTH, band)
		strip.mesh = pl
		var shade := 0.56 if i % 2 == 0 else 0.49
		strip.material_override = _mat(Color(0.15, shade, 0.23))
		strip.position = Vector3(0, 0.015, -PITCH_LENGTH / 2.0 + band * (i + 0.5))
		add_child(strip)

	# Outer safety walls a few metres beyond the lines — the ball leaves the field of play
	# (triggering a set piece) but can't roll away forever.
	var ow := HALF_W + 5.0
	var ol := HALF_L + 5.0
	_add_wall(Vector3(ow, 1.5, 0), Vector3(0.5, 3, (ol) * 2.0))
	_add_wall(Vector3(-ow, 1.5, 0), Vector3(0.5, 3, (ol) * 2.0))
	_add_wall(Vector3(0, 1.5, ol), Vector3(ow * 2.0, 3, 0.5))
	_add_wall(Vector3(0, 1.5, -ol), Vector3(ow * 2.0, 3, 0.5))

	_build_end(HALF_L, Player.HOME)   # HOME attacks +Z, so +Z goal = HOME scores
	_build_end(-HALF_L, Player.AWAY)

	_build_markings()
	_build_stands()

func _build_end(z_line: float, scoring_team: int) -> void:
	var s := signf(z_line)
	# Net backstop spanning ONLY the goal mouth: catches shots on target so they settle in the
	# net, while balls wide of the posts cross the byline for a corner or goal kick.
	_add_wall(Vector3(0, 1.1, z_line + s * 2.2), Vector3(MOUTH + 0.3, 2.2, 0.4))

	# Cosmetic frame + net.
	_add_goal(Vector3(0, 0, z_line))
	_add_net(z_line)

	# Goal sensor in the mouth.
	var area := Area3D.new()
	var acol := CollisionShape3D.new()
	var ashape := BoxShape3D.new()
	ashape.size = Vector3(MOUTH, 3.0, 1.2)
	acol.shape = ashape
	area.add_child(acol)
	area.position = Vector3(0, 1.0, z_line + s * 1.2)
	add_child(area)
	area.body_entered.connect(_on_goal_scored.bind(scoring_team))

func _add_wall(pos: Vector3, size: Vector3) -> void:
	var wall := StaticBody3D.new()
	var col := CollisionShape3D.new()
	var shape := BoxShape3D.new()
	shape.size = size
	col.shape = shape
	wall.add_child(col)
	wall.position = pos
	add_child(wall)

func _add_goal(base: Vector3) -> void:
	var mat := _mat(Color(0.95, 0.95, 0.97))
	var height := 2.2
	var thick := 0.25
	for xoff in [-MOUTH / 2.0, MOUTH / 2.0]:
		var post := MeshInstance3D.new()
		var pmesh := BoxMesh.new()
		pmesh.size = Vector3(thick, height, thick)
		post.mesh = pmesh
		post.material_override = mat
		post.position = base + Vector3(xoff, height / 2.0, 0)
		add_child(post)
	var bar := MeshInstance3D.new()
	var bmesh := BoxMesh.new()
	bmesh.size = Vector3(MOUTH + thick, thick, thick)
	bar.mesh = bmesh
	bar.material_override = mat
	bar.position = base + Vector3(0, height, 0)
	add_child(bar)

func _build_markings() -> void:
	var y := 0.03
	# Boundary.
	_line(Vector3(0, y, HALF_L - 0.3), Vector3(PITCH_WIDTH - 0.6, 0.04, 0.15))
	_line(Vector3(0, y, -(HALF_L - 0.3)), Vector3(PITCH_WIDTH - 0.6, 0.04, 0.15))
	_line(Vector3(HALF_W - 0.3, y, 0), Vector3(0.15, 0.04, PITCH_LENGTH - 0.6))
	_line(Vector3(-(HALF_W - 0.3), y, 0), Vector3(0.15, 0.04, PITCH_LENGTH - 0.6))
	# Halfway line.
	_line(Vector3(0, y, 0), Vector3(PITCH_WIDTH - 0.6, 0.04, 0.15))
	# Center circle.
	var circle := MeshInstance3D.new()
	var torus := TorusMesh.new()
	torus.inner_radius = 2.9
	torus.outer_radius = 3.05
	circle.mesh = torus
	circle.material_override = _line_mat()
	circle.rotation_degrees = Vector3(90, 0, 0)
	circle.position = Vector3(0, y, 0)
	add_child(circle)
	# Penalty boxes.
	for zs in [1.0, -1.0]:
		var gz: float = HALF_L * zs
		var depth := 6.0
		var boxw := 12.0
		_line(Vector3(0, y, gz - zs * depth), Vector3(boxw, 0.04, 0.15))
		_line(Vector3(boxw / 2.0, y, gz - zs * depth / 2.0), Vector3(0.15, 0.04, depth))
		_line(Vector3(-boxw / 2.0, y, gz - zs * depth / 2.0), Vector3(0.15, 0.04, depth))

func _build_stands() -> void:
	var mat := _mat(Color(0.32, 0.35, 0.4))
	for xs in [1.0, -1.0]:
		for tier in range(3):
			var x: float = (HALF_W + 1.5 + tier * 1.3) * xs
			var h := 1.2 + tier * 1.0
			_visual_box(Vector3(1.3, h, PITCH_LENGTH), Vector3(x, h / 2.0, 0), mat)

func _line(pos: Vector3, size: Vector3) -> void:
	_visual_box(size, pos, _line_mat())

func _line_mat() -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(0.95, 0.97, 0.95)
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	return m

func _visual_box(size: Vector3, pos: Vector3, mat: StandardMaterial3D) -> void:
	var m := MeshInstance3D.new()
	var b := BoxMesh.new()
	b.size = size
	m.mesh = b
	m.material_override = mat
	m.position = pos
	add_child(m)

func _add_net(z_line: float) -> void:
	var s := signf(z_line)
	var height := 2.2
	var depth := 1.6
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(1, 1, 1, 0.25)
	mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	mat.cull_mode = BaseMaterial3D.CULL_DISABLED
	var back_z := z_line + s * depth
	_net_panel(Vector3(MOUTH + 0.3, height, 0.05), Vector3(0, height / 2.0, back_z), mat)          # back
	_net_panel(Vector3(MOUTH + 0.3, 0.05, depth), Vector3(0, height, z_line + s * depth / 2.0), mat) # roof
	_net_panel(Vector3(0.05, height, depth), Vector3(MOUTH / 2.0, height / 2.0, z_line + s * depth / 2.0), mat)
	_net_panel(Vector3(0.05, height, depth), Vector3(-MOUTH / 2.0, height / 2.0, z_line + s * depth / 2.0), mat)

func _net_panel(size: Vector3, pos: Vector3, mat: StandardMaterial3D) -> void:
	var m := MeshInstance3D.new()
	var b := BoxMesh.new()
	b.size = size
	m.mesh = b
	m.material_override = mat
	m.position = pos
	add_child(m)

func _make_marker() -> MeshInstance3D:
	var m := MeshInstance3D.new()
	var cone := CylinderMesh.new()
	cone.top_radius = 0.0
	cone.bottom_radius = 0.32
	cone.height = 0.55
	m.mesh = cone
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(1.0, 0.9, 0.15)
	mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	m.material_override = mat
	m.rotation_degrees = Vector3(180, 0, 0) # tip points down
	return m

func _mat(c: Color) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	return m
