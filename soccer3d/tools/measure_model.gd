extends SceneTree
## Measures player.glb's rest-pose height and min-Y after the skeleton has posed.
##   godot --headless --path soccer3d --script res://tools/measure_model.gd

var _inst: Node3D
var _frames := 0

func _init() -> void:
	var packed := load("res://assets/players/player.glb") as PackedScene
	_inst = packed.instantiate() as Node3D
	root.add_child(_inst)
	for n in _all(_inst):
		if n is Skeleton3D:
			(n as Skeleton3D).force_update_all_bone_transforms()

func _process(_delta: float) -> bool:
	_frames += 1
	if _frames < 4:
		return false
	var mn := Vector3(INF, INF, INF)
	var mx := -mn
	var inv := _inst.global_transform.affine_inverse()
	for n in _all(_inst):
		if n is MeshInstance3D:
			var mi := n as MeshInstance3D
			var a := mi.get_aabb()
			var t := inv * mi.global_transform
			for i in 8:
				var c := a.position + Vector3(a.size.x * (i & 1), a.size.y * ((i >> 1) & 1), a.size.z * ((i >> 2) & 1))
				var p := t * c
				mn.x = minf(mn.x, p.x); mn.y = minf(mn.y, p.y); mn.z = minf(mn.z, p.z)
				mx.x = maxf(mx.x, p.x); mx.y = maxf(mx.y, p.y); mx.z = maxf(mx.z, p.z)
	print("HEIGHT=", mx.y - mn.y, " MIN_Y=", mn.y, " MAX_Y=", mx.y, " WIDTH_X=", mx.x - mn.x)
	return true

func _all(n: Node) -> Array:
	var out: Array = [n]
	for c in n.get_children():
		out.append_array(_all(c))
	return out
