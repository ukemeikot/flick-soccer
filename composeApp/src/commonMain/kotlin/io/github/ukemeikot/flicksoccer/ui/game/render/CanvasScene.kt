package io.github.ukemeikot.flicksoccer.ui.game.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import io.github.ukemeikot.flicksoccer.domain.model.BodyKind
import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.platform.gl.PointerEventGl
import io.github.ukemeikot.flicksoccer.util.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Projected 2.5D renderer drawn with Compose Canvas. It runs entirely in `commonMain` — no native
 * GL surface, no platform interop — so the match is visible and identical on Desktop, Android and
 * iOS. The 3D look comes from projecting world points through the same perspective [Camera] used by
 * the OpenGL path; bodies are drawn as depth-sorted 2D primitives with blob shadows for height.
 */
@Composable
fun GameCanvasScene(
    modifier: Modifier,
    snapshotProvider: () -> RenderSnapshot?,
    onPointer: (PointerEventGl) -> Unit,
    paletteIndex: Int = 0,
    pitch: PitchSpec = PitchSpec(),
) {
    val camera = remember(pitch) { Camera(pitch) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var snap by remember { mutableStateOf<RenderSnapshot?>(null) }

    // Pull the latest frame from the game loop at the display refresh rate.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            snap = snapshotProvider()
        }
    }

    fun emit(pos: Offset, type: PointerEventGl.Type) {
        val w = canvasSize.width.toFloat(); val h = canvasSize.height.toFloat()
        if (w <= 0f || h <= 0f) return
        camera.update(w / h)
        val world = camera.unprojectToPitch(pos.x, pos.y, w, h) ?: return
        onPointer(PointerEventGl(world.x, world.y, type))
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                var lastPos = Offset.Zero
                detectDragGestures(
                    onDragStart = { lastPos = it; emit(it, PointerEventGl.Type.DOWN) },
                    onDrag = { change, _ -> lastPos = change.position; emit(change.position, PointerEventGl.Type.MOVE) },
                    onDragEnd = { emit(lastPos, PointerEventGl.Type.UP) },
                    onDragCancel = { emit(lastPos, PointerEventGl.Type.UP) },
                )
            },
    ) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        camera.update(size.width / size.height)
        drawScene(camera, pitch, snap, paletteIndex)
    }
}

private fun Material.toColor() = Color(r, g, b)

private val grassLight = Color(0xFF2E9E43)
private val grassDark = Color(0xFF268B3A)
private val lineColor = Color(0xFFEAF3EC)
private val ballColor = Color(0xFFF5F5F5)

private fun DrawScope.projectPoint(camera: Camera, x: Float, y: Float, z: Float): Offset {
    val ndc = camera.viewProjection().transformPoint(Vec3(x, y, z))
    return Offset((ndc.x * 0.5f + 0.5f) * size.width, (1f - (ndc.y * 0.5f + 0.5f)) * size.height)
}

private fun DrawScope.projectedRadius(camera: Camera, x: Float, y: Float, z: Float, r: Float): Float {
    val c = projectPoint(camera, x, y, z)
    val e = projectPoint(camera, x + r, y, z)
    return (c - e).getDistance().coerceAtLeast(1.5f)
}

private fun DrawScope.drawScene(camera: Camera, pitch: PitchSpec, snap: RenderSnapshot?, paletteIndex: Int) {
    // Sky/backdrop.
    drawRect(Color(0xFF0E1A12), size = size)

    drawPitch(camera, pitch)
    drawMarkings(camera, pitch)
    drawGoals(camera, pitch)

    val idx = paletteIndex.coerceIn(0, Palettes.teamA.size - 1)
    val teamAColor = Palettes.teamA[idx].toColor()
    val teamBColor = Palettes.teamB[idx].toColor()

    val bodies = snap?.bodies ?: return
    // Painter's algorithm: far (larger y) first.
    val sorted = bodies.sortedByDescending { it.y }
    for (b in sorted) {
        // Blob shadow at ground projection.
        val shadowR = projectedRadius(camera, b.x, b.y, 0f, b.radius * 1.1f)
        val shadowC = projectPoint(camera, b.x, b.y, 0f)
        val heightFade = if (b.kind == BodyKind.BALL) (0.35f - b.z / 120f).coerceIn(0.08f, 0.35f) else 0.32f
        drawOval(
            color = Color.Black.copy(alpha = heightFade),
            topLeft = Offset(shadowC.x - shadowR, shadowC.y - shadowR * 0.55f),
            size = androidx.compose.ui.geometry.Size(shadowR * 2f, shadowR * 1.1f),
        )
        when (b.kind) {
            BodyKind.TEAM_A_DISC -> drawDisc(camera, b, teamAColor)
            BodyKind.TEAM_B_DISC -> drawDisc(camera, b, teamBColor)
            BodyKind.BALL -> drawBall(camera, b)
        }
    }
    drawAim(camera, snap)
}

private fun DrawScope.drawPitch(camera: Camera, pitch: PitchSpec) {
    val stripes = 8
    for (i in 0 until stripes) {
        val y0 = pitch.height * i / stripes
        val y1 = pitch.height * (i + 1) / stripes
        val path = Path().apply {
            val a = projectPoint(camera, 0f, y0, 0f)
            val b = projectPoint(camera, pitch.width, y0, 0f)
            val c = projectPoint(camera, pitch.width, y1, 0f)
            val d = projectPoint(camera, 0f, y1, 0f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close()
        }
        drawPath(path, if (i % 2 == 0) grassLight else grassDark)
    }
}

private fun DrawScope.drawMarkings(camera: Camera, pitch: PitchSpec) {
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, w: Float = 2f) =
        drawLine(lineColor, projectPoint(camera, x0, y0, 0f), projectPoint(camera, x1, y1, 0f), strokeWidth = w)

    // Border.
    line(0f, 0f, pitch.width, 0f); line(pitch.width, 0f, pitch.width, pitch.height)
    line(pitch.width, pitch.height, 0f, pitch.height); line(0f, pitch.height, 0f, 0f)
    // Halfway line.
    line(0f, pitch.halfHeight, pitch.width, pitch.halfHeight)
    // Center circle.
    val cx = pitch.halfWidth; val cy = pitch.halfHeight; val r = 12f
    val path = Path()
    val segs = 32
    for (i in 0..segs) {
        val a = 2f * PI.toFloat() * i / segs
        val p = projectPoint(camera, cx + r * cos(a), cy + r * sin(a), 0f)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
}

private fun DrawScope.drawGoals(camera: Camera, pitch: PitchSpec) {
    val mouthHalf = pitch.goalMouthWidth / 2f
    val h = pitch.crossbarHeight
    for (lineY in floatArrayOf(0f, pitch.height)) {
        val lx = pitch.halfWidth - mouthHalf
        val rx = pitch.halfWidth + mouthHalf
        // Posts.
        drawLine(Color.White, projectPoint(camera, lx, lineY, 0f), projectPoint(camera, lx, lineY, h), strokeWidth = 4f)
        drawLine(Color.White, projectPoint(camera, rx, lineY, 0f), projectPoint(camera, rx, lineY, h), strokeWidth = 4f)
        // Crossbar.
        drawLine(Color.White, projectPoint(camera, lx, lineY, h), projectPoint(camera, rx, lineY, h), strokeWidth = 4f)
    }
}

private fun DrawScope.drawDisc(camera: Camera, b: BodyTransform, color: Color) {
    val center = projectPoint(camera, b.x, b.y, 0f)
    val r = projectedRadius(camera, b.x, b.y, 0f, b.radius)
    drawCircle(color, radius = r, center = center)
    drawCircle(color.copy(alpha = 1f).darken(), radius = r, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
    drawCircle(Color.White.copy(alpha = 0.25f), radius = r * 0.5f, center = Offset(center.x - r * 0.25f, center.y - r * 0.25f))
}

private fun DrawScope.drawBall(camera: Camera, b: BodyTransform) {
    val center = projectPoint(camera, b.x, b.y, b.z + b.radius)
    val r = projectedRadius(camera, b.x, b.y, b.z + b.radius, b.radius)
    drawCircle(ballColor, radius = r, center = center)
    drawCircle(Color(0xFF222222), radius = r, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
    // A couple of pentagon-ish spots so spin/height reads.
    drawCircle(Color(0xFF333333), radius = r * 0.28f, center = Offset(center.x + r * 0.2f * cos(b.spin), center.y + r * 0.2f * sin(b.spin)))
}

private fun DrawScope.drawAim(camera: Camera, snap: RenderSnapshot) {
    val aim = snap.aim ?: return
    val disc = snap.bodies.firstOrNull { it.id == aim.discId.value } ?: return
    val dl = kotlin.math.sqrt(aim.dragVector.x * aim.dragVector.x + aim.dragVector.y * aim.dragVector.y)
    if (dl < 1e-3f) return
    val dirX = -aim.dragVector.x / dl
    val dirY = -aim.dragVector.y / dl
    val col = Color(aim.power, 1f - aim.power, 0.15f, 0.9f)
    val spacing = aim.power * 4.5f + 1.6f
    for (i in 1..10) {
        val d = i * spacing
        val p = projectPoint(camera, disc.x + dirX * d, disc.y + dirY * d, 0.2f)
        drawCircle(col, radius = 4f, center = p)
    }
}

private fun Color.darken(): Color = Color(red * 0.6f, green * 0.6f, blue * 0.6f, alpha)
