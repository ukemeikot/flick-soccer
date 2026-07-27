extends Control
## Placeholder for the Flick Soccer game while it's ported from the Kotlin version into Godot.

const MENU_SCENE := "res://scenes/MainMenu.tscn"

func _ready() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)

	var bg := ColorRect.new()
	bg.color = Color(0.07, 0.12, 0.09)
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)

	var center := CenterContainer.new()
	center.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(center)

	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 16)
	center.add_child(vb)

	var label := Label.new()
	label.text = "Flick Soccer\nbeing ported to Godot — coming next"
	label.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	label.add_theme_font_size_override("font_size", 28)
	vb.add_child(label)

	var back := Button.new()
	back.text = "Back to menu"
	back.custom_minimum_size = Vector2(260, 52)
	back.pressed.connect(_on_back)
	vb.add_child(back)

func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel"):
		_on_back()

func _on_back() -> void:
	get_tree().change_scene_to_file(MENU_SCENE)
