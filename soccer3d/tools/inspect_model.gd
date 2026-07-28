extends SceneTree
## Headless inspector: prints the imported player.glb node tree, mesh AABB (for scaling),
## skeleton bone count, and AnimationPlayer animation names. Run:
##   godot --headless --path soccer3d --script res://tools/inspect_model.gd

func _init() -> void:
	var packed := load("res://assets/players/player.glb") as PackedScene
	if packed == null:
		print("LOAD FAILED — is the glb imported?")
		quit()
		return
	var inst := packed.instantiate()
	print("=== player.glb tree ===")
	_walk(inst, 0)
	quit()

func _walk(n: Node, depth: int) -> void:
	var pad := ""
	for i in depth:
		pad += "  "
	var extra := ""
	if n is AnimationPlayer:
		extra = "  ANIMS=" + str((n as AnimationPlayer).get_animation_list())
	elif n is MeshInstance3D:
		extra = "  AABB=" + str((n as MeshInstance3D).get_aabb())
	elif n is Skeleton3D:
		extra = "  BONES=" + str((n as Skeleton3D).get_bone_count())
	print(pad, n.name, " [", n.get_class(), "]", extra)
	for c in n.get_children():
		_walk(c, depth + 1)
