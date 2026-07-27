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
}

/**
 * Owns match rules, turn management, scoring and formation resets on top of a [PhysicsWorld].
 * Pure Kotlin; deterministic. The fixed-timestep game loop that drives [step]/[endTurn] lives in
 * the GameViewModel. Turn/scoring/reset logic is completed in **M4**.
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

    /** Apply a flick to one of the current team's discs, entering the SIMULATING phase. */
    fun flick(discId: BodyId, dragVector: Vec2, shotType: ShotType) {
        // TODO(M4): convert drag → impulse, tag chip flag on the disc, phase → SIMULATING.
        state = state.copy(phase = MatchPhase.SIMULATING)
    }

    /** Advance the physics one fixed step, returning events for effect dispatch. */
    fun step(): List<CollisionEvent> {
        val events = world.step()
        state = state.copy(bodies = world.snapshot())
        return events
    }

    /** Called when the world comes to rest: score/goal resolution, then next team's turn. */
    fun endTurn() {
        // TODO(M4): goal resolution, win/turn-limit check, formation reset, hand turn to the other team.
        state = state.copy(
            phase = MatchPhase.AIMING,
            turn = state.turn.opponent(),
            turnNumber = state.turnNumber + 1,
        )
    }

    fun reset(kickoffTo: Team = Team.A) {
        state = MatchState(bodies = formationProvider.kickoff(kickoffTo), pitch = pitch)
        world.setBodies(state.bodies)
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

    fun kickoff(kickoffTo: Team): List<Body> {
        val bodies = mutableListOf<Body>()
        var id = 0
        val discRadius = 3.2f
        val discMass = 1.2f

        teamShape.forEachIndexed { i, f ->
            bodies += Body(
                id = BodyId(id++),
                kind = BodyKind.TEAM_A_DISC,
                position = Vec2(f.x * pitch.width, f.y * pitch.height),
                radius = discRadius, mass = discMass,
            )
        }
        teamShape.forEachIndexed { i, f ->
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
