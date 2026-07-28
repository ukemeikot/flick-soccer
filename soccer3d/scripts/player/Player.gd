extends CharacterBody3D
class_name Player
## The controllable player (P1/P2): move + sprint (stamina), take possession & dribble, tap-pass,
## charged shot (hold Shoot to build power, release), headers on high balls, and a tackle lunge.
## Recipient-targeted passing arrives with teammates in P3 — for now a pass goes where you face.

@export var kit_color := Color(0.20, 0.45, 0.95)
@export var walk_speed := 6.5
@export var sprint_speed := 10.5
@export var control_radius := 1.5
@export var dribble_distance := 1.15
@export var pass_power := 12.0
@export var min_shot_power := 11.0
@export var max_shot_power := 26.0

const STAMINA_DRAIN := 0.28   # per second while sprinting
const STAMINA_REGEN := 0.16   # per second otherwise
const CHARGE_RATE := 1.5      # shot-charge per second (0..1)
const KICK_COOLDOWN := 0.35
const LUNGE_TIME := 0.28
const HIGH_BALL_Y := 1.0      # ball above this is "in the air" → header

var ball: RigidBody3D
var facing := Vector3(0.0, 0.0, -1.0)
var stamina := 1.0            # 0..1 (read by the HUD)
var charge := 0.0             # 0..1 while holding Shoot (read by the HUD)

var _kick_cooldown := 0.0
var _lunge := 0.0

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

	# Facing "nose" so you can read which way the player points (local -Z is forward).
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

func _physics_process(delta: float) -> void:
	_kick_cooldown = maxf(_kick_cooldown - delta, 0.0)
	_lunge = maxf(_lunge - delta, 0.0)

	var input := Input.get_vector("move_left", "move_right", "move_up", "move_down")
	var move := Vector3(input.x, 0.0, input.y)
	var moving := move.length() > 0.1

	var wants_sprint := Input.is_action_pressed("sprint") and stamina > 0.05 and moving
	var speed := sprint_speed if wants_sprint else walk_speed
	if _lunge > 0.0:
		speed = sprint_speed * 1.5

	velocity.x = move.x * speed
	velocity.z = move.z * speed
	velocity.y = 0.0
	if _lunge > 0.0 and not moving:
		velocity.x = facing.x * sprint_speed * 1.5
		velocity.z = facing.z * sprint_speed * 1.5
	move_and_slide()

	if moving:
		facing = move.normalized()
		look_at(global_position + facing, Vector3.UP)

	# Stamina.
	if wants_sprint:
		stamina = maxf(stamina - STAMINA_DRAIN * delta, 0.0)
	else:
		stamina = minf(stamina + STAMINA_REGEN * delta, 1.0)

	# Charged shot: hold to build power, release to fire.
	if Input.is_action_pressed("kick_shoot"):
		charge = minf(charge + CHARGE_RATE * delta, 1.0)
	if Input.is_action_just_released("kick_shoot"):
		_shoot(charge)
		charge = 0.0
	if Input.is_action_just_pressed("kick_pass"):
		_pass()
	if Input.is_action_just_pressed("tackle"):
		_lunge = LUNGE_TIME

	_dribble()

func has_possession() -> bool:
	if ball == null or _kick_cooldown > 0.0:
		return false
	var d := ball.global_position - global_position
	d.y = 0.0
	return d.length() <= control_radius

func _dribble() -> void:
	if not has_possession():
		return
	# Keep the ball a step ahead in the facing direction.
	var target := global_position + facing * dribble_distance
	var desired := target - ball.global_position
	desired.y = 0.0
	ball.linear_velocity.x = desired.x * 7.0
	ball.linear_velocity.z = desired.z * 7.0

func _pass() -> void:
	if not _within_kick_range():
		return
	var high := ball.global_position.y > HIGH_BALL_Y
	_launch(facing * pass_power + Vector3.UP * (2.5 if high else 1.5))

func _shoot(c: float) -> void:
	if not _within_kick_range():
		return
	var high := ball.global_position.y > HIGH_BALL_Y
	var power := lerpf(min_shot_power, max_shot_power, c)
	# A header (high ball) trades some power for extra loft.
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
