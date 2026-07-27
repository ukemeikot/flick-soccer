package io.github.ukemeikot.flicksoccer

import io.github.ukemeikot.flicksoccer.domain.engine.FormationProvider
import io.github.ukemeikot.flicksoccer.domain.model.BodyKind
import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.domain.model.Team
import io.github.ukemeikot.flicksoccer.ui.game.render.Camera
import io.github.ukemeikot.flicksoccer.util.Vec3
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Objective check that the Canvas 2.5D scene is actually visible: the pitch center and every
 * kickoff body must project inside the viewport. Guards against the "blank screen" failure mode.
 */
class SceneProjectionTest {

    private fun screen(cam: Camera, w: Float, h: Float, x: Float, y: Float, z: Float): Pair<Float, Float> {
        val n = cam.viewProjection().transformPoint(Vec3(x, y, z))
        return (n.x * 0.5f + 0.5f) * w to (1f - (n.y * 0.5f + 0.5f)) * h
    }

    @Test
    fun kickoff_scene_projects_inside_the_viewport() {
        val pitch = PitchSpec()
        for (aspect in listOf(480f / 800f, 1f, 1.6f)) {
            val w = 800f * aspect; val h = 800f
            val cam = Camera(pitch)
            cam.update(aspect)

            val (cx, cy) = screen(cam, w, h, pitch.halfWidth, pitch.halfHeight, 0f)
            assertTrue(cx in 0f..w, "center x $cx off-screen at aspect $aspect (w=$w)")
            assertTrue(cy in 0f..h, "center y $cy off-screen at aspect $aspect")

            for (b in FormationProvider(pitch).kickoff(Team.A)) {
                val (sx, sy) = screen(cam, w, h, b.position.x, b.position.y, 0f)
                // Allow a small overscan margin; bodies must be essentially on-screen.
                assertTrue(sx in -0.1f * w..1.1f * w, "body ${b.kind} x=$sx off-screen at aspect $aspect")
                assertTrue(sy in -0.1f * h..1.1f * h, "body ${b.kind} y=$sy off-screen at aspect $aspect")
            }
        }
    }

    @Test
    fun airborne_ball_projects_higher_on_screen_than_its_ground_shadow() {
        val pitch = PitchSpec()
        val cam = Camera(pitch); cam.update(0.6f)
        val (_, groundY) = screen(cam, 480f, 800f, pitch.halfWidth, pitch.halfHeight, 0f)
        val (_, airY) = screen(cam, 480f, 800f, pitch.halfWidth, pitch.halfHeight, 20f)
        // Screen y grows downward, so a lofted ball must have a smaller y than its shadow.
        assertTrue(airY < groundY, "lofted ball ($airY) should render above its shadow ($groundY)")
        assertTrue(BodyKind.BALL == BodyKind.BALL)
    }
}
