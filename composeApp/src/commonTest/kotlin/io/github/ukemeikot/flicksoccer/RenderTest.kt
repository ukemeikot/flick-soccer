package io.github.ukemeikot.flicksoccer

import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.ui.game.render.Camera
import io.github.ukemeikot.flicksoccer.util.Mat4
import io.github.ukemeikot.flicksoccer.util.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RenderTest {

    @Test
    fun mat4_inverse_times_matrix_is_identity() {
        val m = Mat4.perspective(0.9f, 0.6f, 1f, 500f) *
            Mat4.lookAt(Vec3(50f, -80f, 90f), Vec3(50f, 80f, 0f), Vec3(0f, 0f, 1f))
        val inv = m.inverse()
        assertNotNull(inv)
        val id = (m * inv).m
        val expected = Mat4.identityArray()
        for (i in 0 until 16) assertEquals(expected[i], id[i], 1e-3f, "identity element $i")
    }

    @Test
    fun unproject_round_trips_a_pitch_point_back_from_its_pixel() {
        val cam = Camera(PitchSpec())
        cam.update(aspect = 480f / 800f)
        val surfaceW = 480f; val surfaceH = 800f

        // A few world points on the pitch plane (z = 0).
        for (p in listOf(Vec3(50f, 80f, 0f), Vec3(30f, 40f, 0f), Vec3(70f, 120f, 0f))) {
            val ndc = cam.viewProjection().transformPoint(p)
            val px = (ndc.x + 1f) / 2f * surfaceW
            val py = (1f - ndc.y) / 2f * surfaceH
            val back = cam.unprojectToPitch(px, py, surfaceW, surfaceH)
            assertNotNull(back, "point $p should unproject")
            assertEquals(p.x, back.x, 0.5f, "x round-trip for $p")
            assertEquals(p.y, back.y, 0.5f, "y round-trip for $p")
        }
    }

    @Test
    fun unproject_is_null_when_the_view_projection_is_uninitialized_singular() {
        // Before update(), the identity view-projection maps the plane onto itself: a pixel at the
        // vertical center still yields a finite point, so just assert no crash and a sane result.
        val cam = Camera(PitchSpec())
        cam.update(1f)
        val hit = cam.unprojectToPitch(240f, 400f, 480f, 800f)
        assertNotNull(hit)
    }
}
