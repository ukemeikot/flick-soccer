extends Control
class_name Radar
## Minimap: dots for every player (team-colored) + the ball, with a ring on the player you control.

const HALF_W := 12.5
const HALF_L := 20.0

var world
var active: Player

func _ready() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE

func _draw() -> void:
	var w := size.x
	var h := size.y
	var pad := 8.0
	draw_rect(Rect2(Vector2.ZERO, size), Color(0.05, 0.15, 0.07, 0.6))
	draw_rect(Rect2(Vector2.ZERO, size), Color(1, 1, 1, 0.3), false, 2.0)
	if world == null:
		return
	for p in world.players:
		var col := Color(0.3, 0.55, 1.0) if p.team == Player.HOME else Color(1.0, 0.4, 0.4)
		var pos := _map(p.global_position, w, h, pad)
		draw_circle(pos, 4.0, col)
		if p == active:
			draw_circle(pos, 7.5, Color(1.0, 0.9, 0.15), false, 2.5)
	if world.ball != null:
		draw_circle(_map(world.ball.global_position, w, h, pad), 3.0, Color.WHITE)

func _map(wp: Vector3, w: float, h: float, pad: float) -> Vector2:
	var x := pad + (wp.x + HALF_W) / (2.0 * HALF_W) * (w - 2.0 * pad)
	var y := pad + (wp.z + HALF_L) / (2.0 * HALF_L) * (h - 2.0 * pad)
	return Vector2(x, y)
