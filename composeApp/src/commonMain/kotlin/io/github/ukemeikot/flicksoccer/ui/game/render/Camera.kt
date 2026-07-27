package io.github.ukemeikot.flicksoccer.ui.game.render

import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.domain.model.Vec2
import io.github.ukemeikot.flicksoccer.util.Mat4
import io.github.ukemeikot.flicksoccer.util.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fixed perspective camera looking down the pitch at a tilt (§5.3). Tunable taste parameters are
 * constants here for M7 playtesting. Provides pointer unprojection onto the pitch plane (z = 0) for
 * 3D picking/aiming.
 */
class Camera(
    private val pitch: PitchSpec,
    var tiltDegrees: Float = TILT_DEGREES_DEFAULT,
    var fovDegrees: Float = 45f,
) {
    private var viewProj = Mat4.identity()
    private var invViewProj: Mat4? = null
    var eye: Vec3 = Vec3(pitch.halfWidth, -pitch.height, pitch.height)
        private set

    /** Recompute view*projection for the current surface [aspect] (width / height). */
    fun update(aspect: Float) {
        val safeAspect = aspect.coerceIn(0.4f, 3f)
        val fov = (fovDegrees * PI / 180.0).toFloat()
        val tilt = (tiltDegrees * PI / 180.0).toFloat()

        // Distance so the whole pitch fits; taller/narrower viewports pull the camera back.
        val distance = pitch.height * (0.95f + 0.28f / safeAspect)
        eye = Vec3(
            pitch.halfWidth,
            pitch.halfHeight - cos(tilt) * distance,
            sin(tilt) * distance,
        )
        val center = Vec3(pitch.halfWidth, pitch.halfHeight, 0f)
        val up = Vec3(0f, 0f, 1f)

        val proj = Mat4.perspective(fov, safeAspect, 1f, distance * 3f)
        val view = Mat4.lookAt(eye, center, up)
        viewProj = proj * view
        invViewProj = viewProj.inverse()
    }

    fun viewProjection(): Mat4 = viewProj

    /**
     * Unproject a surface pixel to the pitch plane (z = 0). Returns world (x, y) or null when the
     * ray is parallel to the plane. Pixel origin is top-left (y down), matching pointer events.
     */
    fun unprojectToPitch(pixelX: Float, pixelY: Float, surfaceWidth: Float, surfaceHeight: Float): Vec2? {
        val inv = invViewProj ?: return null
        if (surfaceWidth <= 0f || surfaceHeight <= 0f) return null

        val ndcX = 2f * pixelX / surfaceWidth - 1f
        val ndcY = 1f - 2f * pixelY / surfaceHeight
        val near = inv.transformPoint(Vec3(ndcX, ndcY, -1f))
        val far = inv.transformPoint(Vec3(ndcX, ndcY, 1f))

        val dir = far - near
        if (kotlin.math.abs(dir.z) < 1e-6f) return null
        val t = -near.z / dir.z
        if (t < 0f) return null
        val hit = near + dir * t
        return Vec2(hit.x, hit.y)
    }

    companion object {
        const val TILT_DEGREES_DEFAULT = 35f
    }
}
