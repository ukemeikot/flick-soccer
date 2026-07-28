extends Node3D
## P0 world: builds a 3D pitch (with contained walls + cosmetic goals), a rolling ball, a
## keyboard-movable debug player, and a broadcast camera that follows the ball. Built in code so the
## project loads reliably; proper scenes are extracted as the game grows. Esc returns to the menu.

const MENU_SCENE := "res://scenes/MainMenu.tscn"

const PITCH_LENGTH := 40.0 # along Z
const PITCH_WIDTH := 25.0  # along X

func _ready() -> void:
	_build_environment()
	_build_pitch()

	var ball := Ball.new()
	ball.position = Vector3(0.0, 0.3, 0.0)
	add_child(ball)

	var player := Player.new()
	player.position = Vector3(0.0, 0.9, 4.0)
	add_child(player)
	player.ball = ball

	var cam := BroadcastCamera.new()
	cam.fov = 60.0
	add_child(cam)
	cam.target = ball

	# HUD (stamina + shot-power meter + controls hint).
	var layer := CanvasLayer.new()
	add_child(layer)
	var hud := Hud.new()
	layer.add_child(hud)
	hud.player = player

func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel"):
		get_tree().change_scene_to_file(MENU_SCENE)

func _build_environment() -> void:
	var world_env := WorldEnvironment.new()
	var env := Environment.new()
	env.background_mode = Environment.BG_COLOR
	env.background_color = Color(0.53, 0.81, 0.92)
	env.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.ambient_light_color = Color(0.6, 0.6, 0.6)
	env.ambient_light_energy = 1.0
	world_env.environment = env
	add_child(world_env)

	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-55.0, -40.0, 0.0)
	sun.shadow_enabled = true
	add_child(sun)

func _build_pitch() -> void:
	# Ground: a thin green box with matching collision; top surface at y = 0.
	var ground := StaticBody3D.new()
	var ground_mesh := MeshInstance3D.new()
	var box := BoxMesh.new()
	box.size = Vector3(PITCH_WIDTH, 0.2, PITCH_LENGTH)
	ground_mesh.mesh = box
	ground_mesh.material_override = _color_material(Color(0.16, 0.55, 0.24))
	ground_mesh.position = Vector3(0.0, -0.1, 0.0)
	ground.add_child(ground_mesh)
	var ground_col := CollisionShape3D.new()
	var ground_shape := BoxShape3D.new()
	ground_shape.size = Vector3(PITCH_WIDTH, 0.2, PITCH_LENGTH)
	ground_col.shape = ground_shape
	ground_col.position = Vector3(0.0, -0.1, 0.0)
	ground.add_child(ground_col)
	add_child(ground)

	# Perimeter walls (collision only) keep the ball on the pitch.
	var hw := PITCH_WIDTH / 2.0
	var hl := PITCH_LENGTH / 2.0
	_add_wall(Vector3(0.0, 1.0, hl), Vector3(PITCH_WIDTH, 2.0, 0.5))
	_add_wall(Vector3(0.0, 1.0, -hl), Vector3(PITCH_WIDTH, 2.0, 0.5))
	_add_wall(Vector3(hw, 1.0, 0.0), Vector3(0.5, 2.0, PITCH_LENGTH))
	_add_wall(Vector3(-hw, 1.0, 0.0), Vector3(0.5, 2.0, PITCH_LENGTH))

	# Cosmetic goals at each end.
	_add_goal(Vector3(0.0, 0.0, hl))
	_add_goal(Vector3(0.0, 0.0, -hl))

func _add_wall(pos: Vector3, size: Vector3) -> void:
	var wall := StaticBody3D.new()
	var col := CollisionShape3D.new()
	var shape := BoxShape3D.new()
	shape.size = size
	col.shape = shape
	wall.add_child(col)
	wall.position = pos
	add_child(wall)

func _add_goal(base: Vector3) -> void:
	var mat := _color_material(Color(0.95, 0.95, 0.97))
	var mouth := 6.0
	var height := 2.2
	var thick := 0.25
	for xoff in [-mouth / 2.0, mouth / 2.0]:
		var post := MeshInstance3D.new()
		var pmesh := BoxMesh.new()
		pmesh.size = Vector3(thick, height, thick)
		post.mesh = pmesh
		post.material_override = mat
		post.position = base + Vector3(xoff, height / 2.0, 0.0)
		add_child(post)
	var bar := MeshInstance3D.new()
	var bmesh := BoxMesh.new()
	bmesh.size = Vector3(mouth + thick, thick, thick)
	bar.mesh = bmesh
	bar.material_override = mat
	bar.position = base + Vector3(0.0, height, 0.0)
	add_child(bar)

func _color_material(c: Color) -> StandardMaterial3D:
	var mat := StandardMaterial3D.new()
	mat.albedo_color = c
	return mat
