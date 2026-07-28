extends Node
## Procedural sound effects (no audio files). Synthesizes short tones into AudioStreamWAV and plays
## them through a small pool of players. Call Sfx.play("kick" / "goal" / "whistle").

const RATE := 22050

var _streams := {}
var _players: Array[AudioStreamPlayer] = []
var _next := 0
var _ambience: AudioStreamPlayer

func _ready() -> void:
	_streams["kick"] = _wav(_tone(150.0, 0.12, 30.0, 0.9))
	_streams["goal"] = _wav(_sweep(420.0, 860.0, 0.45))
	_streams["whistle"] = _wav(_tone(2100.0, 0.35, 3.0, 0.5))
	_streams["roar"] = _wav(_roar(1.4))
	for i in 6:
		var p := AudioStreamPlayer.new()
		add_child(p)
		_players.append(p)
	_ambience = AudioStreamPlayer.new()
	_ambience.stream = _wav_loop(_crowd(2.0))
	_ambience.volume_db = -22.0
	add_child(_ambience)

## Start/stop the looping crowd ambience (call at match start / on leaving).
func start_ambience() -> void:
	if _ambience != null and not _ambience.playing:
		_ambience.play()

func stop_ambience() -> void:
	if _ambience != null:
		_ambience.stop()

func play(name: String) -> void:
	var s: AudioStreamWAV = _streams.get(name)
	if s == null:
		return
	var p := _players[_next]
	_next = (_next + 1) % _players.size()
	p.stream = s
	p.play()

func _tone(freq: float, seconds: float, decay: float, gain: float) -> PackedFloat32Array:
	var n := int(seconds * RATE)
	var a := PackedFloat32Array()
	a.resize(n)
	for i in n:
		var t := float(i) / RATE
		a[i] = gain * exp(-t * decay) * sin(TAU * freq * t)
	return a

func _sweep(f0: float, f1: float, seconds: float) -> PackedFloat32Array:
	var n := int(seconds * RATE)
	var a := PackedFloat32Array()
	a.resize(n)
	for i in n:
		var t := float(i) / RATE
		var f := lerpf(f0, f1, t / seconds)
		var env := 1.0 - t / seconds
		a[i] = 0.6 * env * sin(TAU * f * t)
	return a

## Low, steady filtered noise — a stadium murmur that loops seamlessly.
func _crowd(seconds: float) -> PackedFloat32Array:
	var n := int(seconds * RATE)
	var a := PackedFloat32Array()
	a.resize(n)
	var prev := 0.0
	for i in n:
		var white := randf() * 2.0 - 1.0
		prev = lerpf(prev, white, 0.05) # low-pass → rumble
		a[i] = prev * 0.5
	# Fade the ends into each other so the loop point isn't a click.
	var f := int(RATE * 0.05)
	for i in f:
		var g := float(i) / f
		a[i] *= g
		a[n - 1 - i] *= g
	return a

## A swelling crowd roar for goals.
func _roar(seconds: float) -> PackedFloat32Array:
	var n := int(seconds * RATE)
	var a := PackedFloat32Array()
	a.resize(n)
	var prev := 0.0
	for i in n:
		var t := float(i) / RATE
		var env := minf(t / 0.25, 1.0) * (1.0 - t / seconds) # swell then decay
		var white := randf() * 2.0 - 1.0
		prev = lerpf(prev, white, 0.12)
		a[i] = prev * env * 0.9
	return a

func _wav_loop(samples: PackedFloat32Array) -> AudioStreamWAV:
	var s := _wav(samples)
	s.loop_mode = AudioStreamWAV.LOOP_FORWARD
	s.loop_begin = 0
	s.loop_end = samples.size()
	return s

func _wav(samples: PackedFloat32Array) -> AudioStreamWAV:
	var bytes := PackedByteArray()
	bytes.resize(samples.size() * 2)
	for i in samples.size():
		bytes.encode_s16(i * 2, int(clampf(samples[i], -1.0, 1.0) * 32767.0))
	var s := AudioStreamWAV.new()
	s.format = AudioStreamWAV.FORMAT_16_BITS
	s.mix_rate = RATE
	s.stereo = false
	s.data = bytes
	return s
