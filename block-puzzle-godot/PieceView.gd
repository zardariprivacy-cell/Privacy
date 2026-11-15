extends Control

const COLORS: Array[Color] = [
	Color("#ff5a6e"), Color("#ff9f43"), Color("#ffc93c"), Color("#2ed573"),
	Color("#1ebcce"), Color("#5a7bff"), Color("#c05aff")
]

var piece = null              # Dictionary {cells, rows, cols, color}
var cell_size := 18.0

func set_piece(p, cs: float) -> void:
	piece = p
	cell_size = cs
	if p == null:
		custom_minimum_size = Vector2.ZERO
	else:
		custom_minimum_size = Vector2(p["cols"] * cs, p["rows"] * cs)
	queue_redraw()

func _draw() -> void:
	if piece == null:
		return
	for cell in piece["cells"]:
		var rect := Rect2(cell[1] * cell_size + 1.2, cell[0] * cell_size + 1.2, cell_size - 2.4, cell_size - 2.4)
		draw_rect(rect, COLORS[int(piece["color"])], true)
		draw_rect(Rect2(rect.position, Vector2(rect.size.x, rect.size.y * 0.28)), Color(1, 1, 1, 0.20), true)
