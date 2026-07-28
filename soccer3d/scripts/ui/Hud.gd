extends Control
class_name Hud
## Match HUD: a stamina bar, a shot-power meter (only while charging), and a controls hint.
## Reads live values off the player each frame.

var player: Player

var _stamina_fill: ColorRect
var _power_bar: Control
var _power_fill: ColorRect

func _ready() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_IGNORE

	# Stamina bar (top-left).
	var stam_bg := ColorRect.new()
	stam_bg.color = Color(0.0, 0.0, 0.0, 0.4)
	stam_bg.position = Vector2(16, 16)
	stam_bg.size = Vector2(220, 18)
	add_child(stam_bg)
	_stamina_fill = ColorRect.new()
	_stamina_fill.color = Color(0.3, 0.85, 0.4)
	_stamina_fill.position = Vector2(18, 18)
	_stamina_fill.size = Vector2(216, 14)
	add_child(_stamina_fill)
	var stam_label := Label.new()
	stam_label.text = "STAMINA"
	stam_label.position = Vector2(18, 36)
	stam_label.add_theme_font_size_override("font_size", 12)
	add_child(stam_label)

	# Shot-power meter (bottom-center), hidden unless charging.
	_power_bar = Control.new()
	_power_bar.position = Vector2(0, 0)
	add_child(_power_bar)
	var pow_bg := ColorRect.new()
	pow_bg.color = Color(0.0, 0.0, 0.0, 0.4)
	pow_bg.size = Vector2(260, 16)
	_power_bar.add_child(pow_bg)
	_power_fill = ColorRect.new()
	_power_fill.color = Color(0.95, 0.75, 0.2)
	_power_fill.position = Vector2(2, 2)
	_power_fill.size = Vector2(0, 12)
	_power_bar.add_child(_power_fill)
	_power_bar.visible = false

	var hint := Label.new()
	hint.text = "Move: WASD/Arrows   Sprint: Shift   Pass: J/Space   Shoot: hold K   Tackle: L   Menu: Esc"
	hint.add_theme_font_size_override("font_size", 13)
	hint.set_anchors_preset(Control.PRESET_TOP_WIDE)
	hint.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	hint.position = Vector2(0, 44)
	add_child(hint)

func _process(_delta: float) -> void:
	if player == null:
		return
	_stamina_fill.size.x = 216.0 * clampf(player.stamina, 0.0, 1.0)

	var vp := get_viewport_rect().size
	_power_bar.position = Vector2(vp.x / 2.0 - 130.0, vp.y - 48.0)
	var charging := player.charge_ratio() > 0.001
	_power_bar.visible = charging
	if charging:
		_power_fill.size.x = 256.0 * clampf(player.charge_ratio(), 0.0, 1.0)
