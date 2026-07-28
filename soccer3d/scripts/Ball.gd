extends RigidBody3D
class_name Ball
## The match ball. A physics sphere with a little starting roll so P0 shows motion. Kicking
## (impulses from players) arrives in P1.

const RADIUS := 0.22
const MAGNUS_K := 0.9    # how strongly spin curves the flight path
const SPIN_DECAY := 0.8  # spin bleeds off over ~1s

var spin := Vector3.ZERO # curl axis; combined with velocity via the Magnus effect

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

func _physics_process(delta: float) -> void:
	# Magnus effect: a spinning ball in flight curves sideways. F ∝ spin × velocity.
	if spin.length() > 0.05 and linear_velocity.length() > 0.5:
		apply_central_force(spin.cross(linear_velocity) * MAGNUS_K)
	spin = spin.lerp(Vector3.ZERO, clampf(SPIN_DECAY * delta, 0.0, 1.0))

## Kick the ball with an impulse and optional curl (spin about the given axis, usually UP).
func kick(impulse: Vector3, spin_axis := Vector3.ZERO) -> void:
	linear_velocity = Vector3.ZERO
	angular_velocity = Vector3.ZERO
	spin = spin_axis
	apply_central_impulse(impulse)
