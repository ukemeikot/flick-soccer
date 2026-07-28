extends Control
class_name TouchControls
## On-screen controls for mobile: a left virtual joystick + right action buttons (Pass / Shoot /
## Sprint / Tackle). Shown on touchscreens or any mobile export; drives the Touch singleton.

func _ready() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	var show_touch := DisplayServer.is_touchscreen_available() or OS.has_feature("mobile")
	visible = show_touch
	if not show_touch:
		return

	var joy := TouchJoystick.new()
	_place_bl(joy, 190, 190, 40, 40)
	add_child(joy)

	add_child(_hold_button("SHOOT", 150, 150, 40, 40, func(d): Touch.set_shoot(d)))
	add_child(_tap_button("PASS", 120, 120, 210, 46, func(): Touch.press_pass()))
	add_child(_hold_button("SPRINT", 104, 104, 46, 210, func(d): Touch.sprint = d))
	add_child(_tap_button("TACKLE", 104, 104, 176, 210, func(): Touch.press_tackle()))

func _tap_button(text: String, w: int, h: int, mx: int, my: int, cb: Callable) -> Button:
	var b := _make_button(text, w, h, mx, my)
	b.pressed.connect(cb)
	return b

func _hold_button(text: String, w: int, h: int, mx: int, my: int, cb: Callable) -> Button:
	var b := _make_button(text, w, h, mx, my)
	b.button_down.connect(func(): cb.call(true))
	b.button_up.connect(func(): cb.call(false))
	return b

func _make_button(text: String, w: int, h: int, mx: int, my: int) -> Button:
	var b := Button.new()
	b.text = text
	_place_br(b, w, h, mx, my)
	b.add_theme_font_size_override("font_size", 18)
	# Semi-transparent round-ish style so buttons read clearly over the pitch.
	var sb := StyleBoxFlat.new()
	sb.bg_color = Color(0, 0, 0, 0.45)
	sb.set_corner_radius_all(int(min(w, h) / 2))
	sb.set_border_width_all(2)
	sb.border_color = Color(1, 1, 1, 0.5)
	b.add_theme_stylebox_override("normal", sb)
	var sb2 := sb.duplicate()
	sb2.bg_color = Color(0.2, 0.5, 0.9, 0.7)
	b.add_theme_stylebox_override("pressed", sb2)
	b.add_theme_stylebox_override("hover", sb)
	return b

func _place_bl(c: Control, w: int, h: int, mx: int, my: int) -> void:
	c.set_anchors_preset(Control.PRESET_BOTTOM_LEFT)
	c.offset_left = mx
	c.offset_right = mx + w
	c.offset_top = -(h + my)
	c.offset_bottom = -my

func _place_br(c: Control, w: int, h: int, mx: int, my: int) -> void:
	c.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
	c.offset_left = -(w + mx)
	c.offset_right = -mx
	c.offset_top = -(h + my)
	c.offset_bottom = -my
