package io.github.ukemeikot.flicksoccer.domain.ai

import io.github.ukemeikot.flicksoccer.domain.engine.Rules
import io.github.ukemeikot.flicksoccer.domain.model.BodyId
import io.github.ukemeikot.flicksoccer.domain.model.BodyKind
import io.github.ukemeikot.flicksoccer.domain.model.Difficulty
import io.github.ukemeikot.flicksoccer.domain.model.MatchState
import io.github.ukemeikot.flicksoccer.domain.model.ShotType
import io.github.ukemeikot.flicksoccer.domain.model.Team
import io.github.ukemeikot.flicksoccer.domain.model.Vec2
import io.github.ukemeikot.flicksoccer.domain.physics.CollisionEvent
import io.github.ukemeikot.flicksoccer.domain.physics.PhysicsWorld
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A candidate flick the AI evaluates by cloning the world and simulating it. */
data class ShotCandidate(
    val discId: BodyId,
    val dragVector: Vec2,
    val power: Float,
    val shotType: ShotType,
)

/** The plan the AI commits to for its turn. */
data class AiDecision(
    val candidate: ShotCandidate,
    val score: Float,
)

/**
 * Simulation-based candidate search (§6). Enumerates flicks, clones the world, simulates up to 4s,
 * scores the terminal state and selects per difficulty. Deterministic given a seed, so behaviour is
 * reproducible and testable.
 */
class AiPlanner(
    private val evaluator: Evaluator = Evaluator(),
) {
    fun plan(state: MatchState, team: Team, difficulty: Difficulty, seed: Long): AiDecision? {
        val rng = Random(seed)
        val candidates = enumerate(state, team, difficulty, rng)
        if (candidates.isEmpty()) return null

        val scored = candidates.map { c ->
            val result = simulate(state, team, c)
            AiDecision(c, evaluator.score(result, team))
        }
        return select(scored, difficulty, rng)
    }

    private fun enumerate(state: MatchState, team: Team, difficulty: Difficulty, rng: Random): List<ShotCandidate> {
        val discs = state.bodies.filter { it.team() == team }
        val dirCount = if (difficulty == Difficulty.EASY) 12 else 24
        val powers = floatArrayOf(0.55f, 0.8f, 1.0f)
        val shotTypes = if (difficulty == Difficulty.EASY) listOf(ShotType.GROUND)
        else listOf(ShotType.GROUND, ShotType.CHIP)

        val all = ArrayList<ShotCandidate>(discs.size * dirCount * powers.size * shotTypes.size)
        for (disc in discs) {
            for (i in 0 until dirCount) {
                val a = 2f * PI.toFloat() * i / dirCount
                val dirX = cos(a); val dirY = sin(a)
                for (p in powers) {
                    // Slingshot drag is opposite the intended launch direction.
                    val drag = Vec2(-dirX * p * Rules.MAX_DRAG_LEN, -dirY * p * Rules.MAX_DRAG_LEN)
                    for (st in shotTypes) all += ShotCandidate(disc.id, drag, p, st)
                }
            }
        }
        return when (difficulty) {
            Difficulty.EASY -> all.shuffled(rng).take(60)
            Difficulty.MEDIUM -> all.shuffled(rng).take(200)
            Difficulty.HARD -> all
        }
    }

    private fun simulate(state: MatchState, team: Team, c: ShotCandidate): SimResult {
        val world = PhysicsWorld(state.pitch)
        world.setBodies(state.bodies)
        world.applyFlick(c.discId, Rules.launchVelocity(c.dragVector), Rules.flickSpec(c.shotType))

        var goalBy: Team? = null
        var crossbar = false
        var steps = 0
        while (steps < MAX_SIM_STEPS && !world.isAtRest) {
            val events = world.step()
            for (e in events) {
                if (e is CollisionEvent.Goal) goalBy = e.scoredBy
                if (e is CollisionEvent.Crossbar) crossbar = true
            }
            if (goalBy != null) break
            steps++
        }
        val ball = world.snapshot().first { it.kind == BodyKind.BALL }
        return SimResult(goalBy, crossbar, ball.position, state)
    }

    private fun select(scored: List<AiDecision>, difficulty: Difficulty, rng: Random): AiDecision {
        val sorted = scored.sortedByDescending { it.score }
        return when (difficulty) {
            Difficulty.HARD -> sorted.first()
            Difficulty.MEDIUM -> {
                val pool = sorted.take((sorted.size / 10).coerceAtLeast(1))
                addNoise(pool.random(rng), angleDeg = 6f, powerJitter = 0f, rng)
            }
            Difficulty.EASY -> {
                val pool = sorted.take((sorted.size / 2).coerceAtLeast(1))
                addNoise(pool.random(rng), angleDeg = 15f, powerJitter = 0.2f, rng)
            }
        }
    }

    /** Perturb a chosen shot so lower difficulties miss more (keeps the same disc & shot type). */
    private fun addNoise(decision: AiDecision, angleDeg: Float, powerJitter: Float, rng: Random): AiDecision {
        val c = decision.candidate
        val angle = (rng.nextFloat() * 2f - 1f) * angleDeg * (PI.toFloat() / 180f)
        val ca = cos(angle); val sa = sin(angle)
        val rx = c.dragVector.x * ca - c.dragVector.y * sa
        val ry = c.dragVector.x * sa + c.dragVector.y * ca
        val scale = 1f + (rng.nextFloat() * 2f - 1f) * powerJitter
        val drag = Vec2(rx * scale, ry * scale)
        return decision.copy(candidate = c.copy(dragVector = drag, power = (c.power * scale).coerceIn(0f, 1f)))
    }

    companion object {
        const val MAX_SIM_STEPS = 480 // ~4 s at 1/120 s steps
    }
}

/** Terminal state of a simulated candidate. */
class SimResult(
    val goalBy: Team?,
    val crossbar: Boolean,
    val ballPos: Vec2,
    val state: MatchState,
)

/** Scores a terminal simulated state from [forTeam]'s perspective (§6 weights). */
class Evaluator {
    fun score(r: SimResult, forTeam: Team): Float {
        val pitch = r.state.pitch
        // Team A attacks the +y (top) goal; Team B the -y (bottom) goal.
        val targetY = if (forTeam == Team.A) pitch.height else 0f
        val target = Vec2(pitch.halfWidth, targetY)

        var s = 0f
        when (r.goalBy) {
            forTeam -> s += 1000f
            null -> Unit
            else -> s -= 2000f // conceded / own goal
        }
        val progress = if (forTeam == Team.A) r.ballPos.y else pitch.height - r.ballPos.y
        s += progress * PROGRESS_WEIGHT
        val dist = (r.ballPos - target).length()
        s += (pitch.height * 0.5f - dist).coerceAtLeast(0f) * DISTANCE_WEIGHT
        if (r.crossbar) s -= CROSSBAR_PENALTY
        return s
    }

    private companion object {
        const val PROGRESS_WEIGHT = 3f
        const val DISTANCE_WEIGHT = 2f
        const val CROSSBAR_PENALTY = 30f
    }
}
