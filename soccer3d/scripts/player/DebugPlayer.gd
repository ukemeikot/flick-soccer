extends CharacterBody3D
## P0 debug player: a capsule you drive with the arrow keys to prove movement + the pitch work.
## Real controls, dribbling and kicking arrive in P1.

const SPEED := 8.0

func _ready() -> void:
	var mesh := MeshInstance3D.new()
	var capsule := CapsuleMesh.new()
	capsule.radius = 0.4
	capsule.height = 1.8
	mesh.mesh = capsule
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.20, 0.45, 0.95)
	mesh.material_override = mat
	add_child(mesh)

	var col := CollisionShape3D.new()
	var shape := CapsuleShape3D.new()
	shape.radius = 0.4
	shape.height = 1.8
	col.shape = shape
	add_child(col)

func _physics_process(_delta: float) -> void:
	# Built-in ui_* actions (arrow keys) — no InputMap setup needed for P0.
	var input := Input.get_vector("ui_left", "ui_right", "ui_up", "ui_down")
	# Screen "up" drives into the pitch (-Z).
	velocity.x = input.x * SPEED
	velocity.z = input.y * SPEED
	velocity.y = 0.0
	move_and_slide()
