extends RigidBody3D
class_name Ball
## The match ball. A physics sphere with a little starting roll so P0 shows motion. Kicking
## (impulses from players) arrives in P1.

const RADIUS := 0.3

func _ready() -> void:
	var mesh := MeshInstance3D.new()
	var sphere := SphereMesh.new()
	sphere.radius = RADIUS
	sphere.height = RADIUS * 2.0
	mesh.mesh = sphere
	var mat := StandardMaterial3D.new()
	mat.albedo_color = Color(0.96, 0.96, 0.96)
	mesh.material_override = mat
	add_child(mesh)

	var col := CollisionShape3D.new()
	var shape := SphereShape3D.new()
	shape.radius = RADIUS
	col.shape = shape
	add_child(col)

	var pm := PhysicsMaterial.new()
	pm.bounce = 0.5
	pm.friction = 0.6
	physics_material_override = pm
	# Roll resistance so the ball settles instead of gliding forever.
	linear_damp = 0.6
	angular_damp = 1.0
	can_sleep = false # never fall asleep mid-air (avoids getting "stuck" in flight)
