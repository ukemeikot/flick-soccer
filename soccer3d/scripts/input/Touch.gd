extends Node
## Shared touch-control state. The on-screen joystick/buttons write here; the active Player reads it
## and merges it with keyboard/gamepad input, so mobile and desktop share one control path.

var move := Vector2.ZERO      # -1..1 from the virtual joystick
var sprint := false
var shoot_held := false

var _pass := false
var _tackle := false
var _shoot_released := false

func press_pass() -> void:
	_pass = true

func press_tackle() -> void:
	_tackle = true

func set_shoot(down: bool) -> void:
	if shoot_held and not down:
		_shoot_released = true
	shoot_held = down

func consume_pass() -> bool:
	var v := _pass
	_pass = false
	return v

func consume_tackle() -> bool:
	var v := _tackle
	_tackle = false
	return v

func consume_shoot_released() -> bool:
	var v := _shoot_released
	_shoot_released = false
	return v

func reset() -> void:
	move = Vector2.ZERO
	sprint = false
	shoot_held = false
	_pass = false
	_tackle = false
	_shoot_released = false
