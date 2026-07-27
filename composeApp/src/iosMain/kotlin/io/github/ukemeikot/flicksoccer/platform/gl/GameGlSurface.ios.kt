package io.github.ukemeikot.flicksoccer.platform.gl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.ukemeikot.flicksoccer.ui.game.render.RenderSnapshot

/**
 * iOS GL surface. **M3** replaces this placeholder with a `UIKitView { GLKView }` driven by a
 * `CADisplayLink` on an ES 3.0 `EAGLContext` (§5.2). OpenGL ES is deprecated by Apple — see the
 * risks section of IMPLEMENTATION_PLAN.md. For M0 it renders a Compose placeholder.
 */
@Composable
actual fun GameGlSurface(
    modifier: Modifier,
    snapshotProvider: () -> RenderSnapshot?,
    onPointer: (PointerEventGl) -> Unit,
) {
    GlSurfacePlaceholder(modifier, "iOS OpenGL surface — pitch renders here (M3)")
}
