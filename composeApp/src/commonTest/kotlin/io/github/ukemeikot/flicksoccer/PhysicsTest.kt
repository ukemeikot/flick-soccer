package io.github.ukemeikot.flicksoccer

import io.github.ukemeikot.flicksoccer.domain.model.Body
import io.github.ukemeikot.flicksoccer.domain.model.BodyId
import io.github.ukemeikot.flicksoccer.domain.model.BodyKind
import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.domain.model.Team
import io.github.ukemeikot.flicksoccer.domain.model.Vec2
import io.github.ukemeikot.flicksoccer.domain.physics.CollisionEvent
import io.github.ukemeikot.flicksoccer.domain.physics.FlickSpec
import io.github.ukemeikot.flicksoccer.domain.physics.PhysicsWorld
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PhysicsTest {

    private val noDamping = PitchSpec(linearDampingPerSec = 0f, airDampingPerSec = 0f)

    private fun disc(id: Int, x: Float, y: Float, vx: Float = 0f, vy: Float = 0f, r: Float = 3f, m: Float = 1f) =
        Body(BodyId(id), BodyKind.TEAM_A_DISC, Vec2(x, y), Vec2(vx, vy), 0f, 0f, r, m)

    private fun ball(id: Int, x: Float, y: Float, vx: Float = 0f, vy: Float = 0f, z: Float = 0f, vz: Float = 0f, r: Float = 2f, m: Float = 0.4f) =
        Body(BodyId(id), BodyKind.BALL, Vec2(x, y), Vec2(vx, vy), z, vz, r, m)

    // --- Collisions -------------------------------------------------------------------------

    @Test
    fun head_on_disc_collision_conserves_linear_momentum() {
        val w = PhysicsWorld(noDamping)
        w.setBodies(listOf(disc(0, 10f, 50f, vx = 20f), disc(1, 16f, 50f)))
        val before = 20f * 1f
        w.step()
        val a = w.snapshot()[0]; val b = w.snapshot()[1]
        val after = a.velocity.x * 1f + b.velocity.x * 1f
        assertEquals(before, after, 0.05f)
        assertTrue(a.velocity.x < b.velocity.x, "striker should slow, target should speed up")
    }

    @Test
    fun wall_bounce_reflects_with_restitution() {
        val w = PhysicsWorld(noDamping)
        // Just inside the right wall, heading into it.
        w.setBodies(listOf(disc(0, noDamping.width - 3f - 0.1f, 50f, vx = 30f)))
        repeat(3) { w.step() }
        val v = w.snapshot()[0].velocity.x
        assertTrue(v < 0f, "velocity should reverse off the wall")
        assertEquals(30f * noDamping.wallRestitution, abs(v), 0.5f)
    }

    @Test
    fun damping_brings_a_moving_disc_to_rest() {
        val w = PhysicsWorld() // default damping
        w.setBodies(listOf(disc(0, 50f, 80f, vx = 40f, vy = 10f)))
        repeat(800) { w.step() }
        assertTrue(w.isAtRest, "disc should sleep once damping bleeds off its speed")
    }

    // --- Goals & crossbar -------------------------------------------------------------------

    @Test
    fun ball_scores_through_the_mouth_under_the_crossbar() {
        val w = PhysicsWorld(noDamping)
        w.setBodies(listOf(ball(0, 50f, noDamping.height - 2f - 0.1f, vy = 30f, z = 0f)))
        val events = mutableListOf<CollisionEvent>()
        repeat(60) { events += w.step() }
        assertTrue(events.any { it is CollisionEvent.Goal && it.scoredBy == Team.A }, "should register a goal for A")
    }

    @Test
    fun a_disc_cannot_score_it_bounces_off_the_goal_edge() {
        val w = PhysicsWorld(noDamping)
        w.setBodies(listOf(disc(0, 50f, noDamping.height - 3f - 0.1f, vy = 30f)))
        val events = mutableListOf<CollisionEvent>()
        repeat(30) { events += w.step() }
        assertFalse(events.any { it is CollisionEvent.Goal }, "a disc must never score")
        assertTrue(w.snapshot()[0].velocity.y < 0f, "disc should bounce back off the short edge")
    }

    @Test
    fun high_ball_into_the_mouth_clangs_the_crossbar() {
        val w = PhysicsWorld(noDamping)
        w.setBodies(listOf(ball(0, 50f, noDamping.height - 2f - 0.1f, vy = 30f, z = 12f)))
        val events = mutableListOf<CollisionEvent>()
        repeat(5) { events += w.step() }
        assertTrue(events.any { it is CollisionEvent.Crossbar }, "z above the bar should clang")
        assertFalse(events.any { it is CollisionEvent.Goal }, "clanged shot is not a goal")
        assertTrue(w.snapshot()[0].velocity.y < 0f, "ball rebounds off the bar")
    }

    // --- 2.5D disc/ball interaction ---------------------------------------------------------

    @Test
    fun airborne_ball_flies_over_a_disc_but_a_grounded_ball_collides() {
        // Grounded: expect the disc to be knocked (a collision happened).
        val grounded = PhysicsWorld(noDamping)
        grounded.setBodies(listOf(disc(0, 50f, 80f), ball(1, 50f, 72f, vy = 30f, z = 0f)))
        var hitGrounded = false
        repeat(40) { grounded.step().forEach { if (it is CollisionEvent.DiscHitsBall) hitGrounded = true } }
        assertTrue(hitGrounded, "a grounded ball must collide with a disc in its path")

        // Airborne: ball clears the disc's radius while still above disc height, so it passes over.
        val air = PhysicsWorld(noDamping)
        air.setBodies(listOf(disc(0, 50f, 80f), ball(1, 50f, 72f, vy = 120f, z = 8f)))
        var hitAir = false
        repeat(40) { air.step().forEach { if (it is CollisionEvent.DiscHitsBall) hitAir = true } }
        assertFalse(hitAir, "an airborne ball (z >= discHeight) must sail over the disc")
        assertEquals(0f, air.snapshot()[0].velocity.length(), 1e-3f, "the disc it flew over must stay put")
    }

    @Test
    fun a_chip_lofts_the_ball_higher_than_a_ground_shot() {
        fun peakHeightAfterFlick(spec: FlickSpec): Float {
            val w = PhysicsWorld(noDamping)
            // Disc touching the ball, moving into it.
            w.setBodies(listOf(disc(0, 50f, 74f, vy = 100f, r = 3f), ball(1, 50f, 79f, r = 2f)))
            w.applyFlick(BodyId(0), Vec2(0f, 100f), spec)
            var peak = 0f
            repeat(120) { w.step(); peak = maxOf(peak, w.snapshot()[1].z) }
            return peak
        }
        val chipPeak = peakHeightAfterFlick(FlickSpec(loftFactor = 0.55f, planeSpeedPenalty = 0.8f))
        val groundPeak = peakHeightAfterFlick(FlickSpec(loftFactor = 0.05f, planeSpeedPenalty = 1f))
        assertTrue(chipPeak > 3f, "a chip should get the ball meaningfully airborne (got $chipPeak)")
        assertTrue(chipPeak > groundPeak * 3f, "chip ($chipPeak) should loft far higher than ground ($groundPeak)")
    }

    // --- Vertical dynamics ------------------------------------------------------------------

    @Test
    fun ground_bounce_loses_energy_per_restitution() {
        val w = PhysicsWorld(noDamping)
        w.setBodies(listOf(ball(0, 50f, 80f, z = 0.001f, vz = -40f)))
        val events = w.step()
        val v = w.snapshot()[0].velocity // plane unaffected
        val vz = w.snapshot()[0].vz
        assertTrue(vz > 0f, "ball should rebound upward")
        // Impact ≈ 40 (+ one dt of gravity); rebound ≈ impact * groundRestitution (0.55).
        assertTrue(vz in 20f..26f, "rebound vz should be ~impact*restitution, was $vz")
        assertTrue(events.any { it is CollisionEvent.GroundBounce }, "a landing emits a ground-bounce event")
        assertEquals(0f, v.length(), 1e-4f)
    }

    // --- Determinism ------------------------------------------------------------------------

    @Test
    fun simulation_is_deterministic() {
        fun run(): List<Body> {
            val w = PhysicsWorld(noDamping)
            w.setBodies(listOf(disc(0, 40f, 60f, vx = 25f, vy = 12f), disc(1, 55f, 66f), ball(2, 60f, 80f, z = 4f, vz = 3f)))
            w.applyFlick(BodyId(0), Vec2(25f, 12f), FlickSpec(0.55f, 0.8f))
            repeat(200) { w.step() }
            return w.snapshot()
        }
        val a = run(); val b = run()
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals(a[i].position.x, b[i].position.x, 0f, "x[$i] must match exactly")
            assertEquals(a[i].position.y, b[i].position.y, 0f, "y[$i] must match exactly")
            assertEquals(a[i].z, b[i].z, 0f, "z[$i] must match exactly")
        }
    }
}
