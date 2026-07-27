package io.github.ukemeikot.flicksoccer.platform.gl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.ui.game.render.RenderSnapshot
import io.github.ukemeikot.flicksoccer.ui.game.render.SceneRenderer
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.awt.AWTGLCanvas
import org.lwjgl.opengl.awt.GLData
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Desktop GL surface: an `AWTGLCanvas` (lwjgl3-awt) hosted in a `SwingPanel`, rendered on a
 * dedicated GL thread that reads the latest [RenderSnapshot] from an atomic reference (§5.2). Mouse
 * events are unprojected to pitch-plane coordinates before reaching the ViewModel.
 */
@Composable
actual fun GameGlSurface(
    modifier: Modifier,
    snapshotProvider: () -> RenderSnapshot?,
    onPointer: (PointerEventGl) -> Unit,
) {
    val providerRef = remember { AtomicReference<() -> RenderSnapshot?> { null } }
    providerRef.set(snapshotProvider)
    val onPointerState = rememberUpdatedState(onPointer)
    val running = remember { AtomicBoolean(true) }

    DisposableEffect(Unit) { onDispose { running.set(false) } }

    SwingPanel(
        background = Color.Black,
        modifier = modifier,
        factory = {
            val data = GLData().apply {
                majorVersion = 3
                minorVersion = 3
                profile = GLData.Profile.CORE
                forwardCompatible = true
            }
            val canvas = object : AWTGLCanvas(data) {
                @Volatile var renderer: SceneRenderer? = null
                private var painted = false
                override fun initGL() {
                    GL.createCapabilities()
                    renderer = SceneRenderer(LwjglGl(), PitchSpec(), "#version 330 core")
                        .also { it.onSurfaceCreated() }
                }
                override fun paintGL() {
                    val r = renderer ?: return
                    if (!painted) painted = true
                    r.onSurfaceResized(width.coerceAtLeast(1), height.coerceAtLeast(1))
                    r.drawFrame(providerRef.get().invoke())
                    swapBuffers()
                }
            }

            val mouse = object : MouseAdapter() {
                private fun emit(e: MouseEvent, type: PointerEventGl.Type) {
                    val r = canvas.renderer ?: return
                    val w = canvas.width.toFloat(); val h = canvas.height.toFloat()
                    val world = r.unproject(e.x.toFloat(), e.y.toFloat(), w, h) ?: return
                    onPointerState.value(PointerEventGl(world.x, world.y, type))
                }
                override fun mousePressed(e: MouseEvent) = emit(e, PointerEventGl.Type.DOWN)
                override fun mouseDragged(e: MouseEvent) = emit(e, PointerEventGl.Type.MOVE)
                override fun mouseReleased(e: MouseEvent) = emit(e, PointerEventGl.Type.UP)
            }
            canvas.addMouseListener(mouse)
            canvas.addMouseMotionListener(mouse)

            val reportedError = AtomicBoolean(false)
            Thread {
                while (running.get()) {
                    try {
                        if (canvas.isValid) canvas.render()
                    } catch (t: Throwable) {
                        // Transient AWT/context states are normal early on; surface the first real
                        // error (e.g. shader compile) once so a blank canvas isn't silent.
                        if (reportedError.compareAndSet(false, true)) {
                            System.err.println("[flick-gl] render error: ${t.message}")
                            t.printStackTrace()
                        }
                    }
                    Thread.sleep(15)
                }
            }.apply { isDaemon = true; name = "flick-gl" }.start()

            canvas
        },
    )
}
