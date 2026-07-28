extends CharacterBody3D
class_name Player
## A footballer with a simple humanoid body (head/torso/arms/legs) in team kit colors with a shirt
## number, plus a run animation. Driven by human input (keyboard + on-screen touch) when active, or
## by AI; goalkeepers use a dedicated behavior. Feet sit at the node origin (y = 0).

const HOME := 0
const AWAY := 1
const HALF_W := 12.5
const HALF_L := 20.0

@export var walk_speed := 7.6
@export var sprint_speed := 12.0
@export var control_radius := 1.6
@export var dribble_distance := 1.05
@export var pass_power := 13.0
@export var min_shot_power := 12.0
@export var max_shot_power := 26.0

const STAMINA_DRAIN := 0.28
const STAMINA_REGEN := 0.16
const CHARGE_RATE := 1.5
const KICK_COOLDOWN := 0.35
const LUNGE_TIME := 0.28
const HIGH_BALL_Y := 1.0
const ACCEL := 30.0      # how fast we reach target speed (units/s^2)
const DECEL := 38.0      # quicker slow-down when the stick is released
const TURN_RATE := 9.0   # facing turn speed (higher = snappier)
const CURL := 7.0        # sideways spin imparted by curved shots/passes
const SKIN := Color(0.86, 0.66, 0.5)
const SOCKS := Color(0.11, 0.11, 0.14)

# Set by the match before add_child:
var ball: Ball
var world
var team := HOME
var is_gk := false
var home_pos := Vector3.ZERO
var kit_color := Color(0.2, 0.45, 0.95)
var number := 1
var skill := 0.7
# Per-player attributes (1.0 = average). Set by the match from a position profile.
var attr_pace := 1.0
var attr_shoot := 1.0
var attr_pass := 1.0
var attr_defend := 1.0
var sent_off := false # removed from play by a red card
var yellows := 0

# Set each frame by the match:
var is_human := false
var role_chase := false

var facing := Vector3(0.0, 0.0, -1.0)
var _move_dir := Vector3.ZERO # last non-zero input direction (for curl/finesse)
var stamina := 1.0
var charge := 0.0
var _kick_cooldown := 0.0
var _lunge := 0.0
var _tackle_cd := 0.0
var _controllable := false
var _anim_phase := 0.0
var _leg_l: Node3D
var _leg_r: Node3D
var _arm_l: Node3D
var _arm_r: Node3D
var _model_root: Node3D          # loaded rigged model (null when using primitives)
var _model_anim: AnimationPlayer # its AnimationPlayer
var _anim_idle := ""             # clip names mapped from whatever the model provides
var _anim_run := ""
var _anim_kick := ""
var _anim_action := 0.0          # seconds left holding a one-shot action clip (kick/tackle)

func attack_sign() -> float:
	return 1.0 if team == HOME else -1.0

func _ready() -> void:
	_build_body()
	var col := CollisionShape3D.new()
	var shape := CapsuleShape3D.new()
	shape.radius = 0.4
	shape.height = 1.8
	col.shape = shape
	col.position = Vector3(0, 0.9, 0)
	add_child(col)

func _build_body() -> void:
	# Phase B art hook: if a rigged humanoid model has been dropped in, use it; otherwise
	# fall back to the stylized primitive body. Lets real models be added without code changes.
	if _try_load_model():
		_add_number_label()
		return

	var jersey := kit_color
	var shorts := kit_color.darkened(0.5)

	_add_box(Vector3(0.48, 0.30, 0.27), Vector3(0, 0.92, 0), shorts)          # shorts/hips
	_add_box(Vector3(0.50, 0.60, 0.26), Vector3(0, 1.18, 0), jersey)          # torso
	_add_sphere(0.15, Vector3(0, 1.62, 0), SKIN)                              # head

	_leg_l = _limb(Vector3(-0.13, 0.84, 0), Vector3(0.16, 0.82, 0.18), SOCKS)
	_leg_r = _limb(Vector3(0.13, 0.84, 0), Vector3(0.16, 0.82, 0.18), SOCKS)
	_arm_l = _limb(Vector3(-0.34, 1.42, 0), Vector3(0.13, 0.55, 0.13), jersey)
	_arm_r = _limb(Vector3(0.34, 1.42, 0), Vector3(0.13, 0.55, 0.13), SKIN)

	_add_number_label()

const MODEL_PATH := "res://assets/players/player.glb"
const MODEL_YAW := 180.0           # face the body's forward (-Z)
const MODEL_HEIGHT := 1.85         # auto-scale the model to this height (metres)

## Loads the rigged humanoid model (kit-tinted, animated) if present; falls back to the
## primitive body otherwise so the game always runs.
func _try_load_model() -> bool:
	if not ResourceLoader.exists(MODEL_PATH):
		return false
	var packed := load(MODEL_PATH) as PackedScene
	if packed == null:
		return false
	var model := packed.instantiate() as Node3D
	if model == null:
		return false
	model.rotation_degrees = Vector3(0, MODEL_YAW, 0)
	add_child(model)
	_model_root = model
	_fit_model(model)

	# Opaque team-colored kit (no transparency → no alpha-sort flicker).
	var kitmat := StandardMaterial3D.new()
	kitmat.albedo_color = kit_color
	kitmat.roughness = 0.75
	for m in _find_nodes(model, "MeshInstance3D"):
		(m as MeshInstance3D).material_override = kitmat

	# Map whatever clips the model ships to game states (works for rich or single-clip models).
	var players := _find_nodes(model, "AnimationPlayer")
	if not players.is_empty():
		_model_anim = players[0] as AnimationPlayer
		_map_animations()
	return true

## Categorize the model's animation clips into idle / run / kick by name (case-insensitive),
## with sensible fallbacks, and make idle/run loop.
func _map_animations() -> void:
	var list := _model_anim.get_animation_list()
	if list.is_empty():
		return
	for n in list:
		var low := String(n).to_lower()
		if _anim_run == "" and (low.contains("run")):
			_anim_run = n
		if _anim_idle == "" and (low.contains("idle") or low.contains("stand")):
			_anim_idle = n
		if _anim_kick == "" and (low.contains("punch") or low.contains("kick")):
			_anim_kick = n
	if _anim_run == "": # no "run" clip → try walk, else the first clip
		for n in list:
			if String(n).to_lower().contains("walk"):
				_anim_run = n
				break
	if _anim_run == "":
		_anim_run = list[0]
	for key in [_anim_run, _anim_idle]:
		if key != "":
			var a := _model_anim.get_animation(key)
			if a != null:
				a.loop_mode = Animation.LOOP_LINEAR
	if _anim_idle != "":
		_model_anim.play(_anim_idle)
	else:
		_model_anim.play(_anim_run)
		_model_anim.speed_scale = 0.0

## Fit the model to MODEL_HEIGHT and drop its feet to y=0 (handles any source model's scale).
func _fit_model(model: Node3D) -> void:
	var meshes := _find_nodes(model, "MeshInstance3D")
	if meshes.is_empty():
		return
	# Pose the skeleton now so mesh transforms are valid this frame (not next).
	for sk in _find_nodes(model, "Skeleton3D"):
		(sk as Skeleton3D).force_update_all_bone_transforms()
	var mn := Vector3(INF, INF, INF)
	var mx := -mn
	var inv := model.global_transform.affine_inverse()
	for mm in meshes:
		var mi := mm as MeshInstance3D
		var a := mi.get_aabb()
		var t := inv * mi.global_transform
		for i in 8:
			var corner := a.position + Vector3(a.size.x * (i & 1), a.size.y * ((i >> 1) & 1), a.size.z * ((i >> 2) & 1))
			var p := t * corner
			mn.x = minf(mn.x, p.x); mn.y = minf(mn.y, p.y); mn.z = minf(mn.z, p.z)
			mx.x = maxf(mx.x, p.x); mx.y = maxf(mx.y, p.y); mx.z = maxf(mx.z, p.z)
	var h := mx.y - mn.y
	if h <= 0.05:
		return # implausible measurement — leave scale alone rather than exploding the model
	var s := clampf(MODEL_HEIGHT / h, 0.05, 5.0)
	model.scale = Vector3(s, s, s)
	model.position.y = -mn.y * s

## Play a one-shot action clip (kick / tackle) for its duration, if the model has one.
func _play_action(clip: String) -> void:
	if _model_anim == null or clip == "":
		return
	var a := _model_anim.get_animation(clip)
	if a == null:
		return
	a.loop_mode = Animation.LOOP_NONE
	_model_anim.play(clip)
	_model_anim.speed_scale = 1.0
	_anim_action = minf(a.length, 0.55)

func _find_nodes(root: Node, cls: String) -> Array:
	var out: Array = []
	if root.get_class() == cls:
		out.append(root)
	for c in root.get_children():
		out.append_array(_find_nodes(c, cls))
	return out

func _add_number_label() -> void:
	# Shirt number on the back (local +Z is behind, since forward is -Z).
	var label := Label3D.new()
	label.text = str(number)
	label.font_size = 72
	label.pixel_size = 0.006
	label.outline_size = 12
	label.modulate = Color.WHITE
	label.outline_modulate = Color(0, 0, 0, 0.8)
	label.position = Vector3(0, 1.2, 0.16)
	label.rotation_degrees = Vector3(0, 180, 0)
	add_child(label)

func _limb(hip: Vector3, size: Vector3, color: Color) -> Node3D:
	var pivot := Node3D.new()
	pivot.position = hip
	add_child(pivot)
	var mesh := MeshInstance3D.new()
	var box := BoxMesh.new()
	box.size = size
	mesh.mesh = box
	mesh.material_override = _mat(color)
	mesh.position = Vector3(0, -size.y / 2.0, 0) # hang from the pivot
	pivot.add_child(mesh)
	return pivot

func _add_box(size: Vector3, pos: Vector3, color: Color) -> void:
	var m := MeshInstance3D.new()
	var b := BoxMesh.new()
	b.size = size
	m.mesh = b
	m.material_override = _mat(color)
	m.position = pos
	add_child(m)

func _add_sphere(r: float, pos: Vector3, color: Color) -> void:
	var m := MeshInstance3D.new()
	var s := SphereMesh.new()
	s.radius = r
	s.height = r * 2.0
	m.mesh = s
	m.material_override = _mat(color)
	m.position = pos
	add_child(m)

func _mat(c: Color) -> StandardMaterial3D:
	var mat := StandardMaterial3D.new()
	mat.albedo_color = c
	return mat

func set_frozen(frozen: bool) -> void:
	_controllable = not frozen
	if frozen:
		velocity = Vector3.ZERO

func _physics_process(delta: float) -> void:
	_kick_cooldown = maxf(_kick_cooldown - delta, 0.0)
	_lunge = maxf(_lunge - delta, 0.0)
	_tackle_cd = maxf(_tackle_cd - delta, 0.0)

	if not _controllable or ball == null or world == null:
		_apply_movement(Vector3.ZERO, false, delta)
		return

	if is_gk:
		_gk_control(delta)
	elif is_human:
		_human_control(delta)
	else:
		_ai_control(delta)

	if is_human or role_chase or is_gk:
		_dribble()

# --- Movement + animation -------------------------------------------------------------------

func _apply_movement(dir: Vector3, sprint: bool, delta: float) -> void:
	var moving := dir.length() > 0.1
	var use_sprint := sprint and stamina > 0.05 and moving
	var speed := (sprint_speed if use_sprint else walk_speed) * attr_pace
	if _lunge > 0.0:
		speed = sprint_speed * 1.5 * attr_pace
	if moving:
		_move_dir = dir.normalized()

	# Momentum: ease velocity toward the target instead of snapping, so players carry
	# weight, accelerate into sprints, and can't turn on a dime at speed.
	var target := Vector3.ZERO
	if _lunge > 0.0 and not moving:
		target = facing * sprint_speed * 1.5
	elif _lunge > 0.0:
		target = _move_dir * speed
	elif moving:
		target = dir.normalized() * speed
	var rate := (ACCEL if (moving or _lunge > 0.0) else DECEL) * delta
	velocity.x = move_toward(velocity.x, target.x, rate)
	velocity.z = move_toward(velocity.z, target.z, rate)
	velocity.y = 0.0
	move_and_slide()

	# Turn toward the direction of travel at a finite rate (snappier at low speed).
	var travel := Vector3(velocity.x, 0.0, velocity.z)
	if travel.length() > 0.4:
		var want := travel.normalized()
		facing = facing.lerp(want, clampf(TURN_RATE * delta, 0.0, 1.0)).normalized()
		look_at(global_position + facing, Vector3.UP)
	if use_sprint:
		stamina = maxf(stamina - STAMINA_DRAIN * delta, 0.0)
	else:
		stamina = minf(stamina + STAMINA_REGEN * delta, 1.0)
	_animate(moving, delta)

func _animate(moving: bool, delta: float) -> void:
	# Rigged model: pick idle / run / one-shot action clips based on state.
	if _model_anim != null:
		if _anim_action > 0.0:
			_anim_action -= delta
			return # let the kick/tackle clip play out
		if moving:
			if _anim_run != "" and _model_anim.current_animation != _anim_run:
				_model_anim.play(_anim_run)
			var spd := clampf(velocity.length() / walk_speed, 0.7, 2.2)
			_model_anim.speed_scale = spd
		elif _anim_idle != "":
			if _model_anim.current_animation != _anim_idle:
				_model_anim.play(_anim_idle)
			_model_anim.speed_scale = 1.0
		else:
			_model_anim.speed_scale = 0.0 # single-clip model with no idle → freeze
		return
	if _leg_l == null:
		return
	var swing := 0.0
	if moving:
		_anim_phase += delta * (7.0 + (velocity.length() / sprint_speed) * 8.0)
		swing = sin(_anim_phase) * 0.7
	else:
		_anim_phase = 0.0
	_leg_l.rotation.x = lerpf(_leg_l.rotation.x, swing, 0.3)
	_leg_r.rotation.x = lerpf(_leg_r.rotation.x, -swing, 0.3)
	_arm_l.rotation.x = lerpf(_arm_l.rotation.x, -swing * 0.6, 0.3)
	_arm_r.rotation.x = lerpf(_arm_r.rotation.x, swing * 0.6, 0.3)

# --- Human (keyboard + touch) ---------------------------------------------------------------

func _human_control(delta: float) -> void:
	var kb := Input.get_vector("move_left", "move_right", "move_up", "move_down")
	var mv := Vector2(kb.x + Touch.move.x, kb.y + Touch.move.y)
	if mv.length() > 1.0:
		mv = mv.normalized()
	_apply_movement(Vector3(mv.x, 0.0, mv.y), Input.is_action_pressed("sprint") or Touch.sprint, delta)

	if Input.is_action_pressed("kick_shoot") or Touch.shoot_held:
		charge = minf(charge + CHARGE_RATE * delta, 1.0)
	var released := Input.is_action_just_released("kick_shoot")
	if Touch.consume_shoot_released():
		released = true
	if released:
		_shoot(charge)
		charge = 0.0
	var do_pass := Input.is_action_just_pressed("kick_pass")
	if Touch.consume_pass():
		do_pass = true
	if do_pass:
		_pass()
	var do_through := Input.is_action_just_pressed("through_ball")
	if Touch.consume_through():
		do_through = true
	if do_through:
		_through_ball()
	var do_tackle := Input.is_action_just_pressed("tackle")
	if Touch.consume_tackle():
		do_tackle = true
	if do_tackle:
		_attempt_tackle()

# --- AI -------------------------------------------------------------------------------------

func _ai_control(delta: float) -> void:
	var to_ball := ball.global_position - global_position
	to_ball.y = 0.0

	if has_possession():
		var goal: Vector3 = world.opp_goal(team)
		var to_goal := goal - global_position
		to_goal.y = 0.0
		_apply_movement(to_goal.normalized(), false, delta)
		if to_goal.length() < lerpf(11.0, 17.0, skill) and absf(global_position.x) < 8.0:
			_shoot(0.6 + skill * 0.3)
		elif world.opponent_within(self, 2.6):
			_pass()
		return

	if role_chase:
		_apply_movement(to_ball.normalized(), to_ball.length() > 6.0, delta)
		if to_ball.length() < 2.0 and _tackle_cd <= 0.0 and randf() < 0.05:
			_attempt_tackle() # resolve_tackle no-ops unless an opponent actually holds the ball
		return

	var target := _formation_target()
	var to_t := target - global_position
	to_t.y = 0.0
	var dir := to_t.normalized() if to_t.length() > 0.7 else Vector3.ZERO
	_apply_movement(dir, false, delta)

func _formation_target() -> Vector3:
	var atk: bool = world.team_has_ball(team)
	var sign := attack_sign()
	var is_defender := absf(home_pos.z) > 9.0
	var is_forward := absf(home_pos.z) < 6.0

	# Whole line pushes up when we have the ball, drops when defending; forwards run deeper.
	var push := 5.0 if atk else -3.0
	if atk and is_forward:
		push += 5.0
	var base_z := home_pos.z + ball.global_position.z * 0.30 + sign * push
	var tx := clampf(home_pos.x + ball.global_position.x * 0.25, -HALF_W + 1.0, HALF_W - 1.0)
	var tz := clampf(base_z, -HALF_L + 2.0, HALF_L - 2.0)

	# Defenders mark: sit goal-side of the nearest opponent when we're defending.
	if not atk and is_defender:
		var mark: Player = world.nearest_opponent_forward(self)
		if mark != null:
			tx = clampf((mark.global_position.x + tx) * 0.5, -HALF_W + 1.0, HALF_W - 1.0)
			tz = clampf(mark.global_position.z - sign * 1.5, -HALF_L + 2.0, HALF_L - 2.0)
	return Vector3(tx, 0.0, tz)

func _gk_control(delta: float) -> void:
	var own: Vector3 = world.own_goal(team)
	# Stand just in FRONT of the own goal line (inside the pitch), not behind it.
	var line_z := own.z + attack_sign() * 1.2

	# Track the ball, but if it's flying toward goal, get across to where it will cross the line.
	var guard_x := ball.global_position.x
	var toward := ball.linear_velocity.z * signf(own.z) # >0 means heading at our goal
	if toward > 1.0 and absf(ball.linear_velocity.z) > 0.5:
		var t := (line_z - ball.global_position.z) / ball.linear_velocity.z
		if t > 0.0 and t < 1.6:
			guard_x = ball.global_position.x + ball.linear_velocity.x * t

	var target := Vector3(clampf(guard_x, -2.9, 2.9), 0.0, line_z)
	var dist_to_goal := (ball.global_position - own).length()
	if dist_to_goal < 6.5:
		target = Vector3(clampf(guard_x, -2.9, 2.9), 0.0, line_z + attack_sign() * 2.0)

	# Dive at a close, fast shot on target.
	if dist_to_goal < 5.5 and toward > 4.0 and _tackle_cd <= 0.0 and absf(guard_x) < 3.4:
		_tackle_cd = 0.6
		_lunge = LUNGE_TIME

	var to_t := target - global_position
	to_t.y = 0.0
	_apply_movement(to_t.normalized() if to_t.length() > 0.25 else Vector3.ZERO, dist_to_goal < 9.0, delta)
	if has_possession():
		_pass()

# --- Ball actions ---------------------------------------------------------------------------

func has_possession() -> bool:
	if ball == null or _kick_cooldown > 0.0:
		return false
	if ball.global_position.y > 0.8: # can't dribble a ball that's in the air
		return false
	var d := ball.global_position - global_position
	d.y = 0.0
	return d.length() <= control_radius

func _dribble() -> void:
	if not has_possession():
		return
	if world != null:
		world.note_touch(self)
	var target := global_position + facing * dribble_distance
	var desired := target - ball.global_position
	desired.y = 0.0
	# First touch: a fast incoming ball is cushioned, not instantly glued to the feet.
	var incoming := Vector2(ball.linear_velocity.x, ball.linear_velocity.z).length()
	var blend := 0.55 if incoming > 9.0 else 0.85
	ball.linear_velocity.x = lerpf(ball.linear_velocity.x, desired.x * 7.0, blend)
	ball.linear_velocity.z = lerpf(ball.linear_velocity.z, desired.z * 7.0, blend)

func _pass() -> void:
	if not _within_kick_range():
		return
	var dir := facing
	var dist := 9.0
	var target: Player = world.best_pass_target(self)
	if target != null:
		var d := target.global_position - global_position
		d.y = 0.0
		if d.length() > 0.5:
			dir = d.normalized()
			dist = d.length()
	# Weight power to the distance so short passes aren't overhit and long ones actually arrive.
	var power := clampf(dist * 1.9, 9.0, 24.0) * attr_pass
	var high := ball.global_position.y > HIGH_BALL_Y
	_launch(dir * power + Vector3.UP * (2.5 if high else 1.0))

func _shoot(c: float) -> void:
	if not _within_kick_range():
		return
	var high := ball.global_position.y > HIGH_BALL_Y
	var power := lerpf(min_shot_power, max_shot_power, clampf(c, 0.0, 1.0)) * attr_shoot
	var spin := _curl_spin(c)
	if high:
		_launch(facing * (power * 0.8) + Vector3.UP * (5.0 + c * 3.0), spin)
	else:
		_launch(facing * power + Vector3.UP * (2.0 + c * 2.5), spin)

## A driven ground pass played ahead of a forward-running teammate (into space).
func _through_ball() -> void:
	if not _within_kick_range():
		return
	var dir := facing
	var t: Player = world.through_ball_target(self)
	if t != null:
		var lead := t.global_position - global_position
		lead += Vector3(0.0, 0.0, attack_sign() * 3.0) # aim ahead of the runner
		lead.y = 0.0
		if lead.length() > 0.5:
			dir = lead.normalized()
	_launch(dir * pass_power * 1.7 * attr_pass)

## Sideways spin from pushing the stick across the kick direction (curved/finesse shots).
func _curl_spin(power: float) -> Vector3:
	var right := facing.cross(Vector3.UP)
	var lateral := right.dot(_move_dir)
	if absf(lateral) < 0.15:
		return Vector3.ZERO
	return Vector3.UP * (-lateral) * CURL * clampf(power, 0.3, 1.0)

## Slide/standing tackle: lunge, and ask the match to resolve it (win the ball, or concede a foul).
func _attempt_tackle() -> void:
	if _tackle_cd > 0.0:
		return
	_tackle_cd = 0.5
	_lunge = LUNGE_TIME
	_play_action(_anim_kick) # reuse the punch/kick clip as a lunge
	if world != null:
		world.resolve_tackle(self)

func _within_kick_range() -> bool:
	if ball == null:
		return false
	var d := ball.global_position - global_position
	d.y = 0.0
	return d.length() <= control_radius + 0.7

func _launch(impulse: Vector3, spin_axis := Vector3.ZERO) -> void:
	ball.kick(impulse, spin_axis)
	_kick_cooldown = KICK_COOLDOWN
	_play_action(_anim_kick)
	if world != null:
		world.note_touch(self)
	Sfx.play("kick")

func charge_ratio() -> float:
	return charge
