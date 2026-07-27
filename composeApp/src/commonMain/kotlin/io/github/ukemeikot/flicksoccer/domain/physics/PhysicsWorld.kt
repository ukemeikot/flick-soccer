package io.github.ukemeikot.flicksoccer.domain.physics

import io.github.ukemeikot.flicksoccer.domain.model.Body
import io.github.ukemeikot.flicksoccer.domain.model.BodyId
import io.github.ukemeikot.flicksoccer.domain.model.BodyKind
import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.domain.model.Team
import io.github.ukemeikot.flicksoccer.domain.model.Vec2
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Events emitted by [PhysicsWorld.step] so the ViewModel can fire throttled sound/haptic effects.
 * No rendering or platform concerns here.
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
 * Loft applied to the ball on the striking disc's first contact. Ground gives a tiny natural hop;
 * chip launches the ball airborne at the cost of plane speed. See §4.3 of the design brief.
 */
class FlickSpec(
    val loftFactor: Float,
    val planeSpeedPenalty: Float,
)

/**
 * Deterministic 2.5D physics engine (pure Kotlin, no randomness). Discs live on the 2D plane; the
 * ball is a sphere with a height axis. The hot path mutates primitive fields on internal
 * [MutableBody]s — **zero allocations per step** — and exposes immutable [Body] snapshots.
 *
 * Convention: Team A attacks the +y (top) goal, Team B attacks the -y (bottom) goal.
 */
class PhysicsWorld(
    val pitch: PitchSpec = PitchSpec(),
) {
    /** Internal mutable body; primitive fields keep [step] allocation-free. */
    private class MutableBody(
        val id: Int,
        val kind: BodyKind,
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var z: Float,
        var vz: Float,
        val radius: Float,
        val mass: Float,
    ) {
        val invMass: Float = if (mass <= 0f) 0f else 1f / mass
        val isBall: Boolean = kind == BodyKind.BALL
        /** Loft/penalty carried by a flicked disc until its first ball contact, then cleared. */
        var pendingFlick: FlickSpec? = null
    }

    private val bodies = ArrayList<MutableBody>()
    private var ballIndex = -1

    /** Fixed simulation timestep: two substeps per rendered 60fps frame. */
    val stepSeconds: Float get() = FIXED_DT

    fun setBodies(newBodies: List<Body>) {
        bodies.clear()
        ballIndex = -1
        newBodies.forEachIndexed { i, b ->
            if (b.kind == BodyKind.BALL) ballIndex = i
            bodies += MutableBody(
                id = b.id.value,
                kind = b.kind,
                x = b.position.x, y = b.position.y,
                vx = b.velocity.x, vy = b.velocity.y,
                z = b.z, vz = b.vz,
                radius = b.radius, mass = b.mass,
            )
        }
    }

    fun snapshot(): List<Body> = bodies.map { mb ->
        Body(
            id = BodyId(mb.id),
            kind = mb.kind,
            position = Vec2(mb.x, mb.y),
            velocity = Vec2(mb.vx, mb.vy),
            z = mb.z, vz = mb.vz,
            radius = mb.radius, mass = mb.mass,
        )
    }

    /** Apply a flick to a disc from rest: sets its velocity and arms the loft/chip for first contact. */
    fun applyFlick(discId: BodyId, velocity: Vec2, flick: FlickSpec) {
        val b = bodies.firstOrNull { it.id == discId.value } ?: return
        if (b.isBall) return
        b.vx = velocity.x
        b.vy = velocity.y
        b.pendingFlick = flick
    }

    /** True when every body is at rest (plane speed below epsilon and, for the ball, grounded). */
    val isAtRest: Boolean
        get() {
            val stopSq = pitch.stopSpeedEpsilon * pitch.stopSpeedEpsilon
            return bodies.all { b ->
                (b.vx * b.vx + b.vy * b.vy) < stopSq && b.z == 0f && b.vz == 0f
            }
        }

    /** Advance the simulation by one fixed [stepSeconds]; returns the collision events produced. */
    fun step(): List<CollisionEvent> {
        val events = ArrayList<CollisionEvent>(4)
        val dt = FIXED_DT

        integrate(dt, events)
        resolveBodyCollisions(events)
        resolveWallsAndGoals(events)

        return events
    }

    private fun integrate(dt: Float, events: MutableList<CollisionEvent>) {
        val groundFactor = dampingFactor(pitch.linearDampingPerSec, dt)
        val airFactor = dampingFactor(pitch.airDampingPerSec, dt)

        for (b in bodies) {
            // Plane damping is state-dependent for the ball (rolling friction vs air drag).
            val factor = if (b.isBall && b.z > 0f) airFactor else groundFactor
            b.vx *= factor
            b.vy *= factor
            b.x += b.vx * dt
            b.y += b.vy * dt

            if (b.isBall) {
                b.vz -= pitch.gravity * dt
                b.z += b.vz * dt
                if (b.z <= 0f && b.vz < 0f) {
                    val impact = -b.vz // downward speed at landing
                    b.z = 0f
                    b.vz = impact * pitch.groundRestitution
                    if (b.vz < VZ_EPSILON) b.vz = 0f
                    if (impact > VZ_EPSILON) events += CollisionEvent.GroundBounce(BodyId(b.id), impact)
                }
                if (b.z < 0f) b.z = 0f
            }
        }
    }

    private fun resolveBodyCollisions(events: MutableList<CollisionEvent>) {
        val n = bodies.size
        for (i in 0 until n) {
            val a = bodies[i]
            for (j in i + 1 until n) {
                val b = bodies[j]

                // Ball only interacts with a disc when it is low enough to be struck (§4.1 rule 5).
                val involvesBall = a.isBall || b.isBall
                if (involvesBall) {
                    val ball = if (a.isBall) a else b
                    if (ball.z >= pitch.discHeight) continue
                }

                val dx = b.x - a.x
                val dy = b.y - a.y
                var dist = sqrt(dx * dx + dy * dy)
                val minDist = a.radius + b.radius
                if (dist >= minDist) continue

                // Degenerate exact-overlap: nudge along a fixed axis to stay deterministic.
                val nx: Float
                val ny: Float
                if (dist < 1e-4f) {
                    nx = 1f; ny = 0f; dist = 0f
                } else {
                    nx = dx / dist; ny = dy / dist
                }

                val overlap = minDist - dist
                val invSum = a.invMass + b.invMass
                if (invSum <= 0f) continue

                // Positional correction split by inverse mass.
                val corrA = overlap * (a.invMass / invSum)
                val corrB = overlap * (b.invMass / invSum)
                a.x -= nx * corrA; a.y -= ny * corrA
                b.x += nx * corrB; b.y += ny * corrB

                // Relative velocity along the normal.
                val rvx = b.vx - a.vx
                val rvy = b.vy - a.vy
                val relAlongNormal = rvx * nx + rvy * ny
                if (relAlongNormal >= 0f) continue // separating

                val jImpulse = -(1f + pitch.discRestitution) * relAlongNormal / invSum
                val impulseX = jImpulse * nx
                val impulseY = jImpulse * ny
                a.vx -= impulseX * a.invMass; a.vy -= impulseY * a.invMass
                b.vx += impulseX * b.invMass; b.vy += impulseY * b.invMass

                val impactSpeed = abs(relAlongNormal)

                if (involvesBall) {
                    val ball = if (a.isBall) a else b
                    val disc = if (a.isBall) b else a
                    applyLoft(ball, disc, impactSpeed)
                    events += CollisionEvent.DiscHitsBall(BodyId(disc.id), impactSpeed)
                } else if (impactSpeed > DISC_EVENT_SPEED) {
                    events += CollisionEvent.DiscHitsDisc(BodyId(a.id), BodyId(b.id), impactSpeed)
                }
            }
        }
    }

    /** Transfers loft to the ball on contact; a chip trades plane speed for height (§4.3). */
    private fun applyLoft(ball: MutableBody, disc: MutableBody, impactSpeed: Float) {
        val flick = disc.pendingFlick
        val loftFactor = flick?.loftFactor ?: GROUND_LOFT_FALLBACK
        ball.vz += loftFactor * impactSpeed
        if (flick != null) {
            ball.vx *= flick.planeSpeedPenalty
            ball.vy *= flick.planeSpeedPenalty
            disc.pendingFlick = null // flag clears on first ball contact
        }
    }

    private fun resolveWallsAndGoals(events: MutableList<CollisionEvent>) {
        val mouthHalf = pitch.goalMouthWidth / 2f
        val mouthMin = pitch.halfWidth - mouthHalf
        val mouthMax = pitch.halfWidth + mouthHalf

        for (b in bodies) {
            val r = b.radius

            // Left / right side walls (always solid — no goals on the long edges).
            if (b.x < r) {
                b.x = r
                if (b.vx < 0f) { b.vx = -b.vx * pitch.wallRestitution; wallEvent(events, b) }
            } else if (b.x > pitch.width - r) {
                b.x = pitch.width - r
                if (b.vx > 0f) { b.vx = -b.vx * pitch.wallRestitution; wallEvent(events, b) }
            }

            // Short edges (y = 0 and y = height) carry the goal mouths — ball-specific handling.
            if (b.isBall) {
                handleGoalEdge(b, top = true, mouthMin, mouthMax, events)
                handleGoalEdge(b, top = false, mouthMin, mouthMax, events)
            } else {
                if (b.y < r) {
                    b.y = r
                    if (b.vy < 0f) { b.vy = -b.vy * pitch.wallRestitution; wallEvent(events, b) }
                } else if (b.y > pitch.height - r) {
                    b.y = pitch.height - r
                    if (b.vy > 0f) { b.vy = -b.vy * pitch.wallRestitution; wallEvent(events, b) }
                }
            }
        }
    }

    private fun handleGoalEdge(
        ball: MutableBody,
        top: Boolean,
        mouthMin: Float,
        mouthMax: Float,
        events: MutableList<CollisionEvent>,
    ) {
        val r = ball.radius
        val line = if (top) pitch.height else 0f
        val crossing = if (top) ball.y > pitch.height - r else ball.y < r
        if (!crossing) return

        val insideMouth = ball.x in mouthMin..mouthMax
        val movingIntoGoal = if (top) ball.vy > 0f else ball.vy < 0f

        if (insideMouth && ball.z < pitch.crossbarHeight) {
            // Passes under the bar. Goal once the whole ball is past the line.
            val fullyPast = if (top) ball.y - r > line else ball.y + r < line
            if (fullyPast) {
                events += CollisionEvent.Goal(if (top) Team.A else Team.B)
                // Freeze the ball in the net.
                ball.vx = 0f; ball.vy = 0f; ball.vz = 0f
            }
            return
        }

        if (insideMouth && movingIntoGoal) {
            // Arrived at/above the bar inside the mouth → crossbar clang.
            ball.y = if (top) pitch.height - r else r
            ball.vy = -ball.vy * CROSSBAR_RESTITUTION
            ball.vz = -abs(ball.vz) * CROSSBAR_RESTITUTION
            events += CollisionEvent.Crossbar
            return
        }

        // Outside the mouth: solid wall.
        if (top) {
            ball.y = pitch.height - r
            if (ball.vy > 0f) { ball.vy = -ball.vy * pitch.wallRestitution; wallEvent(events, ball) }
        } else {
            ball.y = r
            if (ball.vy < 0f) { ball.vy = -ball.vy * pitch.wallRestitution; wallEvent(events, ball) }
        }
    }

    private fun wallEvent(events: MutableList<CollisionEvent>, b: MutableBody) {
        val speed = sqrt(b.vx * b.vx + b.vy * b.vy)
        if (speed > WALL_EVENT_SPEED) events += CollisionEvent.WallBounce(BodyId(b.id), speed)
    }

    /** Deep copy for AI simulation (clone → simulate → score). */
    fun clone(): PhysicsWorld = PhysicsWorld(pitch).also { world ->
        world.setBodies(snapshot())
        // Carry pending flick flags so a simulated chip behaves like the real one.
        this.bodies.forEachIndexed { i, src -> world.bodies[i].pendingFlick = src.pendingFlick }
        world.ballIndex = ballIndex
    }

    companion object {
        const val FIXED_DT = 1f / 120f
        const val VZ_EPSILON = 6f
        const val CROSSBAR_RESTITUTION = 0.4f
        const val GROUND_LOFT_FALLBACK = 0.0f
        private const val WALL_EVENT_SPEED = 8f
        private const val DISC_EVENT_SPEED = 8f

        /** Frame-rate independent multiplicative damping: v *= e^(-rate*dt), clamped to [0,1]. */
        private fun dampingFactor(ratePerSec: Float, dt: Float): Float {
            val f = 1f - ratePerSec * dt
            return if (f < 0f) 0f else f
        }
    }
}
