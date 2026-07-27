package io.github.ukemeikot.flicksoccer.platform.gl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.ukemeikot.flicksoccer.ui.game.render.RenderSnapshot

/** A pointer event in surface pixels, forwarded from the native GL view to the ViewModel. */
data class PointerEventGl(
    val x: Float,
    val y: Float,
    val type: Type,
) {
    enum class Type { DOWN, MOVE, UP }
}

/**
 * Hosts the native GL surface and draws the latest [RenderSnapshot] on the platform's GL thread.
 * Android → GLSurfaceView, iOS → GLKView, Desktop → AWTGLCanvas in a SwingPanel (§5.2).
 *
 * **M0:** actuals render a lightweight Compose placeholder so the app runs end-to-end; the real
 * OpenGL SceneRenderer is wired in M2 (desktop) and M3 (Android/iOS).
 */
@Composable
expect fun GameGlSurface(
    modifier: Modifier,
    snapshot: RenderSnapshot?,
    onPointer: (PointerEventGl) -> Unit,
)
