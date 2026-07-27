package io.github.ukemeikot.flicksoccer.domain.model

import kotlin.jvm.JvmInline
import kotlin.math.sqrt

/** A 2D vector on the pitch plane. World units only — never pixels. */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    fun dot(o: Vec2) = x * o.x + y * o.y
    fun lengthSquared() = x * x + y * y
    fun length() = sqrt(lengthSquared())
    fun normalizedOrZero(): Vec2 {
        val len = length()
        return if (len <= EPSILON) ZERO else Vec2(x / len, y / len)
    }

    companion object {
        val ZERO = Vec2(0f, 0f)
        const val EPSILON = 1e-6f
    }
}

@JvmInline
value class BodyId(val value: Int)

enum class BodyKind { TEAM_A_DISC, TEAM_B_DISC, BALL }

enum class Team { A, B;
    fun opponent(): Team = if (this == A) B else A
}

enum class Difficulty { EASY, MEDIUM, HARD }

enum class ShotType { GROUND, CHIP }

enum class MatchPhase { AIMING, SIMULATING, GOAL_SCORED, MATCH_OVER }

/**
 * A physics body. Discs are constrained to the plane (z = 0, vz = 0). The ball is a sphere with a
 * height axis: [z] is the height of the ball's bottom above the pitch, [vz] its vertical velocity.
 */
data class Body(
    val id: BodyId,
    val kind: BodyKind,
    val position: Vec2,
    val velocity: Vec2 = Vec2.ZERO,
    val z: Float = 0f,
    val vz: Float = 0f,
    val radius: Float,
    val mass: Float,
) {
    val isBall: Boolean get() = kind == BodyKind.BALL
    fun team(): Team? = when (kind) {
        BodyKind.TEAM_A_DISC -> Team.A
        BodyKind.TEAM_B_DISC -> Team.B
        BodyKind.BALL -> null
    }
}

/** All sizes in abstract world units. Default pitch = 100 x 160. See §4 of the design brief. */
data class PitchSpec(
    val width: Float = 100f,
    val height: Float = 160f,
    val goalMouthWidth: Float = 34f,
    val crossbarHeight: Float = 6f,        // ≈ 3 × ball radius
    val discHeight: Float = 2.4f,          // ≈ 1.2 × ball radius
    val gravity: Float = 260f,
    val groundRestitution: Float = 0.55f,
    val wallRestitution: Float = 0.75f,
    val discRestitution: Float = 0.9f,
    val linearDampingPerSec: Float = 1.5f, // rolling friction while grounded
    val airDampingPerSec: Float = 0.25f,   // air drag while airborne
    val stopSpeedEpsilon: Float = 1.5f,
) {
    val halfWidth: Float get() = width / 2f
    val halfHeight: Float get() = height / 2f
}

/** Immutable aim state, non-null while dragging. */
data class AimState(
    val discId: BodyId,
    val dragVector: Vec2,   // world units, from disc center toward the drag point
    val power: Float,       // clamped 0..1
    val shotType: ShotType,
)

/** The full, immutable match state owned by the engine. */
data class MatchState(
    val bodies: List<Body>,
    val scoreA: Int = 0,
    val scoreB: Int = 0,
    val turn: Team = Team.A,
    val turnNumber: Int = 1,
    val phase: MatchPhase = MatchPhase.AIMING,
    val winner: Team? = null,
    val pitch: PitchSpec = PitchSpec(),
)
