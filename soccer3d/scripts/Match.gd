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

enum Phase { KICKOFF, PLAYING, PAUSED, FULLTIME }

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
			time_left = maxf(time_left - delta, 0.0)
			hud.set_clock(time_left, half)
			if time_left <= 0.0:
				_end_of_half()

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
			if p.team != t or p.is_gk:
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
		if p.team == team and not p.is_gk and p.has_possession():
			if team == Player.HOME:
				_forced_active = null
			return p
	if team == Player.HOME and _forced_active != null:
		return _forced_active
	return _nearest_outfielder(team)

func _home_outfielders() -> Array[Player]:
	var a: Array[Player] = []
	for p in players:
		if p.team == Player.HOME and not p.is_gk:
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
		if p.team != team or p.is_gk:
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
		if p.team != from.team or p == from or p.is_gk:
			continue
		var dist := p.global_position.distance_to(from.global_position)
		if dist < 2.5 or dist > 26.0:
			continue
		var forwardness := (p.global_position.z - from.global_position.z) * from.attack_sign()
		var s := forwardness - dist * 0.2
		if s > best_score:
			best_score = s
			best = p
	return best

func opponent_within(p: Player, r: float) -> bool:
	for o in players:
		if o.team == p.team:
			continue
		if o.global_position.distance_to(p.global_position) < r:
			return true
	return false

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
	_kickoff()

func _end_of_half() -> void:
	if half == 1:
		half = 2
		time_left = MatchConfig.half_seconds
		hud.set_clock(time_left, half)
		_kickoff()
	else:
		phase = Phase.FULLTIME
		_set_frozen(true)
		MatchConfig.save_result(score_home, score_away)
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
	hud.set_score(0, 0)
	hud.hide_overlays()
	_kickoff()

func quit_to_menu() -> void:
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
		p.ball = ball
		p.world = self
		p.position = Vector3(pos.x, 0.0, pos.z)
		add_child(p)
		players.append(p)

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
	var ground := StaticBody3D.new()
	var gmesh := MeshInstance3D.new()
	var gbox := BoxMesh.new()
	gbox.size = Vector3(PITCH_WIDTH, 0.2, PITCH_LENGTH + 8.0)
	gmesh.mesh = gbox
	gmesh.material_override = _mat(Color(0.16, 0.55, 0.24))
	gmesh.position = Vector3(0, -0.1, 0)
	ground.add_child(gmesh)
	var gcol := CollisionShape3D.new()
	var gshape := BoxShape3D.new()
	gshape.size = Vector3(PITCH_WIDTH, 0.2, PITCH_LENGTH + 8.0)
	gcol.shape = gshape
	gcol.position = Vector3(0, -0.1, 0)
	ground.add_child(gcol)
	add_child(ground)

	# Side walls (full length).
	_add_wall(Vector3(HALF_W, 1, 0), Vector3(0.5, 2, PITCH_LENGTH))
	_add_wall(Vector3(-HALF_W, 1, 0), Vector3(0.5, 2, PITCH_LENGTH))

	_build_end(HALF_L, Player.HOME)   # HOME attacks +Z, so +Z goal = HOME scores
	_build_end(-HALF_L, Player.AWAY)

	_build_markings()
	_build_stands()

func _build_end(z_line: float, scoring_team: int) -> void:
	var s := signf(z_line)
	var gap := MOUTH / 2.0
	var seg_w := HALF_W - gap
	var cx := gap + seg_w / 2.0
	# Two wall segments flanking the goal mouth.
	_add_wall(Vector3(cx, 1, z_line), Vector3(seg_w, 2, 0.5))
	_add_wall(Vector3(-cx, 1, z_line), Vector3(seg_w, 2, 0.5))
	# Back wall behind the goal to stop the ball.
	_add_wall(Vector3(0, 1, z_line + s * 3.0), Vector3(PITCH_WIDTH, 2, 0.5))

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
