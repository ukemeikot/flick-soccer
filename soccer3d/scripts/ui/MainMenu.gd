extends Control
## App entry: pick which game to play. Both games live in this one Godot app (§ menu selection).
## "3D Football" is the new build; "Flick Soccer" is being ported from the Kotlin version next.

const MATCH_SCENE := "res://scenes/Match.tscn"
const FLICK_SCENE := "res://scenes/FlickPlaceholder.tscn"

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
	vb.add_theme_constant_override("separation", 18)
	center.add_child(vb)

	var title := Label.new()
	title.text = "SOCCER"
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.add_theme_font_size_override("font_size", 56)
	vb.add_child(title)

	var subtitle := Label.new()
	subtitle.text = "Choose a game"
	subtitle.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	vb.add_child(subtitle)

	vb.add_child(_menu_button("3D Football — Exhibition", _on_football))
	vb.add_child(_menu_button("Flick Soccer", _on_flick))
	vb.add_child(_menu_button("Quit", _on_quit))

func _menu_button(text: String, handler: Callable) -> Button:
	var b := Button.new()
	b.text = text
	b.custom_minimum_size = Vector2(340, 56)
	b.pressed.connect(handler)
	return b

func _on_football() -> void:
	get_tree().change_scene_to_file(MATCH_SCENE)

func _on_flick() -> void:
	get_tree().change_scene_to_file(FLICK_SCENE)

func _on_quit() -> void:
	get_tree().quit()
