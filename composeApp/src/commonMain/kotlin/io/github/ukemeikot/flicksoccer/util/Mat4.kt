package io.github.ukemeikot.flicksoccer.util

import kotlin.math.tan

/**
 * A 4x4 column-major matrix (OpenGL convention) backed by a 16-element [FloatArray]. Column-major
 * means element (row r, col c) is at index c * 4 + r. Provides just what the camera/renderer needs.
 */
class Mat4(val m: FloatArray = identityArray()) {

    operator fun times(o: Mat4): Mat4 {
        val a = m
        val b = o.m
        val r = FloatArray(16)
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += a[k * 4 + row] * b[col * 4 + k]
                }
                r[col * 4 + row] = sum
            }
        }
        return Mat4(r)
    }

    /** Transform a point (w = 1); returns the perspective-divided (x, y, z). */
    fun transformPoint(v: Vec3): Vec3 {
        val x = m[0] * v.x + m[4] * v.y + m[8] * v.z + m[12]
        val y = m[1] * v.x + m[5] * v.y + m[9] * v.z + m[13]
        val z = m[2] * v.x + m[6] * v.y + m[10] * v.z + m[14]
        val w = m[3] * v.x + m[7] * v.y + m[11] * v.z + m[15]
        return if (w != 0f && w != 1f) Vec3(x / w, y / w, z / w) else Vec3(x, y, z)
    }

    companion object {
        fun identityArray() = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )

        fun identity() = Mat4()

        /** Right-handed perspective projection (OpenGL clip space, z in [-1, 1]). */
        fun perspective(fovYRadians: Float, aspect: Float, near: Float, far: Float): Mat4 {
            val f = 1f / tan(fovYRadians / 2f)
            val nf = 1f / (near - far)
            return Mat4(
                floatArrayOf(
                    f / aspect, 0f, 0f, 0f,
                    0f, f, 0f, 0f,
                    0f, 0f, (far + near) * nf, -1f,
                    0f, 0f, 2f * far * near * nf, 0f,
                ),
            )
        }

        /** Right-handed look-at view matrix. */
        fun lookAt(eye: Vec3, center: Vec3, up: Vec3): Mat4 {
            val fwd = (center - eye).normalized()
            val side = fwd.cross(up).normalized()
            val u = side.cross(fwd)
            return Mat4(
                floatArrayOf(
                    side.x, u.x, -fwd.x, 0f,
                    side.y, u.y, -fwd.y, 0f,
                    side.z, u.z, -fwd.z, 0f,
                    -side.dot(eye), -u.dot(eye), fwd.dot(eye), 1f,
                ),
            )
        }
    }
}
