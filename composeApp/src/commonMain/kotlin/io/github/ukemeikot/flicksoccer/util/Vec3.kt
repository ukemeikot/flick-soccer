package io.github.ukemeikot.flicksoccer.util

import kotlin.math.sqrt

/** A minimal 3D vector for the renderer/camera math. World units. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)

    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    fun length() = sqrt(dot(this))
    fun normalized(): Vec3 {
        val len = length()
        return if (len <= 1e-6f) ZERO else Vec3(x / len, y / len, z / len)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }
}
