package io.github.ukemeikot.flicksoccer.domain.engine

import io.github.ukemeikot.flicksoccer.domain.model.Body
import io.github.ukemeikot.flicksoccer.domain.model.BodyId
import io.github.ukemeikot.flicksoccer.domain.model.BodyKind
import io.github.ukemeikot.flicksoccer.domain.model.MatchPhase
import io.github.ukemeikot.flicksoccer.domain.model.MatchState
import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.domain.model.ShotType
import io.github.ukemeikot.flicksoccer.domain.model.Team
import io.github.ukemeikot.flicksoccer.domain.model.Vec2
import io.github.ukemeikot.flicksoccer.domain.physics.CollisionEvent
import io.github.ukemeikot.flicksoccer.domain.physics.FlickSpec
import io.github.ukemeikot.flicksoccer.domain.physics.PhysicsWorld

/** Match rules constants (see §1 of the design brief). */
object Rules {
    const val GOALS_TO_WIN = 3
    const val TURN_LIMIT = 30            // 15 per side
    const val DISCS_PER_TEAM = 5
    const val MAX_DRAG_LEN = 22f
    const val MAX_LAUNCH_SPEED = 140f
    const val MIN_POWER_TO_FLICK = 0.08f
    const val GROUND_LOFT_FACTOR = 0.05f
    const val CHIP_LOFT_FACTOR = 0.55f
    const val CHIP_PLANE_SPEED_PENALTY = 0.8f

    fun flickSpec(shotType: ShotType): FlickSpec = when (shotType) {
        ShotType.GROUND -> FlickSpec(loftFactor = GROUND_LOFT_FACTOR, planeSpeedPenalty = 1f)
        ShotType.CHIP -> FlickSpec(loftFactor = CHIP_LOFT_FACTOR, planeSpeedPenalty = CHIP_PLANE_SPEED_PENALTY)
    }

    /** Convert a slingshot drag (disc → drag point) into a launch velocity opposite the drag. */
    fun launchVelocity(dragVector: Vec2): Vec2 {
        val power = powerOf(dragVector)
        if (power < MIN_POWER_TO_FLICK) return Vec2.ZERO
        val dir = (dragVector * -1f).normalizedOrZero()
        return dir * (power * MAX_LAUNCH_SPEED)
    }

    fun powerOf(dragVector: Vec2): Float = (dragVector.length() / MAX_DRAG_LEN).coerceIn(0f, 1f)
}

/**
 * Owns match rules, turn management, scoring and formation resets on top of a [PhysicsWorld].
 * Pure Kotlin; deterministic. The fixed-timestep loop that drives [step] and the settle/reset
 * transitions lives in the GameViewModel (§9).
 */
class GameEngine(
    private val pitch: PitchSpec = PitchSpec(),
    private val formationProvider: FormationProvider = FormationProvider(pitch),
) {
    private val world = PhysicsWorld(pitch)

    var state: MatchState = MatchState(bodies = formationProvider.kickoff(Team.A), pitch = pitch)
        private set

    init {
        world.setBodies(state.bodies)
    }

    val isAtRest: Boolean get() = world.isAtRest

    /** The disc ids the [team] may flick this turn. */
    fun discsOf(team: Team): List<Body> {
        val kind = if (team == Team.A) BodyKind.TEAM_A_DISC else BodyKind.TEAM_B_DISC
        return state.bodies.filter { it.kind == kind }
    }

    fun body(id: BodyId): Body? = state.bodies.firstOrNull { it.id == id }

    /** True when [id] is a disc the current team owns and we are aiming. */
    fun canFlick(id: BodyId): Boolean {
        if (state.phase != MatchPhase.AIMING) return false
        val b = body(id) ?: return false
        return b.team() == state.turn
    }

    /**
     * Apply a flick to one of the current team's discs, entering SIMULATING. Returns false (no-op)
     * if the shot is illegal or below the minimum power.
     */
    fun flick(discId: BodyId, dragVector: Vec2, shotType: ShotType): Boolean {
        if (!canFlick(discId)) return false
        val velocity = Rules.launchVelocity(dragVector)
        if (velocity == Vec2.ZERO) return false
        world.setBodies(state.bodies)
        world.applyFlick(discId, velocity, Rules.flickSpec(shotType))
        state = state.copy(bodies = world.snapshot(), phase = MatchPhase.SIMULATING)
        return true
    }

    /** Advance the physics one fixed step, returning events. Resolves goals into score/phase. */
    fun step(): List<CollisionEvent> {
        if (state.phase != MatchPhase.SIMULATING) return emptyList()
        val events = world.step()
        var scoreA = state.scoreA
        var scoreB = state.scoreB
        var phase = state.phase
        var winner = state.winner

        for (e in events) {
            if (e is CollisionEvent.Goal) {
                if (e.scoredBy == Team.A) scoreA++ else scoreB++
                phase = MatchPhase.GOAL_SCORED
                if (scoreA >= Rules.GOALS_TO_WIN) { phase = MatchPhase.MATCH_OVER; winner = Team.A }
                if (scoreB >= Rules.GOALS_TO_WIN) { phase = MatchPhase.MATCH_OVER; winner = Team.B }
            }
        }

        state = state.copy(bodies = world.snapshot(), scoreA = scoreA, scoreB = scoreB, phase = phase, winner = winner)
        return events
    }

    /** Team that conceded the most recent goal, i.e. who kicks off next (null if no goal pending). */
    fun concedingTeam(): Team? = when (state.phase) {
        MatchPhase.GOAL_SCORED -> if (state.scoreA + state.scoreB == 0) null else lastScorer()?.opponent()
        else -> null
    }

    private fun lastScorer(): Team? {
        // The scorer is whoever's score changed into GOAL_SCORED; recomputed from the ball's end.
        val ball = state.bodies.firstOrNull { it.kind == BodyKind.BALL } ?: return null
        return if (ball.position.y >= pitch.halfHeight) Team.A else Team.B
    }

    /** Called when the world settles after a normal (non-goal) simulation: hand over the turn. */
    fun settleTurn() {
        if (state.phase != MatchPhase.SIMULATING) return
        val nextTurnNumber = state.turnNumber + 1
        if (nextTurnNumber > Rules.TURN_LIMIT) {
            state = state.copy(phase = MatchPhase.MATCH_OVER, winner = winnerByScore())
        } else {
            state = state.copy(phase = MatchPhase.AIMING, turn = state.turn.opponent(), turnNumber = nextTurnNumber)
        }
    }

    /** Reset the formation after a goal celebration; conceding team kicks off. */
    fun kickoffAfterGoal() {
        if (state.phase != MatchPhase.GOAL_SCORED) return
        val conceding = lastScorer()?.opponent() ?: Team.A
        val nextTurnNumber = state.turnNumber + 1
        if (nextTurnNumber > Rules.TURN_LIMIT) {
            state = state.copy(phase = MatchPhase.MATCH_OVER, winner = winnerByScore())
            return
        }
        val bodies = formationProvider.kickoff(conceding)
        world.setBodies(bodies)
        state = state.copy(bodies = bodies, phase = MatchPhase.AIMING, turn = conceding, turnNumber = nextTurnNumber)
    }

    private fun winnerByScore(): Team? = when {
        state.scoreA > state.scoreB -> Team.A
        state.scoreB > state.scoreA -> Team.B
        else -> null // draw
    }

    fun reset(kickoffTo: Team = Team.A) {
        val bodies = formationProvider.kickoff(kickoffTo)
        world.setBodies(bodies)
        state = MatchState(bodies = bodies, turn = kickoffTo, pitch = pitch)
    }
}

/** Builds the 2-1-2 kickoff formation, mirrored about the halfway line. Stored as fractions. */
class FormationProvider(private val pitch: PitchSpec) {

    /** Fractional (x, y) positions for one team's half (y in 0..0.5 from own goal line). */
    private val teamShape = listOf(
        Vec2(0.25f, 0.12f), Vec2(0.75f, 0.12f), // back two
        Vec2(0.50f, 0.28f),                      // mid
        Vec2(0.30f, 0.42f), Vec2(0.70f, 0.42f),  // front two
    )

    fun kickoff(@Suppress("UNUSED_PARAMETER") kickoffTo: Team): List<Body> {
        val bodies = ArrayList<Body>(11)
        var id = 0
        val discRadius = 3.2f
        val discMass = 1.2f

        teamShape.forEach { f ->
            bodies += Body(
                id = BodyId(id++),
                kind = BodyKind.TEAM_A_DISC,
                position = Vec2(f.x * pitch.width, f.y * pitch.height),
                radius = discRadius, mass = discMass,
            )
        }
        teamShape.forEach { f ->
            bodies += Body(
                id = BodyId(id++),
                kind = BodyKind.TEAM_B_DISC,
                position = Vec2(f.x * pitch.width, pitch.height - f.y * pitch.height),
                radius = discRadius, mass = discMass,
            )
        }
        bodies += Body(
            id = BodyId(id),
            kind = BodyKind.BALL,
            position = Vec2(pitch.halfWidth, pitch.halfHeight),
            radius = 2.0f, mass = 0.4f,
        )
        return bodies
    }
}
