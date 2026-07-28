extends Camera3D
class_name BroadcastCamera
## Broadcast camera: sits above/behind and follows the target (the ball), leading slightly in the
## direction the ball is moving so play stays ahead of centre.

@export var offset := Vector3(0.0, 12.5, 15.5)
@export var smooth := 5.0
@export var lookahead := 0.35

var target: Node3D

func _ready() -> void:
	if target != null:
		global_position = target.global_position + offset
		look_at(target.global_position, Vector3.UP)

func _process(delta: float) -> void:
	if target == null:
		return
	var lead := Vector3.ZERO
	if target is RigidBody3D:
		lead = (target as RigidBody3D).linear_velocity * lookahead
		lead.y = 0.0
		lead = lead.limit_length(6.0)
	var focus := target.global_position + lead
	var desired := focus + offset
	global_position = global_position.lerp(desired, clampf(smooth * delta, 0.0, 1.0))
	look_at(focus + Vector3(0.0, 0.0, -2.0), Vector3.UP)
