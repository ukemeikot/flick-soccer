package io.github.ukemeikot.flicksoccer.domain.physics

import io.github.ukemeikot.flicksoccer.domain.model.Body
import io.github.ukemeikot.flicksoccer.domain.model.BodyId
import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.domain.model.Team

/**
 * Events emitted by a physics [PhysicsWorld.step] so the ViewModel can fire throttled
 * sound/haptic effects. No rendering or platform concerns here.
 */
sealed interface CollisionEvent {
    data class DiscHitsBall(val discId: BodyId, val impactSpeed: Float) : CollisionEvent
    data class DiscHitsDisc(val a: BodyId, val b: BodyId, val impactSpeed: Float) : CollisionEvent
    data class WallBounce(val id: BodyId, val impactSpeed: Float) : CollisionEvent
    data class GroundBounce(val id: BodyId, val impactSpeed: Float) : CollisionEvent
    data object Crossbar : CollisionEvent
    data class Goal(val scoredBy: Team) : CollisionEvent
}

/**
 * Deterministic 2.5D physics engine (pure Kotlin, no randomness). Discs live on the 2D plane; the
 * ball is a sphere with a height axis. Holds mutable internal arrays for the inner loop but exposes
 * immutable [Body] snapshots.
 *
 * Full per-step pipeline (integrate → wall → disc–disc → disc–ball → sleep → goal) is implemented in
 * milestone **M1**; see [IMPLEMENTATION_PLAN.md]. This skeleton fixes the public contract used by the
 * engine, AI, and ViewModel so upper layers can be built against it.
 */
class PhysicsWorld(
    val pitch: PitchSpec = PitchSpec(),
) {
    private val bodies = mutableListOf<Body>()

    /** Fixed simulation timestep: two substeps per rendered 60fps frame. */
    val stepSeconds: Float get() = FIXED_DT

    fun setBodies(newBodies: List<Body>) {
        bodies.clear()
        bodies.addAll(newBodies)
    }

    fun snapshot(): List<Body> = bodies.toList()

    /** True when every body is at rest (plane speed below epsilon and, for the ball, grounded). */
    val isAtRest: Boolean
        get() = bodies.all { b ->
            b.velocity.lengthSquared() < pitch.stopSpeedEpsilon * pitch.stopSpeedEpsilon &&
                (b.z == 0f && b.vz == 0f)
        }

    /**
     * Advance the simulation by one fixed [stepSeconds] and return the collision events produced.
     * TODO(M1): implement the full per-step pipeline described in §4.1 of the design brief.
     */
    fun step(): List<CollisionEvent> {
        // M1 will replace this with the real integrator + collision resolution.
        return emptyList()
    }

    /** Deep copy for AI simulation (clone → simulate → score). */
    fun clone(): PhysicsWorld = PhysicsWorld(pitch).also { it.setBodies(bodies.toList()) }

    companion object {
        const val FIXED_DT = 1f / 120f
    }
}
