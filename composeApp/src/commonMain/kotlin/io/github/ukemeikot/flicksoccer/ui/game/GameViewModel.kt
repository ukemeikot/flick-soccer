package io.github.ukemeikot.flicksoccer.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukemeikot.flicksoccer.data.MatchHistoryRepository
import io.github.ukemeikot.flicksoccer.data.MatchResult
import io.github.ukemeikot.flicksoccer.domain.engine.GameEngine
import io.github.ukemeikot.flicksoccer.domain.engine.Rules
import io.github.ukemeikot.flicksoccer.domain.model.AimState
import io.github.ukemeikot.flicksoccer.domain.model.BodyId
import io.github.ukemeikot.flicksoccer.domain.model.BodyKind
import io.github.ukemeikot.flicksoccer.domain.model.Difficulty
import io.github.ukemeikot.flicksoccer.domain.model.MatchPhase
import io.github.ukemeikot.flicksoccer.domain.model.ShotType
import io.github.ukemeikot.flicksoccer.domain.model.Team
import io.github.ukemeikot.flicksoccer.domain.model.Vec2
import io.github.ukemeikot.flicksoccer.domain.physics.CollisionEvent
import io.github.ukemeikot.flicksoccer.platform.AudioPlayer
import io.github.ukemeikot.flicksoccer.platform.Haptics
import io.github.ukemeikot.flicksoccer.platform.SoundEffect
import io.github.ukemeikot.flicksoccer.platform.gl.PointerEventGl
import io.github.ukemeikot.flicksoccer.ui.game.render.BodyTransform
import io.github.ukemeikot.flicksoccer.ui.game.render.RenderSnapshot
import io.github.ukemeikot.flicksoccer.util.FixedTimestepClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import kotlin.time.TimeSource

data class GameUiState(
    val match: io.github.ukemeikot.flicksoccer.domain.model.MatchState,
    val aim: AimState? = null,
    val vsAi: Boolean = false,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val isPaused: Boolean = false,
    val shotType: ShotType = ShotType.GROUND,
)

/** One-shot effects the ViewModel emits for the View to play (sound/haptic). */
sealed interface GameEffect {
    data object KickSound : GameEffect
    data object WallBounceSound : GameEffect
    data object GroundBounceSound : GameEffect
    data object CrossbarClangSound : GameEffect
    data class GoalScored(val by: Team) : GameEffect
    data object WhistleEnd : GameEffect
    data object HapticTick : GameEffect
}

/**
 * Owns the match: fixed-timestep game loop, turn management, effect dispatch and RenderSnapshot
 * publishing (§9). The GL thread reads [renderSnapshot] each frame; the Compose HUD observes
 * [state]. A lightweight stopgap AI keeps vs-AI matches moving until the M5 planner lands.
 */
class GameViewModel(
    private val audio: AudioPlayer,
    private val haptics: Haptics,
    private val history: MatchHistoryRepository,
) : ViewModel() {

    private val engine = GameEngine()

    private val _state = MutableStateFlow(GameUiState(match = engine.state))
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<GameEffect>(extraBufferCapacity = 32)
    val effects: SharedFlow<GameEffect> = _effects.asSharedFlow()

    // Latest frame for the GL thread. Plain reference read/write is atomic on our targets.
    private var latestSnapshot: RenderSnapshot? = null
    fun renderSnapshot(): RenderSnapshot? = latestSnapshot

    private var loopJob: Job? = null
    private var vsAi = false
    private var difficulty = Difficulty.MEDIUM
    private var shotType = ShotType.GROUND
    private var paused = false
    private var recorded = false

    private var aimDiscId: BodyId? = null
    private var aimDrag = Vec2.ZERO

    private var goalTimer = 0f
    private var aiScheduledForTurn = -1
    private var spin = 0f

    fun startMatch(vsAi: Boolean, difficulty: Difficulty) {
        this.vsAi = vsAi
        this.difficulty = difficulty
        shotType = ShotType.GROUND
        paused = false
        recorded = false
        goalTimer = 0f
        aiScheduledForTurn = -1
        aimDiscId = null
        aimDrag = Vec2.ZERO
        engine.reset()
        publishState()
        publishSnapshot()
        startLoop()
    }

    fun rematch() = startMatch(vsAi, difficulty)

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = viewModelScope.launch {
            val clock = FixedTimestepClock(1f / 120f)
            var last = TimeSource.Monotonic.markNow()
            while (isActive) {
                delay(6)
                val now = TimeSource.Monotonic.markNow()
                val dt = (now - last).inWholeMicroseconds / 1_000_000f
                last = now
                if (!paused) tick(dt, clock)
                publishSnapshot()
            }
        }
    }

    private fun tick(dt: Float, clock: FixedTimestepClock) {
        when (engine.state.phase) {
            MatchPhase.SIMULATING -> {
                clock.accumulate(dt)
                val events = ArrayList<CollisionEvent>()
                var steps = 0
                while (clock.hasStep() && steps < MAX_STEPS_PER_FRAME) {
                    events += engine.step(); clock.consumeStep(); steps++
                }
                dispatchEffects(events)
                when (engine.state.phase) {
                    MatchPhase.MATCH_OVER -> onMatchOver()
                    MatchPhase.SIMULATING -> if (engine.isAtRest) { engine.settleTurn(); afterSettle() }
                    else -> publishState() // GOAL_SCORED: start celebration
                }
            }

            MatchPhase.GOAL_SCORED -> {
                goalTimer += dt
                if (goalTimer >= GOAL_CELEBRATION_SEC) {
                    goalTimer = 0f
                    engine.kickoffAfterGoal()
                    if (engine.state.phase == MatchPhase.MATCH_OVER) onMatchOver() else afterSettle()
                }
            }

            MatchPhase.AIMING -> maybeScheduleAi()
            MatchPhase.MATCH_OVER -> Unit
        }
    }

    private fun afterSettle() {
        aiScheduledForTurn = -1
        publishState()
    }

    private fun onMatchOver() {
        if (!recorded) {
            recorded = true
            history.record(MatchResult(engine.state.scoreA, engine.state.scoreB, vsAi, engine.state.turnNumber))
            _effects.tryEmit(GameEffect.WhistleEnd)
        }
        publishState()
    }

    // --- Input (pitch-plane world coordinates from the surface) --------------------------------

    fun onPointer(e: PointerEventGl) {
        if (paused || !isHumanTurn()) return
        when (e.type) {
            PointerEventGl.Type.DOWN -> {
                val disc = pickDisc(e.x, e.y) ?: return
                aimDiscId = disc.id
                aimDrag = Vec2.ZERO
                publishState()
            }
            PointerEventGl.Type.MOVE -> {
                val id = aimDiscId ?: return
                val disc = engine.body(id) ?: return
                var drag = Vec2(e.x - disc.position.x, e.y - disc.position.y)
                val len = drag.length()
                if (len > Rules.MAX_DRAG_LEN) drag = drag * (Rules.MAX_DRAG_LEN / len)
                aimDrag = drag
                publishState()
            }
            PointerEventGl.Type.UP -> {
                val id = aimDiscId
                aimDiscId = null
                if (id != null && Rules.powerOf(aimDrag) >= Rules.MIN_POWER_TO_FLICK) {
                    if (engine.flick(id, aimDrag, shotType)) {
                        audio.play(SoundEffect.KICK)
                        haptics.tick()
                    }
                }
                aimDrag = Vec2.ZERO
                publishState()
            }
        }
    }

    private fun pickDisc(x: Float, y: Float): io.github.ukemeikot.flicksoccer.domain.model.Body? {
        var best: io.github.ukemeikot.flicksoccer.domain.model.Body? = null
        var bestD = Float.MAX_VALUE
        for (d in engine.discsOf(engine.state.turn)) {
            val dx = x - d.position.x; val dy = y - d.position.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= d.radius + TOUCH_MARGIN && dist < bestD) { bestD = dist; best = d }
        }
        return best
    }

    fun setShotType(shotType: ShotType) { this.shotType = shotType; publishState() }
    fun setPaused(paused: Boolean) { this.paused = paused; publishState() }
    private fun isHumanTurn(): Boolean = !vsAi || engine.state.turn == Team.A

    // --- Stopgap AI (replaced by AiPlanner in M5) ---------------------------------------------

    private fun maybeScheduleAi() {
        if (!vsAi || engine.state.turn != Team.B) return
        if (aiScheduledForTurn == engine.state.turnNumber) return
        aiScheduledForTurn = engine.state.turnNumber
        viewModelScope.launch {
            delay(AI_THINK_MILLIS)
            if (engine.state.phase == MatchPhase.AIMING && engine.state.turn == Team.B) performAiFlick()
        }
    }

    private fun performAiFlick() {
        val ball = engine.state.bodies.firstOrNull { it.kind == BodyKind.BALL } ?: return
        val disc = engine.discsOf(Team.B).minByOrNull {
            val dx = it.position.x - ball.position.x; val dy = it.position.y - ball.position.y
            dx * dx + dy * dy
        } ?: return
        // Drag away from the ball → launches the disc toward the ball (slingshot is opposite drag).
        val away = Vec2(disc.position.x - ball.position.x, disc.position.y - ball.position.y).normalizedOrZero()
        val drag = away * (Rules.MAX_DRAG_LEN * 0.85f)
        if (engine.flick(disc.id, drag, ShotType.GROUND)) {
            audio.play(SoundEffect.KICK)
        }
        publishState()
    }

    // --- Effects & publishing ------------------------------------------------------------------

    private fun dispatchEffects(events: List<CollisionEvent>) {
        if (events.isEmpty()) return
        var kick = false; var wall = false; var ground = false; var crossbar = false
        var goal: Team? = null
        for (e in events) when (e) {
            is CollisionEvent.DiscHitsBall -> kick = true
            is CollisionEvent.WallBounce -> wall = true
            is CollisionEvent.GroundBounce -> ground = true
            is CollisionEvent.Crossbar -> crossbar = true
            is CollisionEvent.Goal -> goal = e.scoredBy
            is CollisionEvent.DiscHitsDisc -> wall = true // reuse the knock sound
        }
        if (kick) { audio.play(SoundEffect.KICK); haptics.tick(); _effects.tryEmit(GameEffect.KickSound) }
        if (wall) { audio.play(SoundEffect.WALL_BOUNCE); _effects.tryEmit(GameEffect.WallBounceSound) }
        if (ground) { audio.play(SoundEffect.GROUND_BOUNCE); _effects.tryEmit(GameEffect.GroundBounceSound) }
        if (crossbar) { audio.play(SoundEffect.CROSSBAR); haptics.tick(); _effects.tryEmit(GameEffect.CrossbarClangSound) }
        goal?.let { audio.play(SoundEffect.GOAL); haptics.tick(); _effects.tryEmit(GameEffect.GoalScored(it)) }
    }

    private fun publishState() {
        val aim = aimDiscId?.let { AimState(it, aimDrag, Rules.powerOf(aimDrag), shotType) }
        _state.value = GameUiState(
            match = engine.state,
            aim = aim,
            vsAi = vsAi,
            difficulty = difficulty,
            isPaused = paused,
            shotType = shotType,
        )
    }

    private fun publishSnapshot() {
        val s = engine.state
        val ball = s.bodies.firstOrNull { it.kind == BodyKind.BALL }
        if (ball != null) spin += ball.velocity.length() * 0.02f
        val aim = aimDiscId?.let { AimState(it, aimDrag, Rules.powerOf(aimDrag), shotType) }
        latestSnapshot = RenderSnapshot(
            bodies = s.bodies.map {
                BodyTransform(it.id.value, it.kind, it.position.x, it.position.y, it.z, it.radius, if (it.isBall) spin else 0f)
            },
            aim = aim,
            phase = s.phase,
            scoreFlashSeconds = if (s.phase == MatchPhase.GOAL_SCORED) GOAL_CELEBRATION_SEC - goalTimer else 0f,
            goalCamPunchSeconds = 0f,
        )
    }

    override fun onCleared() {
        loopJob?.cancel()
        audio.release()
        super.onCleared()
    }

    companion object {
        private const val GOAL_CELEBRATION_SEC = 1.5f
        private const val AI_THINK_MILLIS = 800L
        private const val MAX_STEPS_PER_FRAME = 8
        private const val TOUCH_MARGIN = 2.5f
    }
}
