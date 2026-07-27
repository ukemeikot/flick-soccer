package io.github.ukemeikot.flicksoccer.ui.game.render

import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.domain.model.Vec2
import io.github.ukemeikot.flicksoccer.util.Mat4
import io.github.ukemeikot.flicksoccer.util.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fixed perspective camera looking down the pitch at a tilt (§5.3). Tunable taste parameters live
 * here as constants for M7 playtesting. Also provides pointer unprojection onto the pitch plane
 * (z = 0) for 3D picking/aiming — fully exercised by the M2 camera test suite.
 */
class Camera(
    private val pitch: PitchSpec,
    var tiltDegrees: Float = TILT_DEGREES_DEFAULT,
    var fovDegrees: Float = 45f,
) {
    private var viewProj = Mat4.identity()

    /** Recompute view*projection for the current surface [aspect] (width / height). */
    fun update(aspect: Float) {
        val fov = (fovDegrees * PI / 180.0).toFloat()
        val tilt = (tiltDegrees * PI / 180.0).toFloat()

        // Position behind/above the near goal; distance adapts to aspect so the pitch fits.
        val distance = pitch.height * (0.62f + 0.20f / aspect.coerceAtLeast(0.4f))
        val height = distance * sin(tilt)
        val back = distance * cos(tilt)

        val eye = Vec3(pitch.halfWidth, -back + pitch.halfHeight * 0f, height)
        val center = Vec3(pitch.halfWidth, pitch.halfHeight, 0f)
        val up = Vec3(0f, 0f, 1f)

        val proj = Mat4.perspective(fov, aspect, 1f, distance * 3f)
        val view = Mat4.lookAt(eye, center, up)
        viewProj = proj * view
    }

    fun viewProjection(): Mat4 = viewProj

    /**
     * Unproject a surface pixel to the pitch plane (z = 0). Returns world (x, y) or null when the
     * ray is parallel to / points away from the plane. Implemented in **M2**.
     */
    fun unprojectToPitch(pixelX: Float, pixelY: Float, surfaceWidth: Float, surfaceHeight: Float): Vec2? {
        // TODO(M2): build a ray from the inverse view-projection through the NDC point and intersect z = 0.
        return null
    }

    companion object {
        const val TILT_DEGREES_DEFAULT = 35f
    }
}
