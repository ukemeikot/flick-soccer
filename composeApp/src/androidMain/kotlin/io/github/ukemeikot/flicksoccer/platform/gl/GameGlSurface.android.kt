package io.github.ukemeikot.flicksoccer.platform.gl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.ukemeikot.flicksoccer.ui.game.render.RenderSnapshot

/**
 * Android GL surface. **M3** replaces this placeholder with an `AndroidView { GLSurfaceView }`
 * (ES 3.0, RENDERMODE_CONTINUOUSLY) that runs the shared SceneRenderer and rebuilds resources on
 * context loss (§5.2, §9). For M0 it renders a Compose placeholder.
 */
@Composable
actual fun GameGlSurface(
    modifier: Modifier,
    snapshot: RenderSnapshot?,
    onPointer: (PointerEventGl) -> Unit,
) {
    GlSurfacePlaceholder(modifier, "Android OpenGL surface — pitch renders here (M3)")
}
