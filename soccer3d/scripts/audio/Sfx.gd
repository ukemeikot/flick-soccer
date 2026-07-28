extends Node
## Procedural sound effects (no audio files). Synthesizes short tones into AudioStreamWAV and plays
## them through a small pool of players. Call Sfx.play("kick" / "goal" / "whistle").

const RATE := 22050

var _streams := {}
var _players: Array[AudioStreamPlayer] = []
var _next := 0

func _ready() -> void:
	_streams["kick"] = _wav(_tone(150.0, 0.12, 30.0, 0.9))
	_streams["goal"] = _wav(_sweep(420.0, 860.0, 0.45))
	_streams["whistle"] = _wav(_tone(2100.0, 0.35, 3.0, 0.5))
	for i in 6:
		var p := AudioStreamPlayer.new()
		add_child(p)
		_players.append(p)

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
