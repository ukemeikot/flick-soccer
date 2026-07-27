package io.github.ukemeikot.flicksoccer.platform.gl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.ukemeikot.flicksoccer.ui.game.render.RenderSnapshot

/**
 * Desktop GL surface. **M2** replaces this placeholder with an `AWTGLCanvas` (lwjgl3-awt) hosted in
 * a `SwingPanel`, running the [io.github.ukemeikot.flicksoccer.ui.game.render.SceneRenderer] on a
 * dedicated GL thread (§5.2). For M0 it renders a Compose placeholder so the app runs end-to-end.
 */
@Composable
actual fun GameGlSurface(
    modifier: Modifier,
    snapshot: RenderSnapshot?,
    onPointer: (PointerEventGl) -> Unit,
) {
    GlSurfacePlaceholder(modifier, "Desktop OpenGL surface — pitch renders here (M2)")
}
