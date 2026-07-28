extends Control
## Main menu + Exhibition setup. Picks which game to play, and configures the exhibition match
## (difficulty, half length, kit colors) before kickoff. Both games live in this one Godot app.

const MATCH_SCENE := "res://scenes/Match.tscn"
const FLICK_SCENE := "res://scenes/FlickMatch.tscn"
const HALF_LENGTHS := [60.0, 120.0, 180.0]

var _main: Control
var _setup: Control
var _diff: OptionButton
var _half: OptionButton
var _home_kit: OptionButton
var _away_kit: OptionButton

func _ready() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	var bg := ColorRect.new()
	bg.color = Color(0.07, 0.12, 0.09)
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)

	_main = _build_main()
	add_child(_main)
	_setup = _build_setup()
	_setup.visible = false
	add_child(_setup)

func _build_main() -> Control:
	var panel := _centered_vbox()
	var vb: VBoxContainer = panel.get_meta("vb")

	vb.add_child(_title("SOCCER", 56))
	vb.add_child(_title("Choose a game", 18))

	var last: Dictionary = MatchConfig.last_result()
	if not last.is_empty():
		vb.add_child(_title("Last: HOME %d - %d AWAY" % [int(last.get("home", 0)), int(last.get("away", 0))], 16))

	vb.add_child(_button("3D Football — Exhibition", func(): _show_setup()))
	vb.add_child(_button("Flick Soccer", func(): get_tree().change_scene_to_file(FLICK_SCENE)))
	vb.add_child(_button("Quit", func(): get_tree().quit()))
	return panel

func _build_setup() -> Control:
	var panel := _centered_vbox()
	var vb: VBoxContainer = panel.get_meta("vb")

	vb.add_child(_title("Exhibition", 40))

	_diff = _option(["Casual", "Normal", "Pro"], 1)
	vb.add_child(_row("Difficulty", _diff))
	_half = _option(["1 min", "2 min", "3 min"], 1)
	vb.add_child(_row("Half length", _half))
	_home_kit = _option(["Blue", "Red", "Gold", "Teal"], 0)
	vb.add_child(_row("Home kit", _home_kit))
	_away_kit = _option(["Blue", "Red", "Gold", "Teal"], 1)
	vb.add_child(_row("Away kit", _away_kit))

	vb.add_child(_button("Kick Off", func(): _start_match()))
	vb.add_child(_button("Back", func(): _show_main()))
	return panel

func _start_match() -> void:
	MatchConfig.difficulty = _diff.selected
	MatchConfig.half_seconds = HALF_LENGTHS[_half.selected]
	MatchConfig.home_kit = _home_kit.selected
	MatchConfig.away_kit = _away_kit.selected
	get_tree().change_scene_to_file(MATCH_SCENE)

func _show_setup() -> void:
	_main.visible = false
	_setup.visible = true

func _show_main() -> void:
	_setup.visible = false
	_main.visible = true

# --- builders -------------------------------------------------------------------------------

func _centered_vbox() -> Control:
	var root := Control.new()
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	var center := CenterContainer.new()
	center.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.add_child(center)
	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 14)
	center.add_child(vb)
	root.set_meta("vb", vb)
	return root

func _title(text: String, size: int) -> Label:
	var l := Label.new()
	l.text = text
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.add_theme_font_size_override("font_size", size)
	return l

func _button(text: String, cb: Callable) -> Button:
	var b := Button.new()
	b.text = text
	b.custom_minimum_size = Vector2(340, 52)
	b.pressed.connect(cb)
	return b

func _option(items: Array, selected: int) -> OptionButton:
	var o := OptionButton.new()
	for it in items:
		o.add_item(str(it))
	o.selected = clampi(selected, 0, items.size() - 1)
	o.custom_minimum_size = Vector2(160, 40)
	return o

func _row(label: String, control: Control) -> HBoxContainer:
	var h := HBoxContainer.new()
	h.add_theme_constant_override("separation", 12)
	var l := Label.new()
	l.text = label
	l.custom_minimum_size = Vector2(150, 40)
	l.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	h.add_child(l)
	h.add_child(control)
	return h
