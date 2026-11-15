extends Control

const N := 10
const COLORS: Array[Color] = [
	Color("#ff5a6e"), Color("#ff9f43"), Color("#ffc93c"), Color("#2ed573"),
	Color("#1ebcce"), Color("#5a7bff"), Color("#c05aff")
]
const EMPTY := Color("#232b47")

var grid: Array = []          # 10x10 of int (-1 empty, else color idx)
var ghost_cells: Array = []   # Array of Vector2i currently hovered
var ghost_ok := false
var flash_cells: Dictionary = {}  # idx -> true, drawn bright briefly after a clear

func _ready() -> void:
	mouse_filter = MOUSE_FILTER_IGNORE

func cell_size() -> float:
	return size.x / float(N)

func _draw() -> void:
	if grid.size() != N:
		return
	var cs := cell_size()
	for r in range(N):
		for c in range(N):
			var rect := Rect2(c * cs + 1.2, r * cs + 1.2, cs - 2.4, cs - 2.4)
			var v: int = grid[r][c]
			if v >= 0:
				draw_rect(rect, COLORS[v], true)
				# top highlight for a blocky 3D feel
				draw_rect(Rect2(rect.position, Vector2(rect.size.x, rect.size.y * 0.28)), Color(1, 1, 1, 0.20), true)
			else:
				draw_rect(rect, EMPTY, true)
			if flash_cells.has(r * N + c):
				draw_rect(rect, Color(1, 1, 1, 0.55), true)
	for cell in ghost_cells:
		var rect := Rect2(cell.x * cs + 1.2, cell.y * cs + 1.2, cs - 2.4, cs - 2.4)
		var col := Color(0.49, 0.91, 0.53, 0.85) if ghost_ok else Color(1, 0.35, 0.43, 0.85)
		draw_rect(rect, col, false, 3.0)
