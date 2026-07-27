package io.github.ukemeikot.flicksoccer

import io.github.ukemeikot.flicksoccer.domain.engine.Rules
import io.github.ukemeikot.flicksoccer.domain.model.Body
import io.github.ukemeikot.flicksoccer.domain.model.BodyId
import io.github.ukemeikot.flicksoccer.domain.model.BodyKind
import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.domain.model.ShotType
import io.github.ukemeikot.flicksoccer.domain.model.Vec2
import io.github.ukemeikot.flicksoccer.domain.physics.CollisionEvent
import io.github.ukemeikot.flicksoccer.domain.physics.PhysicsWorld
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Regression tests for the §4.3 tuning targets, so future constant changes can't silently break the
 * feel: a chip must clear an interposed disc and come back down; a full-power ground shot from
 * midfield must reach the goal.
 */
class TuningTest {

    private val pitch = PitchSpec()

    private fun disc(id: Int, x: Float, y: Float) =
        Body(BodyId(id), BodyKind.TEAM_A_DISC, Vec2(x, y), Vec2.ZERO, 0f, 0f, radius = 3.2f, mass = 1.2f)

    private fun ball(id: Int, x: Float, y: Float) =
        Body(BodyId(id), BodyKind.BALL, Vec2(x, y), Vec2.ZERO, 0f, 0f, radius = 2f, mass = 0.4f)

    private class SimOutcome(val peakZ: Float, val endBall: Body, val goal: Boolean)

    private fun simulate(bodies: List<Body>, discId: Int, power: Float, shot: ShotType, dir: Vec2): SimOutcome {
        val world = PhysicsWorld(pitch)
        world.setBodies(bodies)
        val drag = Vec2(-dir.x, -dir.y) * (power * Rules.MAX_DRAG_LEN)
        world.applyFlick(BodyId(discId), Rules.launchVelocity(drag), Rules.flickSpec(shot))
        var peakZ = 0f
        var goal = false
        var steps = 0
        while (steps < 600 && !world.isAtRest) {
            for (e in world.step()) if (e is CollisionEvent.Goal) goal = true
            val b = world.snapshot().first { it.kind == BodyKind.BALL }
            peakZ = maxOf(peakZ, b.z)
            if (goal) break
            steps++
        }
        return SimOutcome(peakZ, world.snapshot().first { it.kind == BodyKind.BALL }, goal)
    }

    @Test
    fun a_chip_clears_an_interposed_disc_and_lands() {
        val defenderY = 74f
        val bodies = listOf(
            disc(0, pitch.halfWidth, 58f),          // striker
            ball(1, pitch.halfWidth, 66f),          // ball just ahead
            disc(2, pitch.halfWidth, defenderY),    // interposed defender
        )
        val out = simulate(bodies, discId = 0, power = 0.85f, shot = ShotType.CHIP, dir = Vec2(0f, 1f))

        assertTrue(out.peakZ > pitch.discHeight, "chip should loft the ball above disc height (peak ${out.peakZ})")
        assertTrue(out.endBall.position.y > defenderY + 3.2f, "ball should end past the defender (y=${out.endBall.position.y})")
        assertTrue(out.endBall.z == 0f, "ball should come back down to the ground")
    }

    @Test
    fun a_full_power_ground_shot_from_midfield_reaches_goal() {
        val bodies = listOf(
            disc(0, pitch.halfWidth, 76f),
            ball(1, pitch.halfWidth, 82f),
        )
        val out = simulate(bodies, discId = 0, power = 1.0f, shot = ShotType.GROUND, dir = Vec2(0f, 1f))
        assertTrue(out.goal || out.endBall.position.y >= 150f, "ground shot should reach the goal (goal=${out.goal}, y=${out.endBall.position.y})")
    }
}
