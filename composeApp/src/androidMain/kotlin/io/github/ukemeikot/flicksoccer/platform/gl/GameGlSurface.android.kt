package io.github.ukemeikot.flicksoccer.platform.gl

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.ui.game.render.RenderSnapshot
import io.github.ukemeikot.flicksoccer.ui.game.render.SceneRenderer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val MOBILE_PREAMBLE = "#version 300 es\nprecision highp float;"

/**
 * Android GL surface: a `GLSurfaceView` (ES 3.0, continuous) running the shared SceneRenderer.
 * On GL context loss the renderer is rebuilt in [GLSurfaceView.Renderer.onSurfaceCreated] — cheap,
 * since all meshes/shaders are procedural (§5.2, §9). Touches unproject to pitch coordinates.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
actual fun GameGlSurface(
    modifier: Modifier,
    snapshotProvider: () -> RenderSnapshot?,
    onPointer: (PointerEventGl) -> Unit,
) {
    val providerRef = remember { AtomicReference<() -> RenderSnapshot?> { null } }
    providerRef.set(snapshotProvider)
    val onPointerState = rememberUpdatedState(onPointer)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLSurfaceView(ctx).apply {
                setEGLContextClientVersion(3)
                val rendererRef = AtomicReference<SceneRenderer?>(null)

                setRenderer(object : GLSurfaceView.Renderer {
                    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
                        // Rebuilt on every context (re)creation, including resume after loss.
                        rendererRef.set(
                            SceneRenderer(GlesGl(), PitchSpec(), MOBILE_PREAMBLE).also { it.onSurfaceCreated() },
                        )
                    }
                    override fun onSurfaceChanged(unused: GL10?, w: Int, h: Int) {
                        rendererRef.get()?.onSurfaceResized(w, h)
                    }
                    override fun onDrawFrame(unused: GL10?) {
                        rendererRef.get()?.drawFrame(providerRef.get().invoke())
                    }
                })
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

                setOnTouchListener { view, e ->
                    val r = rendererRef.get() ?: return@setOnTouchListener false
                    val world = r.unproject(e.x, e.y, view.width.toFloat(), view.height.toFloat())
                        ?: return@setOnTouchListener true
                    val type = when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> PointerEventGl.Type.DOWN
                        MotionEvent.ACTION_MOVE -> PointerEventGl.Type.MOVE
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> PointerEventGl.Type.UP
                        else -> return@setOnTouchListener true
                    }
                    onPointerState.value(PointerEventGl(world.x, world.y, type))
                    true
                }
            }
        },
    )
}
