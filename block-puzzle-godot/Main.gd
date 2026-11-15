extends Control

# ================= CONFIG =================
const N := 10
const COIN_CONTINUE := 100
const FREE_COINS := 50
const LEVEL_EVERY := 300
const BoardView := preload("res://BoardView.gd")
const PieceView := preload("res://PieceView.gd")

const SHAPE_STRINGS: Array = [
	["X"], ["XX"], ["X","X"], ["XXX"], ["X","X","X"], ["XX","XX"],
	["XX","X."], ["XX",".X"], ["X.","XX"], [".X","XX"],
	["XXXX"], ["X","X","X","X"],
	["XXX",".X."], [".X.","XXX"], ["X.","XX","X."], [".X","XX",".X"],
	["XXX","X.."], ["XXX","..X"], ["X..","XXX"], ["..X","XXX"],
	[".XX","XX."], ["XX.",".XX"], ["X.","XX",".X"], [".X","XX","X."],
	["XXXXX"], ["X","X","X","X","X"],
	["XX","XX","XX"], ["XXX","XXX"],
	["X..","X..","XXX"], ["..X","..X","XXX"], ["XXX","X..","X.."], ["XXX","..X","..X"],
	[".X.","XXX",".X."],
	["XXX","XXX","XXX"], ["XX.",".XX",".XX"], [".XX",".XX","XX."], ["X.X","XXX"], ["XXX","X.X"]
]
const SHAPE_TIERS: Array = [
	0,0,0,0,0,0, 0,0,0,0,
	1,1, 1,1,1,1, 1,1,1,1, 1,1,1,1,
	2,2, 2,2, 2,2,2,2, 2,
	3,3,3, 3,3
]

# ================= STATE =================
var pieces: Array = []
var grid: Array = []
var tray: Array = [null, null, null]
var score := 0
var streak := 0
var coins := 100
var best := 0
var level := 1
var game_active := false
var continued := 0
var player_name := ""
var device_id := ""
var sound_on := true
var last_free := 0

# UI refs
var board_view: Control
var tray_views: Array = []
var lbl_score: Label
var lbl_best: Label
var lbl_coins: Label
var lbl_level: Label
var lbl_next: Label
var lvl_bar: ProgressBar
var lbl_float: Label
var ov_over: Control
var lbl_final: Label
var lbl_over_msg: Label
var btn_coin_cont: Button
var ov_ad: Control
var lbl_ad_count: Label
var ov_how: Control
var btn_sound: Button

var drag = null  # {piece, slot, view, grab_r, grab_c}
var ad_busy := false
var players: Array = []
var player_idx := 0
var tones := {}

# ================= HELPERS =================
func style_box(bg: Color, radius: int = 12, border: Color = Color(0,0,0,0), bw: int = 0) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = bg
	s.corner_radius_top_left = radius
	s.corner_radius_top_right = radius
	s.corner_radius_bottom_left = radius
	s.corner_radius_bottom_right = radius
	if bw > 0:
		s.border_color = border
		s.set_border_width_all(bw)
	s.content_margin_left = 10
	s.content_margin_right = 10
	s.content_margin_top = 8
	s.content_margin_bottom = 8
	return s

func make_label(text: String, size: int, color: Color = Color("#eef1ff"), bold_center: bool = false) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	if bold_center:
		l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	return l

func make_button(text: String, bg: Color, fg: Color = Color("#0d1020")) -> Button:
	var b := Button.new()
	b.text = text
	b.add_theme_font_size_override("font_size", 15)
	b.add_theme_color_override("font_color", fg)
	b.add_theme_stylebox_override("normal", style_box(bg, 12))
	b.add_theme_stylebox_override("hover", style_box(bg.lightened(0.08), 12))
	b.add_theme_stylebox_override("pressed", style_box(bg.darkened(0.12), 12))
	return b

# ================= AUDIO =================
func make_tone(freq: float, dur: float, vol: float = 0.5, slide: float = 0.0) -> AudioStreamWAV:
	var rate := 22050
	var n := int(rate * dur)
	var data := PackedByteArray()
	data.resize(n)
	for i in range(n):
		var t := float(i) / rate
		var f := freq + slide * t
		var env := 1.0 - float(i) / n
		data[i] = int(128.0 + sin(TAU * f * t) * env * vol * 105.0)
	var w := AudioStreamWAV.new()
	w.format = AudioStreamWAV.FORMAT_8_BITS
	w.mix_rate = rate
	w.data = data
	return w

func play_tone(key: String) -> void:
	if not sound_on or not tones.has(key):
		return
	var p: AudioStreamPlayer = players[player_idx]
	player_idx = (player_idx + 1) % players.size()
	p.stream = tones[key]
	p.play()

func sfx_clear(n: int) -> void:
	play_tone("clear1")
	await get_tree().create_timer(0.06).timeout
	play_tone("clear2")
	if n >= 2:
		await get_tree().create_timer(0.06).timeout
		play_tone("clear3")

func sfx_coin() -> void:
	play_tone("coin1")
	await get_tree().create_timer(0.07).timeout
	play_tone("coin2")

func sfx_over() -> void:
	for k in ["over1", "over2", "over3", "over4"]:
		play_tone(k)
		await get_tree().create_timer(0.13).timeout

func sfx_level() -> void:
	for k in ["lvl1", "lvl2", "lvl3", "lvl4"]:
		play_tone(k)
		await get_tree().create_timer(0.06).timeout

# ================= PERSISTENCE =================
func save_data() -> void:
	var cfg := ConfigFile.new()
	cfg.set_value("u", "name", player_name)
	cfg.set_value("u", "did", device_id)
	cfg.set_value("u", "coins", coins)
	cfg.set_value("u", "best", best)
	cfg.set_value("u", "sound", sound_on)
	cfg.set_value("u", "last_free", last_free)
	cfg.save("user://bpm.cfg")

func load_data() -> void:
	var cfg := ConfigFile.new()
	if cfg.load("user://bpm.cfg") == OK:
		player_name = str(cfg.get_value("u", "name", ""))
		device_id = str(cfg.get_value("u", "did", ""))
		coins = int(cfg.get_value("u", "coins", 100))
		best = int(cfg.get_value("u", "best", 0))
		sound_on = bool(cfg.get_value("u", "sound", true))
		last_free = int(cfg.get_value("u", "last_free", 0))
	if device_id == "":
		device_id = "d" + str(Time.get_unix_time_from_system()) + str(randi() % 100000)
		save_data()

# ================= UI BUILD =================
func _ready() -> void:
	randomize()
	load_data()
	# build piece library
	for i in range(SHAPE_STRINGS.size()):
		var rows_str: Array = SHAPE_STRINGS[i]
		var cells: Array = []
		for r in range(rows_str.size()):
			var row: String = rows_str[r]
			for c in range(row.length()):
				if row[c] == "X":
					cells.append([r, c])
		pieces.append({
			"cells": cells, "rows": rows_str.size(), "cols": String(rows_str[0]).length(),
			"tier": int(SHAPE_TIERS[i]), "color": i % 7, "size": cells.size()
		})
	# audio
	tones = {
		"place": make_tone(330, 0.09, 0.55), "clear1": make_tone(523, 0.13, 0.5),
		"clear2": make_tone(659, 0.13, 0.5), "clear3": make_tone(880, 0.16, 0.5),
		"coin1": make_tone(988, 0.08, 0.4), "coin2": make_tone(1319, 0.14, 0.4),
		"over1": make_tone(392, 0.18, 0.35), "over2": make_tone(330, 0.18, 0.35),
		"over3": make_tone(262, 0.18, 0.35), "over4": make_tone(196, 0.24, 0.35),
		"lvl1": make_tone(440, 0.12, 0.5), "lvl2": make_tone(554, 0.12, 0.5),
		"lvl3": make_tone(659, 0.12, 0.5), "lvl4": make_tone(880, 0.16, 0.5)
	}
	for i in range(6):
		var p := AudioStreamPlayer.new()
		add_child(p)
		players.append(p)
	build_ui()
	new_game()
	# layout sizes settle after the first frames - redraw once they are real
	await get_tree().process_frame
	await get_tree().process_frame
	refresh()

func build_ui() -> void:
	var bg := ColorRect.new()
	bg.color = Color("#0e1220")
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(bg)

	var margin := MarginContainer.new()
	margin.set_anchors_preset(Control.PRESET_FULL_RECT)
	margin.add_theme_constant_override("margin_left", 12)
	margin.add_theme_constant_override("margin_right", 12)
	margin.add_theme_constant_override("margin_top", 10)
	margin.add_theme_constant_override("margin_bottom", 12)
	add_child(margin)

	var vbox := VBoxContainer.new()
	vbox.add_theme_constant_override("separation", 10)
	margin.add_child(vbox)

	# header
	var head := HBoxContainer.new()
	head.add_theme_constant_override("separation", 8)
	vbox.add_child(head)
	var title := make_label("Block Puzzle Master", 21)
	title.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	head.add_child(title)
	lbl_coins = make_label("Coins: 0", 15, Color("#ffc93c"))
	head.add_child(lbl_coins)
	var btn_free := make_button("+FREE", Color("#ffc93c"))
	btn_free.pressed.connect(_on_free_coins)
	head.add_child(btn_free)
	btn_sound = make_button("♪ ON", Color("#1f2742"), Color("#eef1ff"))
	btn_sound.pressed.connect(_on_sound_toggle)
	head.add_child(btn_sound)

	# HUD
	var hud := HBoxContainer.new()
	hud.add_theme_constant_override("separation", 8)
	vbox.add_child(hud)
	for stat_name in ["Score", "Best", "Level"]:
		var panel := PanelContainer.new()
		panel.add_theme_stylebox_override("panel", style_box(Color("#171d31"), 14, Color("#26304f"), 1))
		panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		var vb := VBoxContainer.new()
		var lab := make_label(stat_name.to_upper(), 10, Color("#9aa5c7"), true)
		vb.add_child(lab)
		var val := make_label("0", 20, Color.WHITE, true)
		vb.add_child(val)
		panel.add_child(vb)
		hud.add_child(panel)
		if stat_name == "Score":
			lbl_score = val
		elif stat_name == "Best":
			lbl_best = val
		else:
			lbl_level = val
			lvl_bar = ProgressBar.new()
			lvl_bar.min_value = 0
			lvl_bar.max_value = LEVEL_EVERY
			lvl_bar.value = 0
			lvl_bar.custom_minimum_size = Vector2(0, 8)
			lvl_bar.show_percentage = false
			vb.add_child(lvl_bar)
			lbl_next = make_label("Next: 300", 10, Color("#9aa5c7"), true)
			vb.add_child(lbl_next)

	# board
	var aspect := AspectRatioContainer.new()
	aspect.ratio = 1.0
	aspect.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	vbox.add_child(aspect)
	var board_panel := PanelContainer.new()
	board_panel.add_theme_stylebox_override("panel", style_box(Color("#171d31"), 16, Color("#26304f"), 1))
	aspect.add_child(board_panel)
	board_view = BoardView.new()
	board_view.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	board_view.size_flags_vertical = Control.SIZE_EXPAND_FILL
	board_panel.add_child(board_view)

	# tray
	var tray_box := HBoxContainer.new()
	tray_box.add_theme_constant_override("separation", 8)
	vbox.add_child(tray_box)
	for i in range(3):
		var slot := PanelContainer.new()
		slot.add_theme_stylebox_override("panel", style_box(Color("#171d31"), 14, Color("#2e3a5f"), 1))
		slot.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		slot.custom_minimum_size = Vector2(0, 108)
		var center := CenterContainer.new()
		slot.add_child(center)
		var pv = PieceView.new()
		center.add_child(pv)
		tray_views.append(pv)
		tray_box.add_child(slot)

	# buttons
	var btns := HBoxContainer.new()
	btns.add_theme_constant_override("separation", 8)
	vbox.add_child(btns)
	var b_how := make_button("How to Play", Color("#1f2742"), Color("#eef1ff"))
	b_how.pressed.connect(func() -> void: ov_how.visible = true)
	btns.add_child(b_how)
	var b_new := make_button("New Game", Color("#22c7a9"))
	b_new.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	b_new.pressed.connect(func() -> void: ov_over.visible = false; new_game())
	btns.add_child(b_new)

	var foot := make_label("Drag blocks onto the grid. Fill a full row or column to clear it.\nClear lines to earn coins - spend coins or watch an ad to continue after game over.", 11, Color("#9aa5c7"), true)
	foot.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	vbox.add_child(foot)

	# floating score label
	lbl_float = make_label("", 22, Color("#7ee787"), true)
	lbl_float.visible = false
	lbl_float.z_index = 50
	add_child(lbl_float)

	build_overlays()
	_update_sound_btn()

func make_overlay() -> Dictionary:
	var root := ColorRect.new()
	root.color = Color(0.02, 0.03, 0.06, 0.8)
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.visible = false
	root.z_index = 100
	add_child(root)
	var center := CenterContainer.new()
	center.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.add_child(center)
	var card := PanelContainer.new()
	card.add_theme_stylebox_override("panel", style_box(Color("#171d31"), 20, Color("#2e3a5f"), 1))
	card.custom_minimum_size = Vector2(330, 0)
	center.add_child(card)
	var vb := VBoxContainer.new()
	vb.add_theme_constant_override("separation", 10)
	card.add_child(vb)
	return {"root": root, "box": vb}

func build_overlays() -> void:
	# ---- game over ----
	var o3 := make_overlay()
	ov_over = o3["root"]
	var b3: VBoxContainer = o3["box"]
	b3.add_child(make_label("Game Over!", 24, Color.WHITE, true))
	b3.add_child(make_label("Koi block fit nahi ho raha. Tumhara score:", 13, Color("#9aa5c7"), true))
	lbl_final = make_label("0", 38, Color("#ffc93c"), true)
	b3.add_child(lbl_final)
	lbl_over_msg = make_label("", 13, Color("#9aa5c7"), true)
	lbl_over_msg.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	b3.add_child(lbl_over_msg)
	var row1 := HBoxContainer.new()
	row1.add_theme_constant_override("separation", 8)
	b3.add_child(row1)
	var b_ad := make_button("Watch Ad - Continue FREE", Color("#ffc93c"))
	b_ad.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	b_ad.pressed.connect(_on_ad_continue)
	row1.add_child(b_ad)
	btn_coin_cont = make_button("100 Coins - Continue", Color("#22c7a9"))
	btn_coin_cont.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	btn_coin_cont.pressed.connect(_on_coin_continue)
	row1.add_child(btn_coin_cont)
	var b_end := make_button("End Game", Color("#1f2742"), Color("#eef1ff"))
	b_end.pressed.connect(_on_end_game)
	b3.add_child(b_end)

	# ---- ad ----
	var o4 := make_overlay()
	ov_ad = o4["root"]
	var b4: VBoxContainer = o4["box"]
	b4.add_child(make_label("Advertisement", 20, Color.WHITE, true))
	b4.add_child(make_label("AD", 11, Color("#ffc93c"), true))
	b4.add_child(make_label("Block Puzzle Master", 18, Color.WHITE, true))
	b4.add_child(make_label("Your game continues in...", 13, Color("#9aa5c7"), true))
	lbl_ad_count = make_label("5", 40, Color.WHITE, true)
	b4.add_child(lbl_ad_count)
	b4.add_child(make_label("Your reward arrives when the ad ends", 12, Color("#9aa5c7"), true))

	# ---- how to ----
	var o5 := make_overlay()
	ov_how = o5["root"]
	var b5: VBoxContainer = o5["box"]
	b5.add_child(make_label("How to Play", 22, Color.WHITE, true))
	var how_text := "Drag the 3 blocks below onto the grid.\n\nWhen a row or column is completely filled, it clears - you earn score + coins.\n\nClear lines back-to-back for a combo - more score!\n\nEvery 300 points you go up a Level - the blocks get harder.\n\nWhen no block fits, it is game over - but you can continue by watching an ad or paying 100 coins (the bottom 4 rows will be cleared)."
	var p5 := make_label(how_text, 13, Color("#c6cdea"))
	p5.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	b5.add_child(p5)
	var b_close5 := make_button("Got it!", Color("#ffc93c"))
	b_close5.pressed.connect(func() -> void: ov_how.visible = false)
	b5.add_child(b_close5)

# ================= GAME LOGIC =================
func new_game() -> void:
	grid = []
	for r in range(N):
		var row: Array = []
		for c in range(N):
			row.append(-1)
		grid.append(row)
	score = 0
	streak = 0
	level = 1
	game_active = true
	continued = 0
	deal_pieces()
	refresh()

func deal_pieces() -> void:
	tray = [pick_piece(), pick_piece(), pick_piece()]

func pick_piece() -> Dictionary:
	var band := 0
	if level >= 6:
		band = 3
	elif level >= 4:
		band = 2
	elif level >= 2:
		band = 1
	var pool: Array = []
	for p in pieces:
		var w := 10 if int(p["tier"]) == 0 else 7 if int(p["tier"]) == 1 else 4 if int(p["tier"]) == 2 else 2
		if int(p["tier"]) > band:
			w = maxi(1, w - 5)
		if int(p["tier"]) == band and band > 0:
			w += 4
		if int(p["size"]) <= 2 and level >= 5:
			w = 2
		for i in range(w):
			pool.append(p)
	return pool[randi() % pool.size()]

func can_place(p: Dictionary, r0: int, c0: int) -> bool:
	for cell in p["cells"]:
		var rr: int = r0 + int(cell[0])
		var cc: int = c0 + int(cell[1])
		if rr < 0 or cc < 0 or rr >= N or cc >= N or int(grid[rr][cc]) != -1:
			return false
	return true

func fits_anywhere(p: Dictionary) -> bool:
	for r in range(N):
		for c in range(N):
			if can_place(p, r, c):
				return true
	return false

func place_piece(p: Dictionary, r0: int, c0: int) -> void:
	for cell in p["cells"]:
		grid[r0 + int(cell[0])][c0 + int(cell[1])] = int(p["color"])
	score += int(p["size"])
	play_tone("place")
	var cleared := clear_lines()
	if cleared > 0:
		streak += 1
		var pts := cleared * 10 * streak + ((cleared - 1) * 10 if cleared > 1 else 0)
		score += pts
		coins += cleared * 5 + (10 if cleared > 1 else 0)
		save_data()
		sfx_coin()
		float_text("+" + str(pts) + ("  COMBO x" + str(streak) if streak > 1 else ""))
		sfx_clear(cleared)
	else:
		streak = 0
	var new_level := int(score / LEVEL_EVERY) + 1
	if new_level > level:
		level = new_level
		sfx_level()
		float_text("LEVEL " + str(level))
	if score > best:
		best = score
		save_data()
	refresh()
	check_end()

func clear_lines() -> int:
	var rows: Array = []
	var cols: Array = []
	for r in range(N):
		var full := true
		for c in range(N):
			if int(grid[r][c]) == -1:
				full = false
				break
		if full:
			rows.append(r)
	for c in range(N):
		var full := true
		for r in range(N):
			if int(grid[r][c]) == -1:
				full = false
				break
		if full:
			cols.append(c)
	var n := rows.size() + cols.size()
	if n == 0:
		return 0
	var cells := {}
	for r in rows:
		for c in range(N):
			cells[r * N + c] = true
	for c in cols:
		for r in range(N):
			cells[r * N + c] = true
	for k in cells.keys():
		grid[int(k) / N][int(k) % N] = -1
	board_view.flash_cells = cells
	board_view.queue_redraw()
	get_tree().create_timer(0.4).timeout.connect(func() -> void:
		board_view.flash_cells = {}
		board_view.queue_redraw()
	)
	return n

func check_end() -> void:
	var left: Array = []
	for p in tray:
		if p != null:
			left.append(p)
	if left.is_empty():
		return
	for p in left:
		if fits_anywhere(p):
			return
	await get_tree().create_timer(0.45).timeout
	game_over()

func game_over() -> void:
	if not game_active:
		return
	game_active = false
	sfx_over()
	lbl_final.text = str(score)
	lbl_over_msg.text = "New BEST score!" if score >= best and score > 0 else "Best: " + str(best) + "  -  You reached Level " + str(level)
	btn_coin_cont.text = str(COIN_CONTINUE) + " Coins (yours: " + str(coins) + ")"
	ov_over.visible = true

func rescue() -> void:
	for r in range(N - 4, N):
		for c in range(N):
			grid[r][c] = -1
	deal_pieces()
	game_active = true
	continued += 1
	ov_over.visible = false
	refresh()
	float_text("Continue! Bottom rows cleared")
	check_end()

# ================= RENDER =================
func mini_cell() -> float:
	return clamp(board_view.cell_size() * 0.52, 13.0, 20.0)

func refresh() -> void:
	board_view.grid = grid
	board_view.queue_redraw()
	for i in range(3):
		(tray_views[i] as Control).visible = tray[i] != null
		if tray[i] != null:
			tray_views[i].set_piece(tray[i], mini_cell())
	lbl_score.text = str(score)
	lbl_best.text = str(best)
	lbl_coins.text = "Coins: " + str(coins)
	lbl_level.text = str(level)
	var into := score % LEVEL_EVERY
	lvl_bar.value = into
	lbl_next.text = "Next: " + str(score - into + LEVEL_EVERY)

func float_text(txt: String) -> void:
	lbl_float.text = txt
	var rect := board_view.get_global_rect()
	lbl_float.global_position = rect.position + Vector2(rect.size.x / 2 - 70, rect.size.y / 2 - 16)
	lbl_float.visible = true
	lbl_float.modulate = Color(1, 1, 1, 1)
	var tw := create_tween()
	tw.set_parallel(true)
	tw.tween_property(lbl_float, "global_position", lbl_float.global_position + Vector2(0, -44), 0.95)
	tw.tween_property(lbl_float, "modulate:a", 0.0, 0.95)
	tw.chain().tween_callback(func() -> void: lbl_float.visible = false)

# ================= DRAG & DROP =================
func _input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		if event.pressed:
			_try_grab(event.position)
		elif drag != null:
			_drop(event.position)
	elif event is InputEventMouseMotion and drag != null:
		_update_drag(event.position)

func _try_grab(pos: Vector2) -> void:
	if not game_active or drag != null:
		return
	if ov_over.visible or ov_ad.visible or ov_how.visible:
		return
	for i in range(3):
		var p = tray[i]
		if p == null:
			continue
		var rect: Rect2 = (tray_views[i] as Control).get_global_rect().grow(8)
		if rect.has_point(pos):
			var local := pos - (tray_views[i] as Control).global_position
			var mc := mini_cell()
			var grab_c := clampi(int(local.x / mc), 0, int(p["cols"]) - 1)
			var grab_r := clampi(int(local.y / mc), 0, int(p["rows"]) - 1)
			var view = PieceView.new()
			view.set_piece(p, board_view.cell_size() - 2.4)
			view.z_index = 60
			view.mouse_filter = Control.MOUSE_FILTER_IGNORE
			add_child(view)
			drag = {"piece": p, "slot": i, "view": view, "grab_r": grab_r, "grab_c": grab_c}
			_update_drag(pos)
			return

func _drag_target(pos: Vector2):
	var cell: float = board_view.cell_size()
	var rect := board_view.get_global_rect()
	var px: float = pos.x
	var py: float = pos.y - cell * 1.4
	var c := int(floor((px - rect.position.x) / cell)) - int(drag["grab_c"])
	var r := int(floor((py - rect.position.y) / cell)) - int(drag["grab_r"])
	if r < -2 or c < -2 or r >= N or c >= N:
		return null
	return Vector2i(r, c)

func _update_drag(pos: Vector2) -> void:
	var cell: float = board_view.cell_size()
	var view: Control = drag["view"]
	view.global_position = pos - Vector2((int(drag["grab_c"]) + 0.5) * cell, (int(drag["grab_r"]) + 0.5) * cell) - Vector2(0, cell * 1.4)
	var t = _drag_target(pos)
	board_view.ghost_cells = []
	if t != null:
		var p: Dictionary = drag["piece"]
		board_view.ghost_ok = can_place(p, t.x, t.y)
		for cellv in p["cells"]:
			var rr: int = t.x + int(cellv[0])
			var cc: int = t.y + int(cellv[1])
			if rr >= 0 and cc >= 0 and rr < N and cc < N:
				board_view.ghost_cells.append(Vector2i(cc, rr))
	board_view.queue_redraw()

func _drop(pos: Vector2) -> void:
	var d = drag
	var t = _drag_target(pos)  # drag must still be set for this call
	drag = null
	(d["view"] as Control).queue_free()
	board_view.ghost_cells = []
	board_view.queue_redraw()
	if t != null and can_place(d["piece"], t.x, t.y):
		tray[int(d["slot"])] = null
		var all_used := true
		for p in tray:
			if p != null:
				all_used = false
		if all_used:
			deal_pieces()
		place_piece(d["piece"], t.x, t.y)
	else:
		refresh()

# ================= ADS (simulated break) =================
func show_ad(on_reward: Callable) -> void:
	if ad_busy:
		return
	ad_busy = true
	ov_ad.visible = true
	for i in range(5, 0, -1):
		lbl_ad_count.text = str(i)
		await get_tree().create_timer(1.0).timeout
	ov_ad.visible = false
	ad_busy = false
	on_reward.call()

# ================= HANDLERS =================
func _on_ad_continue() -> void:
	ov_over.visible = false
	show_ad(rescue)

func _on_coin_continue() -> void:
	if coins >= COIN_CONTINUE:
		coins -= COIN_CONTINUE
		save_data()
		rescue()
	else:
		lbl_over_msg.text = "Not enough coins (" + str(coins) + "/" + str(COIN_CONTINUE) + ") - tap +FREE above to watch an ad for coins, or use Watch Ad to continue FREE!"
		sfx_over()

func _on_end_game() -> void:
	ov_over.visible = false
	new_game()

func _on_free_coins() -> void:
	var wait := 60 - int(Time.get_unix_time_from_system() - last_free)
	if wait > 0:
		float_text("FREE coins again in " + str(wait) + "s")
		return
	show_ad(func() -> void:
		coins += FREE_COINS
		last_free = int(Time.get_unix_time_from_system())
		save_data()
		refresh()
		sfx_coin()
		float_text("+" + str(FREE_COINS) + " FREE coins!")
	)

func _on_sound_toggle() -> void:
	sound_on = not sound_on
	save_data()
	_update_sound_btn()

func _update_sound_btn() -> void:
	btn_sound.text = "♪ ON" if sound_on else "♪ OFF"
