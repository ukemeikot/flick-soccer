package io.github.ukemeikot.flicksoccer

import io.github.ukemeikot.flicksoccer.domain.model.Vec2
import io.github.ukemeikot.flicksoccer.util.Mat4
import io.github.ukemeikot.flicksoccer.util.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MathTest {

    @Test
    fun vec2_arithmetic_and_length() {
        val a = Vec2(3f, 4f)
        assertEquals(5f, a.length(), 1e-4f)
        assertEquals(Vec2(4f, 6f), a + Vec2(1f, 2f))
        assertEquals(Vec2(2f, 2f), a - Vec2(1f, 2f))
        assertEquals(Vec2(6f, 8f), a * 2f)
    }

    @Test
    fun vec2_normalized_zero_is_safe() {
        assertEquals(Vec2.ZERO, Vec2.ZERO.normalizedOrZero())
        val n = Vec2(0f, 5f).normalizedOrZero()
        assertEquals(1f, n.length(), 1e-4f)
    }

    @Test
    fun mat4_identity_times_identity_is_identity() {
        val i = Mat4.identity() * Mat4.identity()
        Mat4.identityArray().forEachIndexed { idx, v -> assertEquals(v, i.m[idx], 1e-6f) }
    }

    @Test
    fun mat4_lookat_maps_center_in_front_of_camera() {
        // Camera at (0,0,10) looking at origin: the target should land on the -Z axis in view space.
        val view = Mat4.lookAt(Vec3(0f, 0f, 10f), Vec3.ZERO, Vec3(0f, 1f, 0f))
        val p = view.transformPoint(Vec3.ZERO)
        assertTrue(p.z < 0f, "target should be in front of the camera (negative view-space z)")
    }
}
