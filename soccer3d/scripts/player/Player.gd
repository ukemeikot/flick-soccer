extends CharacterBody3D
class_name Player
## A footballer. The same body is driven by human input (the active Home player) or by AI, and
## goalkeepers use a dedicated behavior. Handles movement, possession/dribble, pass (with teammate
## targeting), charged shot, headers, tackle lunge, sprint + stamina.

const HOME := 0
const AWAY := 1
const HALF_W := 12.5
const HALF_L := 20.0

@export var walk_speed := 6.5
@export var sprint_speed := 10.5
@export var control_radius := 1.5
@export var dribble_distance := 1.15
@export var pass_power := 13.0
@export var min_shot_power := 12.0
@export var max_shot_power := 26.0

const STAMINA_DRAIN := 0.28
const STAMINA_REGEN := 0.16
const CHARGE_RATE := 1.5
const KICK_COOLDOWN := 0.35
const LUNGE_TIME := 0.28
const HIGH_BALL_Y := 1.0

# Set by the match before add_child:
var ball: Ball
var world                      # GameMatch (untyped to avoid cyclic class refs)
var team := HOME
var is_gk := false
var home_pos := Vector3.ZERO
var kit_color := Color(0.2, 0.45, 0.95)
var skill := 0.7               # 0..1 AI quality (difficulty)

# Set each frame by the match:
var is_human := false
var role_chase := false        # this team's nearest outfielder to the ball

var facing := Vector3(0.0, 0.0, -1.0)
var stamina := 1.0
var charge := 0.0
var _kick_cooldown := 0.0
var _lunge := 0.0
var _controllable := false     # movement allowed (false during kickoff freeze)

func attack_sign() -> float:
	return 1.0 if team == HOME else -1.0 # HOME attacks +Z

func _ready() -> void:
	var mesh := MeshInstance3D.new()
	var capsule := CapsuleMesh.new()
	capsule.radius = 0.4
	capsule.height = 1.8
	mesh.mesh = capsule
	var mat := StandardMaterial3D.new()
	mat.albedo_color = kit_color
	mesh.material_override = mat
	add_child(mesh)

	var nose := MeshInstance3D.new()
	var nose_mesh := BoxMesh.new()
	nose_mesh.size = Vector3(0.3, 0.3, 0.5)
	nose.mesh = nose_mesh
	var nose_mat := StandardMaterial3D.new()
	nose_mat.albedo_color = kit_color.darkened(0.4)
	nose.material_override = nose_mat
	nose.position = Vector3(0.0, 0.3, -0.6)
	add_child(nose)

	var col := CollisionShape3D.new()
	var shape := CapsuleShape3D.new()
	shape.radius = 0.4
	shape.height = 1.8
	col.shape = shape
	add_child(col)

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

# --- Movement -------------------------------------------------------------------------------

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

# --- Human ----------------------------------------------------------------------------------

func _human_control(delta: float) -> void:
	var input := Input.get_vector("move_left", "move_right", "move_up", "move_down")
	var dir := Vector3(input.x, 0.0, input.y)
	_apply_movement(dir, Input.is_action_pressed("sprint"), delta)
	if Input.is_action_pressed("kick_shoot"):
		charge = minf(charge + CHARGE_RATE * delta, 1.0)
	if Input.is_action_just_released("kick_shoot"):
		_shoot(charge)
		charge = 0.0
	if Input.is_action_just_pressed("kick_pass"):
		_pass()
	if Input.is_action_just_pressed("tackle"):
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
		var in_range := to_goal.length() < lerpf(11.0, 17.0, skill)
		if in_range and absf(global_position.x) < 8.0:
			_shoot(0.6 + skill * 0.3)
		elif world.opponent_within(self, 2.6):
			_pass()
		return

	if role_chase:
		_apply_movement(to_ball.normalized(), to_ball.length() > 6.0, delta)
		return

	# Hold formation, shifted toward the ball.
	var target := _formation_target()
	var to_t := target - global_position
	to_t.y = 0.0
	var dir := to_t.normalized() if to_t.length() > 0.7 else Vector3.ZERO
	_apply_movement(dir, false, delta)

func _formation_target() -> Vector3:
	var t := home_pos
	t.x = clampf(home_pos.x + ball.global_position.x * 0.25, -HALF_W + 1.0, HALF_W - 1.0)
	t.z = clampf(home_pos.z + ball.global_position.z * 0.30, -HALF_L + 1.0, HALF_L - 1.0)
	return Vector3(t.x, 0.9, t.z)

# --- Goalkeeper -----------------------------------------------------------------------------

func _gk_control(delta: float) -> void:
	var own: Vector3 = world.own_goal(team)
	var line_z := own.z - attack_sign() * 1.2 # just in front of own goal
	var target := Vector3(clampf(ball.global_position.x, -3.0, 3.0), 0.9, line_z)
	var dist_to_goal := (ball.global_position - own).length()
	if dist_to_goal < 6.5:
		# Rush out a little to close the angle.
		target = Vector3(clampf(ball.global_position.x, -3.0, 3.0), 0.9, line_z - attack_sign() * 2.5)
	var to_t := target - global_position
	to_t.y = 0.0
	var dir := to_t.normalized() if to_t.length() > 0.3 else Vector3.ZERO
	_apply_movement(dir, dist_to_goal < 8.0, delta)
	if has_possession():
		_pass() # clear it

# --- Ball actions ---------------------------------------------------------------------------

func has_possession() -> bool:
	if ball == null or _kick_cooldown > 0.0:
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

func charge_ratio() -> float:
	return charge
