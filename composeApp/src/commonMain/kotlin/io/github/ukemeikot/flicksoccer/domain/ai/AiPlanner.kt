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
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

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

        // Score with a wall-clock budget; if we run out, decide from what we have (§6).
        val start = TimeSource.Monotonic.markNow()
        val scored = ArrayList<AiDecision>(candidates.size)
        for (c in candidates) {
            scored += AiDecision(c, evaluator.score(simulate(state, team, c), team))
            if (start.elapsedNow() > BUDGET) break
        }
        return select(scored, difficulty, rng)
    }

    private fun enumerate(state: MatchState, team: Team, difficulty: Difficulty, rng: Random): List<ShotCandidate> {
        val discs = state.bodies.filter { it.team() == team }
        val dirCount = if (difficulty == Difficulty.EASY) 12 else 24
        val powers = floatArrayOf(0.55f, 0.8f, 1.0f)
        val chipsAllowed = difficulty != Difficulty.EASY

        val all = ArrayList<ShotCandidate>(discs.size * dirCount * powers.size * 2)
        for (disc in discs) {
            // Corridor pruning: chips only matter when a defender blocks the ground path (§6).
            val shotTypes = if (chipsAllowed && chipWorthwhile(state, team, disc.position)) {
                listOf(ShotType.GROUND, ShotType.CHIP)
            } else {
                listOf(ShotType.GROUND)
            }
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

    /** True when an opponent disc sits in the corridor from [from] to the target goal mouth. */
    private fun chipWorthwhile(state: MatchState, team: Team, from: Vec2): Boolean {
        val pitch = state.pitch
        val target = Vec2(pitch.halfWidth, if (team == Team.A) pitch.height else 0f)
        return state.bodies.any { it.team() == team.opponent() && distToSegment(it.position, from, target) < CORRIDOR_HALF_WIDTH }
    }

    private fun distToSegment(p: Vec2, a: Vec2, b: Vec2): Float {
        val abx = b.x - a.x; val aby = b.y - a.y
        val lenSq = abx * abx + aby * aby
        if (lenSq < 1e-4f) return sqrt((p.x - a.x) * (p.x - a.x) + (p.y - a.y) * (p.y - a.y))
        var t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq
        t = t.coerceIn(0f, 1f)
        val cx = a.x + t * abx; val cy = a.y + t * aby
        return sqrt((p.x - cx) * (p.x - cx) + (p.y - cy) * (p.y - cy))
    }

    private fun simulate(state: MatchState, team: Team, c: ShotCandidate): SimResult {
        val startBall = state.bodies.first { it.kind == BodyKind.BALL }.position
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
        return SimResult(goalBy, crossbar, ball.position, startBall, state)
    }

    private fun select(scored: List<AiDecision>, difficulty: Difficulty, rng: Random): AiDecision {
        val ranked = scored.sortedByDescending { it.score }
        val best = ranked.first()
        // Only ever choose from shots that actually helped (positive ⇒ struck & advanced the ball),
        // so lower difficulties play sub-optimally but never fire a disc that misses entirely.
        val useful = ranked.filter { it.score > 0f }.ifEmpty { listOf(best) }
        return when (difficulty) {
            Difficulty.HARD -> best
            Difficulty.MEDIUM -> {
                val pool = useful.take((useful.size / 6).coerceAtLeast(1))
                addNoise(pool.random(rng), angleDeg = 5f, powerJitter = 0.05f, rng)
            }
            Difficulty.EASY -> {
                val pool = useful.take((useful.size * 2 / 5).coerceAtLeast(1))
                addNoise(pool.random(rng), angleDeg = 10f, powerJitter = 0.12f, rng)
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
        private val BUDGET = 1.5.seconds
        private const val CORRIDOR_HALF_WIDTH = 9f // ball + disc radii ⇒ a real block
    }
}

/** Terminal state of a simulated candidate. */
class SimResult(
    val goalBy: Team?,
    val crossbar: Boolean,
    val ballPos: Vec2,
    val startBall: Vec2,
    val state: MatchState,
)

/**
 * Scores a terminal simulated state from [forTeam]'s perspective (§6). Scoring is **relative to the
 * ball's starting position**: a shot that never touches the ball scores ~0 (not a competitive
 * "safe" value), so contacting shots dominate the ranking and even the random Easy/Medium pools are
 * full of shots that actually hit the ball.
 */
class Evaluator {
    fun score(r: SimResult, forTeam: Team): Float {
        val pitch = r.state.pitch
        // Team A attacks the +y (top) goal; Team B the -y (bottom) goal.
        val target = Vec2(pitch.halfWidth, if (forTeam == Team.A) pitch.height else 0f)

        var s = 0f
        when (r.goalBy) {
            forTeam -> s += 1000f
            null -> Unit
            else -> s -= 2000f // conceded / own goal
        }
        // How much closer to the target goal the ball ended up than where it started.
        val progressToward = (r.startBall - target).length() - (r.ballPos - target).length()
        s += progressToward * PROGRESS_WEIGHT
        // Small bonus for making contact at all, so the AI always engages the ball.
        val displacement = (r.ballPos - r.startBall).length()
        s += displacement * CONTACT_WEIGHT
        if (r.crossbar) s -= CROSSBAR_PENALTY
        return s
    }

    private companion object {
        const val PROGRESS_WEIGHT = 6f
        const val CONTACT_WEIGHT = 0.6f
        const val CROSSBAR_PENALTY = 30f
    }
}
