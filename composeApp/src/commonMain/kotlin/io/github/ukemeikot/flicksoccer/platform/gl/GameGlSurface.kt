package io.github.ukemeikot.flicksoccer.platform.gl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.ukemeikot.flicksoccer.ui.game.render.RenderSnapshot

/**
 * A pointer event whose [x]/[y] are **pitch-plane world coordinates** — the surface has already
 * unprojected the raw pixel through the camera (§5.4), so the ViewModel works purely in world units.
 */
data class PointerEventGl(
    val x: Float,
    val y: Float,
    val type: Type,
) {
    enum class Type { DOWN, MOVE, UP }
}

/**
 * Hosts the native GL surface and draws the latest [RenderSnapshot] on the platform's GL thread.
 * Android → GLSurfaceView, iOS → GLKView (placeholder until real host), Desktop → AWTGLCanvas in a
 * SwingPanel (§5.2). The GL thread reads the newest frame each draw via [snapshotProvider] — an
 * atomic-style read that avoids per-frame recomposition.
 */
@Composable
expect fun GameGlSurface(
    modifier: Modifier,
    snapshotProvider: () -> RenderSnapshot?,
    onPointer: (PointerEventGl) -> Unit,
)
