extends Node3D
## Flick Soccer, ported into Godot: turn-based table football. Drag from one of your discs toward
## where you want it to go, release to flick. Knock the ball into the opponent's goal. Uses Godot 3D
## physics (discs/ball are y-locked RigidBodies). Blue = you, Red = CPU (or pass-and-play).

const MENU_SCENE := "res://scenes/MainMenu.tscn"
const HALF_W := 15.0
const HALF_L := 22.0
const MOUTH := 10.0
const DISC_R := 1.2
const BALL_R := 0.7
const MAX_DRAG := 8.0
const MAX_IMPULSE := 46.0
const REST_SPEED := 0.35
const GOALS_TO_WIN := 5
const TURN_LIMIT := 24

const BLUE := 0
const RED := 1

enum Phase { AIMING, SIMULATING, OVER }

# Blue formation (defends -Z, attacks +Z); Red mirrored.
const FORMATION: Array[Vector2] = [Vector2(0, -18), Vector2(-7, -11), Vector2(7, -11), Vector2(0, -5), Vector2(0, 8)]

var discs: Array[RigidBody3D] = []
var ball: RigidBody3D
var camera: Camera3D
var _aim_line: MeshInstance3D

var phase := Phase.AIMING
var turn := BLUE
var turn_number := 1
var score_blue := 0
var score_red := 0
var _sim_time := 0.0
var _resetting := false
var _ai_pending := false
var _ai_turn_seed := -1
var _kickoff_team := BLUE

var _aim_disc: RigidBody3D
var _aim_vec := Vector3.ZERO

var _score_label: Label
var _turn_label: Label

func _ready() -> void:
	_build_environment()
	_build_pitch()
	ball = _make_ball()
	ball.position = Vector3(0, BALL_R, 0)
	add_child(ball)
	_spawn_team(BLUE, Color(0.2, 0.45, 0.95))
	_spawn_team(RED, Color(0.92, 0.28, 0.28))

	camera = Camera3D.new()
	camera.position = Vector3(0, 34, 20)
	camera.fov = 50.0
	add_child(camera)
	camera.look_at(Vector3(0, 0, -1), Vector3.UP)

	_aim_line = MeshInstance3D.new()
	var m := StandardMaterial3D.new()
	m.albedo_color = Color(1, 1, 0.2, 0.9)
	m.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	_aim_line.material_override = m
	_aim_line.visible = false
	add_child(_aim_line)

	_build_hud()
	_kickoff(BLUE)

func _physics_process(delta: float) -> void:
	match phase:
		Phase.SIMULATING:
			_sim_time += delta
			if _sim_time > 0.35 and _all_at_rest():
				_end_turn()
		Phase.AIMING:
			if turn == RED and not MatchConfig.two_player and not _ai_pending:
				_ai_pending = true
				_schedule_ai()

func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel"):
		get_tree().change_scene_to_file(MENU_SCENE)
		return
	if phase != Phase.AIMING:
		return
	if not (_can_human_control()):
		return

	if event is InputEventScreenTouch or event is InputEventMouseButton:
		if event.pressed:
			_begin_aim(event.position)
		else:
			_release_aim()
	elif (event is InputEventScreenDrag or event is InputEventMouseMotion) and _aim_disc != null:
		_update_aim(event.position)

func _can_human_control() -> bool:
	return turn == BLUE or MatchConfig.two_player

# --- Aiming / flicking ----------------------------------------------------------------------

func _begin_aim(screen: Vector2) -> void:
	var gp := _ground_point(screen)
	if gp == Vector3.INF:
		return
	_aim_disc = _pick_disc(turn, gp)

func _update_aim(screen: Vector2) -> void:
	var gp := _ground_point(screen)
	if gp == Vector3.INF or _aim_disc == null:
		return
	var v := gp - _aim_disc.global_position
	v.y = 0.0
	if v.length() > MAX_DRAG:
		v = v.normalized() * MAX_DRAG
	_aim_vec = v
	_draw_aim()

func _release_aim() -> void:
	if _aim_disc != null and _aim_vec.length() > 0.6:
		var power := clampf(_aim_vec.length() / MAX_DRAG, 0.0, 1.0)
		_aim_disc.apply_central_impulse(_aim_vec.normalized() * power * MAX_IMPULSE)
		Sfx.play("kick")
		phase = Phase.SIMULATING
		_sim_time = 0.0
	_aim_disc = null
	_aim_vec = Vector3.ZERO
	_aim_line.visible = false

func _draw_aim() -> void:
	if _aim_disc == null or _aim_vec.length() < 0.2:
		_aim_line.visible = false
		return
	var start := _aim_disc.global_position
	var end := start + _aim_vec
	var mid := (start + end) * 0.5
	var box := BoxMesh.new()
	box.size = Vector3(0.25, 0.1, _aim_vec.length())
	_aim_line.mesh = box
	_aim_line.global_position = Vector3(mid.x, 0.4, mid.z)
	_aim_line.look_at(Vector3(end.x, 0.4, end.z), Vector3.UP)
	_aim_line.visible = true

func _pick_disc(team: int, gp: Vector3) -> RigidBody3D:
	var best: RigidBody3D = null
	var best_d := DISC_R + 1.2
	for d in discs:
		if int(d.get_meta("team")) != team:
			continue
		var dist := Vector2(d.global_position.x - gp.x, d.global_position.z - gp.z).length()
		if dist < best_d:
			best_d = dist
			best = d
	return best

func _ground_point(screen: Vector2) -> Vector3:
	var from := camera.project_ray_origin(screen)
	var dir := camera.project_ray_normal(screen)
	if absf(dir.y) < 1e-5:
		return Vector3.INF
	var t := -from.y / dir.y
	if t < 0.0:
		return Vector3.INF
	return from + dir * t

# --- Turn flow ------------------------------------------------------------------------------

func _all_at_rest() -> bool:
	if ball.linear_velocity.length() > REST_SPEED:
		return false
	for d in discs:
		if d.linear_velocity.length() > REST_SPEED:
			return false
	return true

func _end_turn() -> void:
	if _resetting:
		return
	_ai_pending = false
	turn_number += 1
	if turn_number > TURN_LIMIT:
		_finish()
		return
	turn = RED if turn == BLUE else BLUE
	phase = Phase.AIMING
	_update_hud()

func _schedule_ai() -> void:
	_ai_turn_seed = turn_number
	get_tree().create_timer(0.8).timeout.connect(_ai_try)

func _ai_try() -> void:
	if phase == Phase.AIMING and turn == RED and turn_number == _ai_turn_seed:
		_ai_flick()

func _ai_flick() -> void:
	# Pick the red disc best placed to knock the ball toward Blue's -Z... Red attacks -Z.
	var goal := Vector3(0, 0, -HALF_L)
	var best: RigidBody3D = null
	var best_score := -INF
	for d in discs:
		if int(d.get_meta("team")) != RED:
			continue
		var to_ball := ball.global_position - d.global_position
		to_ball.y = 0.0
		var to_goal := goal - ball.global_position
		to_goal.y = 0.0
		var align := to_ball.normalized().dot(to_goal.normalized())
		var s := align - to_ball.length() * 0.03
		if s > best_score:
			best_score = s
			best = d
	if best == null:
		return
	var dir := (ball.global_position - best.global_position)
	dir.y = 0.0
	var noise := (0.35 - MatchConfig.ai_skill() * 0.3)
	dir = dir.normalized().rotated(Vector3.UP, randf_range(-noise, noise))
	best.apply_central_impulse(dir * MAX_IMPULSE * randf_range(0.8, 1.0))
	Sfx.play("kick")
	phase = Phase.SIMULATING
	_sim_time = 0.0

func _on_goal(body: Node, scoring_team: int) -> void:
	if phase == Phase.OVER or _resetting or body != ball:
		return
	if scoring_team == BLUE:
		score_blue += 1
	else:
		score_red += 1
	Sfx.play("goal")
	_update_hud()
	if score_blue >= GOALS_TO_WIN or score_red >= GOALS_TO_WIN:
		_finish()
		return
	_resetting = true
	_kickoff_team = RED if scoring_team == BLUE else BLUE # conceding team kicks off
	get_tree().create_timer(1.2).timeout.connect(_do_kickoff_after_goal)

func _do_kickoff_after_goal() -> void:
	_kickoff(_kickoff_team)
	_resetting = false

func _finish() -> void:
	phase = Phase.OVER
	Sfx.play("whistle")
	MatchConfig.save_result(score_blue, score_red)
	var res := "DRAW"
	if score_blue > score_red:
		res = "BLUE WINS"
	elif score_red > score_blue:
		res = "RED WINS"
	_turn_label.text = "%s  %d - %d" % [res, score_blue, score_red]

func _kickoff(to_team: int) -> void:
	_reset_bodies()
	turn = to_team
	phase = Phase.AIMING
	_ai_pending = false
	Sfx.play("whistle")
	_update_hud()

func _reset_bodies() -> void:
	ball.linear_velocity = Vector3.ZERO
	ball.angular_velocity = Vector3.ZERO
	ball.global_position = Vector3(0, BALL_R, 0)
	var bi := 0
	var ri := 0
	for d in discs:
		var team := int(d.get_meta("team"))
		var slot: Vector2 = FORMATION[bi if team == BLUE else ri]
		if team == BLUE:
			bi += 1
			d.global_position = Vector3(slot.x, 0.31, slot.y)
		else:
			ri += 1
			d.global_position = Vector3(slot.x, 0.31, -slot.y)
		d.linear_velocity = Vector3.ZERO
		d.angular_velocity = Vector3.ZERO

# --- Build ----------------------------------------------------------------------------------

func _spawn_team(team: int, color: Color) -> void:
	for slot in FORMATION:
		var d := _make_disc(team, color)
		var z := slot.y if team == BLUE else -slot.y
		d.position = Vector3(slot.x, 0.31, z)
		add_child(d)
		discs.append(d)

func _make_disc(team: int, color: Color) -> RigidBody3D:
	var d := RigidBody3D.new()
	d.axis_lock_linear_y = true
	d.axis_lock_angular_x = true
	d.axis_lock_angular_z = true
	d.mass = 3.0
	d.linear_damp = 2.6
	d.angular_damp = 3.0
	d.set_meta("team", team)
	var mesh := MeshInstance3D.new()
	var cyl := CylinderMesh.new()
	cyl.top_radius = DISC_R
	cyl.bottom_radius = DISC_R
	cyl.height = 0.6
	mesh.mesh = cyl
	mesh.material_override = _mat(color)
	d.add_child(mesh)
	var col := CollisionShape3D.new()
	var shape := CylinderShape3D.new()
	shape.radius = DISC_R
	shape.height = 0.6
	col.shape = shape
	d.add_child(col)
	return d

func _make_ball() -> RigidBody3D:
	var b := RigidBody3D.new()
	b.axis_lock_linear_y = true
	b.mass = 1.0
	b.linear_damp = 1.4
	b.angular_damp = 1.5
	b.can_sleep = false
	var mesh := MeshInstance3D.new()
	var s := SphereMesh.new()
	s.radius = BALL_R
	s.height = BALL_R * 2.0
	mesh.mesh = s
	mesh.material_override = _mat(Color(0.96, 0.96, 0.96))
	b.add_child(mesh)
	var col := CollisionShape3D.new()
	var shape := SphereShape3D.new()
	shape.radius = BALL_R
	col.shape = shape
	b.add_child(col)
	return b

func _build_environment() -> void:
	var we := WorldEnvironment.new()
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.12, 0.16, 0.2)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.7, 0.7, 0.7)
	env.ambient_light_energy = 1.0
	we.environment = env
	add_child(we)
	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-70, -30, 0)
	add_child(sun)

func _build_pitch() -> void:
	var ground := MeshInstance3D.new()
	var gb := BoxMesh.new()
	gb.size = Vector3(HALF_W * 2, 0.2, HALF_L * 2)
	ground.mesh = gb
	ground.material_override = _mat(Color(0.16, 0.5, 0.24))
	ground.position = Vector3(0, -0.1, 0)
	add_child(ground)
	# Side walls (full length).
	_wall(Vector3(HALF_W, 0.75, 0), Vector3(0.5, 1.5, HALF_L * 2))
	_wall(Vector3(-HALF_W, 0.75, 0), Vector3(0.5, 1.5, HALF_L * 2))
	_build_end(HALF_L, BLUE)
	_build_end(-HALF_L, RED)
	_markings()

func _build_end(z_line: float, scoring_team: int) -> void:
	var s := signf(z_line)
	var gap := MOUTH / 2.0
	var seg := HALF_W - gap
	var cx := gap + seg / 2.0
	_wall(Vector3(cx, 0.75, z_line), Vector3(seg, 1.5, 0.5))
	_wall(Vector3(-cx, 0.75, z_line), Vector3(seg, 1.5, 0.5))
	_wall(Vector3(0, 0.75, z_line + s * 3.0), Vector3(HALF_W * 2, 1.5, 0.5)) # back net wall
	# posts
	var mat := _mat(Color(0.95, 0.95, 0.97))
	for xoff in [-gap, gap]:
		var p := MeshInstance3D.new()
		var pm := BoxMesh.new()
		pm.size = Vector3(0.3, 2.0, 0.3)
		p.mesh = pm
		p.material_override = mat
		p.position = Vector3(xoff, 1.0, z_line)
		add_child(p)
	# goal sensor
	var area := Area3D.new()
	var acol := CollisionShape3D.new()
	var ashape := BoxShape3D.new()
	ashape.size = Vector3(MOUTH, 2.0, 1.4)
	acol.shape = ashape
	area.add_child(acol)
	area.position = Vector3(0, 0.8, z_line + s * 1.2)
	add_child(area)
	area.body_entered.connect(_on_goal.bind(scoring_team))

func _markings() -> void:
	var line := StandardMaterial3D.new()
	line.albedo_color = Color(0.95, 0.97, 0.95)
	line.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	var half := MeshInstance3D.new()
	var hb := BoxMesh.new()
	hb.size = Vector3(HALF_W * 2, 0.02, 0.2)
	half.mesh = hb
	half.material_override = line
	half.position = Vector3(0, 0.02, 0)
	add_child(half)
	var circle := MeshInstance3D.new()
	var torus := TorusMesh.new()
	torus.inner_radius = 3.4
	torus.outer_radius = 3.6
	circle.mesh = torus
	circle.material_override = line
	circle.rotation_degrees = Vector3(90, 0, 0)
	circle.position = Vector3(0, 0.02, 0)
	add_child(circle)

func _wall(pos: Vector3, size: Vector3) -> void:
	var w := StaticBody3D.new()
	var col := CollisionShape3D.new()
	var shape := BoxShape3D.new()
	shape.size = size
	col.shape = shape
	w.add_child(col)
	w.position = pos
	add_child(w)

func _build_hud() -> void:
	var layer := CanvasLayer.new()
	add_child(layer)
	var root := Control.new()
	root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.position = Vector2.ZERO
	root.size = get_viewport().get_visible_rect().size
	get_viewport().size_changed.connect(func(): root.size = get_viewport().get_visible_rect().size)
	layer.add_child(root)

	_score_label = Label.new()
	_score_label.add_theme_font_size_override("font_size", 30)
	_score_label.set_anchors_preset(Control.PRESET_TOP_WIDE)
	_score_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_score_label.position = Vector2(0, 10)
	root.add_child(_score_label)

	_turn_label = Label.new()
	_turn_label.add_theme_font_size_override("font_size", 22)
	_turn_label.set_anchors_preset(Control.PRESET_TOP_WIDE)
	_turn_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_turn_label.position = Vector2(0, 48)
	root.add_child(_turn_label)

	var back := Button.new()
	back.text = "Menu"
	back.position = Vector2(16, 16)
	back.custom_minimum_size = Vector2(90, 40)
	back.pressed.connect(func(): get_tree().change_scene_to_file(MENU_SCENE))
	root.add_child(back)

	var hint := Label.new()
	hint.text = "Drag from your disc to aim, release to flick"
	hint.add_theme_font_size_override("font_size", 14)
	hint.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
	hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	hint.position = Vector2(0, -30)
	root.add_child(hint)

	_update_hud()

func _update_hud() -> void:
	if _score_label != null:
		_score_label.text = "BLUE  %d - %d  RED" % [score_blue, score_red]
	if _turn_label != null:
		_turn_label.text = ("BLUE to flick" if turn == BLUE else "RED to flick") + "   ·   turn %d" % turn_number

func _mat(c: Color) -> StandardMaterial3D:
	var m := StandardMaterial3D.new()
	m.albedo_color = c
	return m
