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

# Set each frame by the match:
var is_human := false
var role_chase := false

var facing := Vector3(0.0, 0.0, -1.0)
var stamina := 1.0
var charge := 0.0
var _kick_cooldown := 0.0
var _lunge := 0.0
var _controllable := false
var _anim_phase := 0.0
var _leg_l: Node3D
var _leg_r: Node3D
var _arm_l: Node3D
var _arm_r: Node3D

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
	var jersey := kit_color
	var shorts := kit_color.darkened(0.5)

	_add_box(Vector3(0.48, 0.30, 0.27), Vector3(0, 0.92, 0), shorts)          # shorts/hips
	_add_box(Vector3(0.50, 0.60, 0.26), Vector3(0, 1.18, 0), jersey)          # torso
	_add_sphere(0.15, Vector3(0, 1.62, 0), SKIN)                              # head

	_leg_l = _limb(Vector3(-0.13, 0.84, 0), Vector3(0.16, 0.82, 0.18), SOCKS)
	_leg_r = _limb(Vector3(0.13, 0.84, 0), Vector3(0.16, 0.82, 0.18), SOCKS)
	_arm_l = _limb(Vector3(-0.34, 1.42, 0), Vector3(0.13, 0.55, 0.13), jersey)
	_arm_r = _limb(Vector3(0.34, 1.42, 0), Vector3(0.13, 0.55, 0.13), SKIN)

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
	var speed := sprint_speed if use_sprint else walk_speed
	if _lunge > 0.0:
		speed = sprint_speed * 1.5
	velocity.x = dir.x * speed
	velocity.z = dir.z * speed
	velocity.y = 0.0
	if _lunge > 0.0 and not moving:
		velocity.x = facing.x * sprint_speed * 1.5
		velocity.z = facing.z * sprint_speed * 1.5
	move_and_slide()
	if moving:
		facing = dir.normalized()
		look_at(global_position + facing, Vector3.UP)
	if use_sprint:
		stamina = maxf(stamina - STAMINA_DRAIN * delta, 0.0)
	else:
		stamina = minf(stamina + STAMINA_REGEN * delta, 1.0)
	_animate(moving, delta)

func _animate(moving: bool, delta: float) -> void:
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
	var do_tackle := Input.is_action_just_pressed("tackle")
	if Touch.consume_tackle():
		do_tackle = true
	if do_tackle:
		_lunge = LUNGE_TIME

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
		return

	var target := _formation_target()
	var to_t := target - global_position
	to_t.y = 0.0
	var dir := to_t.normalized() if to_t.length() > 0.7 else Vector3.ZERO
	_apply_movement(dir, false, delta)

func _formation_target() -> Vector3:
	return Vector3(
		clampf(home_pos.x + ball.global_position.x * 0.25, -HALF_W + 1.0, HALF_W - 1.0),
		0.0,
		clampf(home_pos.z + ball.global_position.z * 0.30, -HALF_L + 1.0, HALF_L - 1.0),
	)

func _gk_control(delta: float) -> void:
	var own: Vector3 = world.own_goal(team)
	# Stand just in FRONT of the own goal line (inside the pitch), not behind it.
	var line_z := own.z + attack_sign() * 1.2
	var target := Vector3(clampf(ball.global_position.x, -2.8, 2.8), 0.0, line_z)
	var dist_to_goal := (ball.global_position - own).length()
	if dist_to_goal < 6.5:
		target = Vector3(clampf(ball.global_position.x, -2.8, 2.8), 0.0, line_z + attack_sign() * 2.5)
	var to_t := target - global_position
	to_t.y = 0.0
	_apply_movement(to_t.normalized() if to_t.length() > 0.3 else Vector3.ZERO, dist_to_goal < 8.0, delta)
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
	var target := global_position + facing * dribble_distance
	var desired := target - ball.global_position
	desired.y = 0.0
	ball.linear_velocity.x = desired.x * 7.0
	ball.linear_velocity.z = desired.z * 7.0

func _pass() -> void:
	if not _within_kick_range():
		return
	var dir := facing
	var target: Player = world.best_pass_target(self)
	if target != null:
		var d := target.global_position - global_position
		d.y = 0.0
		if d.length() > 0.5:
			dir = d.normalized()
	var high := ball.global_position.y > HIGH_BALL_Y
	_launch(dir * pass_power + Vector3.UP * (2.5 if high else 1.5))

func _shoot(c: float) -> void:
	if not _within_kick_range():
		return
	var high := ball.global_position.y > HIGH_BALL_Y
	var power := lerpf(min_shot_power, max_shot_power, clampf(c, 0.0, 1.0))
	if high:
		_launch(facing * (power * 0.8) + Vector3.UP * (5.0 + c * 3.0))
	else:
		_launch(facing * power + Vector3.UP * (2.0 + c * 2.5))

func _within_kick_range() -> bool:
	if ball == null:
		return false
	var d := ball.global_position - global_position
	d.y = 0.0
	return d.length() <= control_radius + 0.7

func _launch(impulse: Vector3) -> void:
	ball.linear_velocity = Vector3.ZERO
	ball.apply_central_impulse(impulse)
	_kick_cooldown = KICK_COOLDOWN
	Sfx.play("kick")

func charge_ratio() -> float:
	return charge
