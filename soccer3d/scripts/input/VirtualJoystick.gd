extends Control
class_name TouchJoystick
## Left-thumb virtual joystick. Writes a -1..1 vector into the Touch singleton.

@export var radius := 85.0
var _knob := Vector2.ZERO
var _active := false

func _ready() -> void:
	custom_minimum_size = Vector2(radius * 2.0, radius * 2.0)
	mouse_filter = Control.MOUSE_FILTER_STOP

func _draw() -> void:
	var c := size / 2.0
	draw_circle(c, radius, Color(1, 1, 1, 0.12))
	draw_circle(c, radius, Color(1, 1, 1, 0.25), false, 3.0)
	draw_circle(c + _knob, radius * 0.42, Color(1, 1, 1, 0.4))

func _gui_input(event: InputEvent) -> void:
	if event is InputEventScreenTouch or event is InputEventMouseButton:
		if event.pressed:
			_active = true
			_update(event.position)
		else:
			_active = false
			_knob = Vector2.ZERO
			Touch.move = Vector2.ZERO
			queue_redraw()
	elif _active and (event is InputEventScreenDrag or event is InputEventMouseMotion):
		_update(event.position)

func _update(pos: Vector2) -> void:
	var v := pos - size / 2.0
	if v.length() > radius:
		v = v.normalized() * radius
	_knob = v
	Touch.move = v / radius
	queue_redraw()
