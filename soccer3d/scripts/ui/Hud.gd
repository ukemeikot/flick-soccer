extends Control
class_name Hud
## Match HUD: scoreboard + clock, stamina and shot-power meters, a transient GOAL! banner, and
## pause / full-time overlays. Reads the active player each frame; buttons call back into the match.

var world           # GameMatch (untyped to avoid cyclic refs)
var player: Player

var _score_label: Label
var _clock_label: Label
var _stamina_fill: ColorRect
var _power_bar: Control
var _power_fill: ColorRect
var _goal_banner: Label
var _goal_timer := 0.0
var _pause: Control
var _result: Control
var _result_label: Label
var _radar: Radar

func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	_fit()
	get_viewport().size_changed.connect(_fit)

	_score_label = _make_label("HOME  0 - 0  AWAY", 30)
	_score_label.set_anchors_preset(Control.PRESET_TOP_WIDE)
	_score_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_score_label.position = Vector2(0, 10)
	add_child(_score_label)

	_clock_label = _make_label("1st  02:00", 20)
	_clock_label.set_anchors_preset(Control.PRESET_TOP_WIDE)
	_clock_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_clock_label.position = Vector2(0, 48)
	add_child(_clock_label)

	# Stamina (top-left).
	var stam_bg := ColorRect.new()
	stam_bg.color = Color(0, 0, 0, 0.4)
	stam_bg.position = Vector2(16, 16)
	stam_bg.size = Vector2(200, 16)
	add_child(stam_bg)
	_stamina_fill = ColorRect.new()
	_stamina_fill.color = Color(0.3, 0.85, 0.4)
	_stamina_fill.position = Vector2(18, 18)
	_stamina_fill.size = Vector2(196, 12)
	add_child(_stamina_fill)

	# Shot-power meter (bottom-center, only while charging).
	_power_bar = Control.new()
	add_child(_power_bar)
	var pow_bg := ColorRect.new()
	pow_bg.color = Color(0, 0, 0, 0.4)
	pow_bg.size = Vector2(260, 16)
	_power_bar.add_child(pow_bg)
	_power_fill = ColorRect.new()
	_power_fill.color = Color(0.95, 0.75, 0.2)
	_power_fill.position = Vector2(2, 2)
	_power_fill.size = Vector2(0, 12)
	_power_bar.add_child(_power_fill)
	_power_bar.visible = false

	var hint := _make_label("Move WASD/Arrows · Sprint Shift · Pass J · Shoot(hold) K · Tackle L · Pause Esc", 12)
	hint.position = Vector2(16, 40)
	add_child(hint)

	# GOAL! banner.
	_goal_banner = _make_label("GOAL!", 72)
	_goal_banner.set_anchors_preset(Control.PRESET_FULL_RECT)
	_goal_banner.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_goal_banner.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	_goal_banner.visible = false
	add_child(_goal_banner)

	_pause = _make_overlay("Paused", [
		{"text": "Resume", "cb": func(): if world: world.resume()},
		{"text": "Quit to menu", "cb": func(): if world: world.quit_to_menu()},
	])
	add_child(_pause)

	_result = _make_overlay("", [
		{"text": "Rematch", "cb": func(): if world: world.rematch()},
		{"text": "Menu", "cb": func(): if world: world.quit_to_menu()},
	])
	_result_label = _result.get_meta("title") as Label
	add_child(_result)

	# Radar minimap (top-right).
	_radar = Radar.new()
	_radar.set_anchors_preset(Control.PRESET_TOP_RIGHT)
	_radar.offset_left = -216
	_radar.offset_right = -16
	_radar.offset_top = 78
	_radar.offset_bottom = 208
	_radar.world = world
	add_child(_radar)

func _fit() -> void:
	position = Vector2.ZERO
	size = get_viewport().get_visible_rect().size

func _process(_delta: float) -> void:
	if player != null:
		_stamina_fill.size.x = 196.0 * clampf(player.stamina, 0.0, 1.0)
		var vp := get_viewport_rect().size
		_power_bar.position = Vector2(vp.x / 2.0 - 130.0, vp.y - 48.0)
		var charging := player.charge_ratio() > 0.001
		_power_bar.visible = charging
		if charging:
			_power_fill.size.x = 256.0 * clampf(player.charge_ratio(), 0.0, 1.0)
	if _radar != null:
		_radar.active = player
		_radar.queue_redraw()
	if _goal_timer > 0.0:
		_goal_timer -= _delta
		if _goal_timer <= 0.0:
			_goal_banner.visible = false

func set_score(home: int, away: int) -> void:
	_score_label.text = "HOME  %d - %d  AWAY" % [home, away]

func set_clock(seconds: float, half: int) -> void:
	var s := int(ceil(seconds))
	var half_str := "1st" if half == 1 else "2nd"
	_clock_label.text = "%s  %02d:%02d" % [half_str, s / 60, s % 60]

func show_goal(team_name: String) -> void:
	_goal_banner.text = "%s GOAL!" % team_name
	_goal_banner.visible = true
	_goal_timer = 1.6

func show_pause(shown: bool) -> void:
	_pause.visible = shown

func show_result(text: String) -> void:
	_result_label.text = text
	_result.visible = true

func hide_overlays() -> void:
	_pause.visible = false
	_result.visible = false
	_goal_banner.visible = false

# --- builders -------------------------------------------------------------------------------

func _make_label(text: String, size: int) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", size)
	l.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return l

func _make_overlay(title: String, buttons: Array) -> Control:
	var root := Control.new()
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.visible = false

	var dim := ColorRect.new()
	dim.color = Color(0, 0, 0, 0.6)
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.add_child(dim)

	var center := CenterContainer.new()
	center.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.add_child(center)
	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 16)
	center.add_child(vb)

	var title_label := _make_label(title, 44)
	title_label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	vb.add_child(title_label)
	root.set_meta("title", title_label)

	for b in buttons:
		var btn := Button.new()
		btn.text = b["text"]
		btn.custom_minimum_size = Vector2(280, 52)
		btn.pressed.connect(b["cb"])
		vb.add_child(btn)
	return root
