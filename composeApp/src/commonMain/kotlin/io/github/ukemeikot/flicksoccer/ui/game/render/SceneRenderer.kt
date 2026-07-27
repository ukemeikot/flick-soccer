package io.github.ukemeikot.flicksoccer.ui.game.render

import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.platform.gl.Gl

/**
 * Shared OpenGL frame orchestrator (§5). Owns the camera, procedural meshes, shader programs and
 * draw order; runs entirely on the GL thread. Depends only on the [Gl] interface — no platform
 * types. Constructed by each platform's GL surface host. Body is implemented across **M2/M3**.
 */
class SceneRenderer(
    private val gl: Gl,
    private val pitch: PitchSpec = PitchSpec(),
) {
    private val camera = Camera(pitch)
    private var initialized = false

    /** Build shaders, meshes and GL state once the context exists (also after Android context loss). */
    fun onSurfaceCreated() {
        // TODO(M2): compile programs, upload mesh VAOs, set clear color / depth state.
        initialized = true
    }

    fun onSurfaceResized(width: Int, height: Int) {
        gl.viewport(0, 0, width, height)
        camera.update(width.toFloat() / height.coerceAtLeast(1).toFloat())
    }

    /** Draw one frame from an immutable [snapshot]. Opaque pass, then blended (shadows, aim, net). */
    fun drawFrame(snapshot: RenderSnapshot?) {
        gl.clearColor(0.05f, 0.06f, 0.09f, 1f)
        gl.clear(Gl.COLOR_BUFFER_BIT or Gl.DEPTH_BUFFER_BIT)
        // TODO(M2/M3): draw pitch, discs, goals, ball, then blob shadows + aim overlay from snapshot.
    }
}
