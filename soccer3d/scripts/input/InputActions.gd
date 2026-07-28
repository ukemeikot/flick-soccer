extends Node
## Autoload that registers gameplay input actions in code (keyboard for now; gamepad/touch later).
## Movement: WASD + arrows. Sprint: Shift. Pass: J. Shoot: K.

func _ready() -> void:
	_bind("move_left", [KEY_A, KEY_LEFT])
	_bind("move_right", [KEY_D, KEY_RIGHT])
	_bind("move_up", [KEY_W, KEY_UP])
	_bind("move_down", [KEY_S, KEY_DOWN])
	_bind("sprint", [KEY_SHIFT])
	_bind("kick_pass", [KEY_J, KEY_SPACE])
	_bind("kick_shoot", [KEY_K])
	_bind("tackle", [KEY_L])
	_bind("switch_player", [KEY_Q, KEY_TAB])

func _bind(action: String, keys: Array) -> void:
	if not InputMap.has_action(action):
		InputMap.add_action(action)
	for k in keys:
		var e := InputEventKey.new()
		e.physical_keycode = k
		InputMap.action_add_event(action, e)
