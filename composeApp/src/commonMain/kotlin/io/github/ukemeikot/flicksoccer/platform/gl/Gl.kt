package io.github.ukemeikot.flicksoccer.platform.gl

/**
 * The entire GL surface the shared renderer is allowed to touch — an ES 3.0 feature subset. Each
 * platform implements this with one-line delegations (Android GLES30, iOS GLES cinterop, Desktop
 * LWJGL GL30). **No GL types leak past this interface**: handles are plain [Int]s and data is
 * [FloatArray] / [IntArray]. See §5.1 of the design brief.
 *
 * Implementations are wired in **M2** (desktop) and **M3** (Android/iOS). The renderer in
 * `ui/game/render/` depends only on this interface.
 */
interface Gl {
    // --- Shaders & programs ---
    fun createShader(type: Int): Int
    fun shaderSource(shader: Int, source: String)
    fun compileShader(shader: Int)
    fun getShaderCompileStatus(shader: Int): Boolean
    fun getShaderInfoLog(shader: Int): String
    fun deleteShader(shader: Int)
    fun createProgram(): Int
    fun attachShader(program: Int, shader: Int)
    fun linkProgram(program: Int)
    fun getProgramLinkStatus(program: Int): Boolean
    fun getProgramInfoLog(program: Int): String
    fun useProgram(program: Int)

    // --- Buffers & vertex arrays ---
    fun genBuffer(): Int
    fun bindBuffer(target: Int, buffer: Int)
    fun bufferData(target: Int, data: FloatArray, usage: Int)
    fun bufferDataInt(target: Int, data: IntArray, usage: Int)
    fun genVertexArray(): Int
    fun bindVertexArray(vao: Int)
    fun enableVertexAttribArray(index: Int)
    fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int)

    // --- Textures ---
    fun genTexture(): Int
    fun bindTexture(target: Int, texture: Int)
    fun texParameteri(target: Int, pname: Int, param: Int)
    fun texImage2D(target: Int, level: Int, internalFormat: Int, width: Int, height: Int, format: Int, type: Int, pixels: ByteArray?)
    fun activeTexture(unit: Int)

    // --- Uniforms ---
    fun getUniformLocation(program: Int, name: String): Int
    fun getAttribLocation(program: Int, name: String): Int
    fun uniformMatrix4fv(location: Int, transpose: Boolean, value: FloatArray)
    fun uniform3f(location: Int, x: Float, y: Float, z: Float)
    fun uniform4f(location: Int, x: Float, y: Float, z: Float, w: Float)
    fun uniform1f(location: Int, value: Float)
    fun uniform1i(location: Int, value: Int)

    // --- Draw calls ---
    fun drawArrays(mode: Int, first: Int, count: Int)
    fun drawElements(mode: Int, count: Int, type: Int, offset: Int)

    // --- State ---
    fun enable(cap: Int)
    fun disable(cap: Int)
    fun depthFunc(func: Int)
    fun depthMask(flag: Boolean)
    fun blendFunc(sfactor: Int, dfactor: Int)
    fun cullFace(mode: Int)
    fun viewport(x: Int, y: Int, width: Int, height: Int)
    fun clearColor(r: Float, g: Float, b: Float, a: Float)
    fun clear(mask: Int)

    companion object {
        // ES 3.0 enum constants (identical numeric values across all backends).
        const val VERTEX_SHADER = 0x8B31
        const val FRAGMENT_SHADER = 0x8B30
        const val ARRAY_BUFFER = 0x8892
        const val ELEMENT_ARRAY_BUFFER = 0x8893
        const val STATIC_DRAW = 0x88E4
        const val DYNAMIC_DRAW = 0x88E8
        const val FLOAT = 0x1406
        const val UNSIGNED_INT = 0x1405
        const val TRIANGLES = 0x0004
        const val TRIANGLE_STRIP = 0x0005
        const val DEPTH_TEST = 0x0B71
        const val CULL_FACE = 0x0B44
        const val BLEND = 0x0BE2
        const val LEQUAL = 0x0203
        const val SRC_ALPHA = 0x0302
        const val ONE_MINUS_SRC_ALPHA = 0x0303
        const val BACK = 0x0405
        const val TEXTURE_2D = 0x0DE1
        const val TEXTURE0 = 0x84C0
        const val COLOR_BUFFER_BIT = 0x4000
        const val DEPTH_BUFFER_BIT = 0x0100
    }
}
