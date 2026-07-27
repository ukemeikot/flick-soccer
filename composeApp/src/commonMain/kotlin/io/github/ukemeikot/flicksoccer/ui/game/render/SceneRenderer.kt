package io.github.ukemeikot.flicksoccer.ui.game.render

import io.github.ukemeikot.flicksoccer.domain.engine.FormationProvider
import io.github.ukemeikot.flicksoccer.domain.model.BodyKind
import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.domain.model.Team
import io.github.ukemeikot.flicksoccer.platform.gl.Gl
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared OpenGL frame orchestrator (§5). Owns the camera, procedural meshes, shader programs and
 * draw order; runs entirely on the GL thread. Depends only on the [Gl] interface — no platform
 * types. Model matrices are written into reused scratch arrays (no per-frame allocations).
 *
 * @param glslPreamble platform version/precision header prepended to every shader.
 */
class SceneRenderer(
    private val gl: Gl,
    private val pitch: PitchSpec = PitchSpec(),
    private val glslPreamble: String = "#version 330 core",
) {
    private class GlMesh(val vao: Int, val indexCount: Int)

    private val camera = Camera(pitch)
    private var initialized = false

    private var litProgram = 0
    private var unlitProgram = 0
    private var uLitViewProj = 0; private var uLitModel = 0
    private var uLitLightDir = 0; private var uLitColor = 0; private var uLitMode = 0
    private var uUnViewProj = 0; private var uUnModel = 0; private var uUnRgba = 0

    private var pitchMesh: GlMesh? = null
    private var discMesh: GlMesh? = null
    private var ballMesh: GlMesh? = null
    private var boxMesh: GlMesh? = null
    private var shadowMesh: GlMesh? = null

    private val scratch = FloatArray(16)
    private val staticSnapshot: RenderSnapshot = buildStaticSnapshot()

    // Constant directional light (points from surface toward the light).
    private val lightX = 0.35f; private val lightY = 0.45f; private val lightZ = 1.0f

    /** Build shaders/meshes/state once the context exists (also after Android context loss). */
    fun onSurfaceCreated() {
        litProgram = compile(Shaders.LIT_VERTEX, Shaders.LIT_FRAGMENT)
        unlitProgram = compile(Shaders.UNLIT_VERTEX, Shaders.UNLIT_FRAGMENT)

        uLitViewProj = gl.getUniformLocation(litProgram, "uViewProj")
        uLitModel = gl.getUniformLocation(litProgram, "uModel")
        uLitLightDir = gl.getUniformLocation(litProgram, "uLightDir")
        uLitColor = gl.getUniformLocation(litProgram, "uColor")
        uLitMode = gl.getUniformLocation(litProgram, "uMode")
        uUnViewProj = gl.getUniformLocation(unlitProgram, "uViewProj")
        uUnModel = gl.getUniformLocation(unlitProgram, "uModel")
        uUnRgba = gl.getUniformLocation(unlitProgram, "uRgba")

        pitchMesh = upload(Meshes.quad(1f, 1f))
        discMesh = upload(Meshes.cylinder(height = 1f, segments = 24))
        ballMesh = upload(Meshes.uvSphere(16, 24))
        boxMesh = upload(Meshes.box())
        shadowMesh = upload(Meshes.quad(1f, 1f))

        gl.enable(Gl.DEPTH_TEST)
        gl.depthFunc(Gl.LEQUAL)
        gl.disable(Gl.CULL_FACE) // small scene; skip winding pitfalls
        gl.blendFunc(Gl.SRC_ALPHA, Gl.ONE_MINUS_SRC_ALPHA)
        initialized = true
    }

    fun onSurfaceResized(width: Int, height: Int) {
        gl.viewport(0, 0, width, height)
        camera.update(width.toFloat() / height.coerceAtLeast(1).toFloat())
    }

    /** Convert a surface pixel to a pitch-plane world point for picking/aiming (delegates to camera). */
    fun unproject(pixelX: Float, pixelY: Float, w: Float, h: Float) = camera.unprojectToPitch(pixelX, pixelY, w, h)

    /** Draw one frame from an immutable [snapshot]; falls back to a static kickoff scene when null. */
    fun drawFrame(snapshot: RenderSnapshot?) {
        if (!initialized) return
        val snap = snapshot ?: staticSnapshot

        gl.clearColor(0.06f, 0.08f, 0.12f, 1f)
        gl.clear(Gl.COLOR_BUFFER_BIT or Gl.DEPTH_BUFFER_BIT)

        val vp = camera.viewProjection().m

        // --- Opaque: pitch ---
        gl.useProgram(litProgram)
        gl.uniformMatrix4fv(uLitViewProj, false, vp)
        gl.uniform3f(uLitLightDir, lightX, lightY, lightZ)

        drawLit(pitchMesh, Palettes.pitch.r, Palettes.pitch.g, Palettes.pitch.b, mode = 1) {
            model(pitch.halfWidth, pitch.halfHeight, 0f, pitch.width, pitch.height, 1f, 0f)
        }

        // --- Blended: blob shadows on the ground ---
        gl.enable(Gl.BLEND)
        gl.depthMask(false)
        gl.useProgram(unlitProgram)
        gl.uniformMatrix4fv(uUnViewProj, false, vp)
        for (b in snap.bodies) {
            val heightFade = if (b.kind == BodyKind.BALL) (1f - (b.z / 40f)).coerceIn(0.25f, 1f) else 1f
            val grow = if (b.kind == BodyKind.BALL) 1f + b.z / 30f else 1f
            gl.uniform4f(uUnRgba, 0f, 0f, 0f, 0.28f * heightFade)
            model(b.x, b.y, 0.05f, b.radius * 2.1f * grow, b.radius * 2.1f * grow, 1f, 0f)
            gl.uniformMatrix4fv(uUnModel, false, scratch)
            drawMesh(shadowMesh)
        }
        gl.depthMask(true)
        gl.disable(Gl.BLEND)

        // --- Opaque: goals, discs, ball ---
        gl.useProgram(litProgram)
        drawGoals()

        for (b in snap.bodies) {
            when (b.kind) {
                BodyKind.TEAM_A_DISC -> drawDisc(b, Palettes.teamA[0])
                BodyKind.TEAM_B_DISC -> drawDisc(b, Palettes.teamB[0])
                BodyKind.BALL -> drawBall(b)
            }
        }

        drawAim(snap, vp)
    }

    /** Dotted launch ray on the pitch, colored green→red by power (§5.3). */
    private fun drawAim(snap: RenderSnapshot, vp: FloatArray) {
        val aim = snap.aim ?: return
        val disc = snap.bodies.firstOrNull { it.id == aim.discId.value } ?: return
        val dl = kotlin.math.sqrt(aim.dragVector.x * aim.dragVector.x + aim.dragVector.y * aim.dragVector.y)
        if (dl < 1e-3f) return
        val dirX = -aim.dragVector.x / dl
        val dirY = -aim.dragVector.y / dl

        gl.enable(Gl.BLEND)
        gl.depthMask(false)
        gl.useProgram(unlitProgram)
        gl.uniformMatrix4fv(uUnViewProj, false, vp)
        val dots = 10
        val spacing = aim.power * 4.5f + 1.4f
        for (i in 1..dots) {
            val d = i * spacing
            gl.uniform4f(uUnRgba, aim.power, 1f - aim.power, 0.15f, 0.9f)
            model(disc.x + dirX * d, disc.y + dirY * d, 0.1f, 0.9f, 0.9f, 1f, 0f)
            gl.uniformMatrix4fv(uUnModel, false, scratch)
            drawMesh(shadowMesh)
        }
        gl.depthMask(true)
        gl.disable(Gl.BLEND)
    }

    private fun drawDisc(b: BodyTransform, mat: Material) {
        gl.uniform3f(uLitColor, mat.r, mat.g, mat.b)
        gl.uniform1i(uLitMode, 0)
        model(b.x, b.y, 0f, b.radius, b.radius, DISC_VISUAL_HEIGHT, 0f)
        gl.uniformMatrix4fv(uLitModel, false, scratch)
        drawMesh(discMesh)
    }

    private fun drawBall(b: BodyTransform) {
        gl.uniform3f(uLitColor, Palettes.ball.r, Palettes.ball.g, Palettes.ball.b)
        gl.uniform1i(uLitMode, 0)
        // Sphere center sits at z (ball bottom) + radius; z-spin is cosmetic.
        model(b.x, b.y, b.z + b.radius, b.radius, b.radius, b.radius, b.spin)
        gl.uniformMatrix4fv(uLitModel, false, scratch)
        drawMesh(ballMesh)
    }

    private fun drawGoals() {
        val mouthHalf = pitch.goalMouthWidth / 2f
        val postThk = 1.4f
        val barH = pitch.crossbarHeight
        gl.uniform3f(uLitColor, 0.95f, 0.95f, 0.97f)
        gl.uniform1i(uLitMode, 0)
        for (lineY in floatArrayOf(0f, pitch.height)) {
            // Two posts.
            drawBox(pitch.halfWidth - mouthHalf, lineY, barH / 2f, postThk, postThk, barH)
            drawBox(pitch.halfWidth + mouthHalf, lineY, barH / 2f, postThk, postThk, barH)
            // Crossbar.
            drawBox(pitch.halfWidth, lineY, barH, pitch.goalMouthWidth + postThk, postThk, postThk)
        }
    }

    private fun drawBox(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float) {
        model(x, y, z, sx, sy, sz, 0f)
        gl.uniformMatrix4fv(uLitModel, false, scratch)
        drawMesh(boxMesh)
    }

    private inline fun drawLit(mesh: GlMesh?, r: Float, g: Float, b: Float, mode: Int, setModel: () -> Unit) {
        gl.uniform3f(uLitColor, r, g, b)
        gl.uniform1i(uLitMode, mode)
        setModel()
        gl.uniformMatrix4fv(uLitModel, false, scratch)
        drawMesh(mesh)
    }

    private fun drawMesh(mesh: GlMesh?) {
        val m = mesh ?: return
        gl.bindVertexArray(m.vao)
        gl.drawElements(Gl.TRIANGLES, m.indexCount, Gl.UNSIGNED_INT, 0)
    }

    /** Write translate(t) * scale(s) * rotZ(rot) into [scratch] (column-major, no allocation). */
    private fun model(tx: Float, ty: Float, tz: Float, sx: Float, sy: Float, sz: Float, rot: Float) {
        val c = cos(rot); val s = sin(rot)
        val m = scratch
        m[0] = sx * c; m[1] = sy * s; m[2] = 0f; m[3] = 0f
        m[4] = -sx * s; m[5] = sy * c; m[6] = 0f; m[7] = 0f
        m[8] = 0f; m[9] = 0f; m[10] = sz; m[11] = 0f
        m[12] = tx; m[13] = ty; m[14] = tz; m[15] = 1f
    }

    private fun upload(mesh: MeshData): GlMesh {
        val vao = gl.genVertexArray()
        gl.bindVertexArray(vao)
        val vbo = gl.genBuffer()
        gl.bindBuffer(Gl.ARRAY_BUFFER, vbo)
        gl.bufferData(Gl.ARRAY_BUFFER, mesh.vertices, Gl.STATIC_DRAW)
        val ebo = gl.genBuffer()
        gl.bindBuffer(Gl.ELEMENT_ARRAY_BUFFER, ebo)
        gl.bufferDataInt(Gl.ELEMENT_ARRAY_BUFFER, mesh.indices, Gl.STATIC_DRAW)
        val strideBytes = MeshData.STRIDE_FLOATS * 4
        gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 3, Gl.FLOAT, false, strideBytes, 0)
        gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 3, Gl.FLOAT, false, strideBytes, 3 * 4)
        gl.enableVertexAttribArray(2); gl.vertexAttribPointer(2, 2, Gl.FLOAT, false, strideBytes, 6 * 4)
        gl.bindVertexArray(0)
        return GlMesh(vao, mesh.indexCount)
    }

    private fun compile(vsBody: String, fsBody: String): Int {
        val vs = gl.createShader(Gl.VERTEX_SHADER)
        gl.shaderSource(vs, Shaders.withPreamble(glslPreamble, vsBody))
        gl.compileShader(vs)
        check(gl.getShaderCompileStatus(vs)) { "Vertex shader compile failed: ${gl.getShaderInfoLog(vs)}" }
        val fs = gl.createShader(Gl.FRAGMENT_SHADER)
        gl.shaderSource(fs, Shaders.withPreamble(glslPreamble, fsBody))
        gl.compileShader(fs)
        check(gl.getShaderCompileStatus(fs)) { "Fragment shader compile failed: ${gl.getShaderInfoLog(fs)}" }
        val p = gl.createProgram()
        gl.attachShader(p, vs); gl.attachShader(p, fs); gl.linkProgram(p)
        check(gl.getProgramLinkStatus(p)) { "Program link failed: ${gl.getProgramInfoLog(p)}" }
        gl.deleteShader(vs); gl.deleteShader(fs)
        return p
    }

    private fun buildStaticSnapshot(): RenderSnapshot {
        val bodies = FormationProvider(pitch).kickoff(Team.A).map {
            BodyTransform(it.id.value, it.kind, it.position.x, it.position.y, it.z, it.radius, 0f)
        }
        return RenderSnapshot(bodies, aim = null, phase = io.github.ukemeikot.flicksoccer.domain.model.MatchPhase.AIMING, scoreFlashSeconds = 0f, goalCamPunchSeconds = 0f)
    }

    companion object {
        private const val DISC_VISUAL_HEIGHT = 1.6f
    }
}
