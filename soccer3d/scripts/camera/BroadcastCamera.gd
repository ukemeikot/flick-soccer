extends Camera3D
class_name BroadcastCamera
## Broadcast-style camera: sits above/behind and follows the target (the ball) with smoothing.

@export var offset := Vector3(0.0, 16.0, 14.0)
@export var smooth := 4.0

var target: Node3D

func _ready() -> void:
	if target != null:
		global_position = target.global_position + offset
		look_at(target.global_position, Vector3.UP)

func _process(delta: float) -> void:
	if target == null:
		return
	var desired := target.global_position + offset
	global_position = global_position.lerp(desired, clampf(smooth * delta, 0.0, 1.0))
	look_at(target.global_position + Vector3(0.0, 0.0, -3.0), Vector3.UP)
