package io.github.ukemeikot.flicksoccer.platform.gl

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import java.nio.ByteBuffer

/**
 * Desktop [Gl] implementation over LWJGL (GL 3.3 core). One-line delegations only — no rendering
 * logic. A single reusable 4x4 buffer keeps per-frame uniform uploads allocation-free.
 */
class LwjglGl : Gl {
    private val mat4Buf = BufferUtils.createFloatBuffer(16)

    override fun createShader(type: Int) = GL20.glCreateShader(type)
    override fun shaderSource(shader: Int, source: String) = GL20.glShaderSource(shader, source)
    override fun compileShader(shader: Int) = GL20.glCompileShader(shader)
    override fun getShaderCompileStatus(shader: Int) = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE
    override fun getShaderInfoLog(shader: Int): String = GL20.glGetShaderInfoLog(shader)
    override fun deleteShader(shader: Int) = GL20.glDeleteShader(shader)
    override fun createProgram() = GL20.glCreateProgram()
    override fun attachShader(program: Int, shader: Int) = GL20.glAttachShader(program, shader)
    override fun linkProgram(program: Int) = GL20.glLinkProgram(program)
    override fun getProgramLinkStatus(program: Int) = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE
    override fun getProgramInfoLog(program: Int): String = GL20.glGetProgramInfoLog(program)
    override fun useProgram(program: Int) = GL20.glUseProgram(program)

    override fun genBuffer() = GL15.glGenBuffers()
    override fun bindBuffer(target: Int, buffer: Int) = GL15.glBindBuffer(target, buffer)
    override fun bufferData(target: Int, data: FloatArray, usage: Int) {
        val buf = BufferUtils.createFloatBuffer(data.size).put(data).flip()
        GL15.glBufferData(target, buf, usage)
    }
    override fun bufferDataInt(target: Int, data: IntArray, usage: Int) {
        val buf = BufferUtils.createIntBuffer(data.size).put(data).flip()
        GL15.glBufferData(target, buf, usage)
    }
    override fun genVertexArray() = GL30.glGenVertexArrays()
    override fun bindVertexArray(vao: Int) = GL30.glBindVertexArray(vao)
    override fun enableVertexAttribArray(index: Int) = GL20.glEnableVertexAttribArray(index)
    override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) =
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, offset.toLong())

    override fun genTexture() = GL11.glGenTextures()
    override fun bindTexture(target: Int, texture: Int) = GL11.glBindTexture(target, texture)
    override fun texParameteri(target: Int, pname: Int, param: Int) = GL11.glTexParameteri(target, pname, param)
    override fun texImage2D(target: Int, level: Int, internalFormat: Int, width: Int, height: Int, format: Int, type: Int, pixels: ByteArray?) {
        val buf: ByteBuffer? = pixels?.let { BufferUtils.createByteBuffer(it.size).put(it).flip() as ByteBuffer }
        GL11.glTexImage2D(target, level, internalFormat, width, height, 0, format, type, buf)
    }
    override fun activeTexture(unit: Int) = GL13.glActiveTexture(unit)

    override fun getUniformLocation(program: Int, name: String) = GL20.glGetUniformLocation(program, name)
    override fun getAttribLocation(program: Int, name: String) = GL20.glGetAttribLocation(program, name)
    override fun uniformMatrix4fv(location: Int, transpose: Boolean, value: FloatArray) {
        mat4Buf.clear(); mat4Buf.put(value); mat4Buf.flip()
        GL20.glUniformMatrix4fv(location, transpose, mat4Buf)
    }
    override fun uniform3f(location: Int, x: Float, y: Float, z: Float) = GL20.glUniform3f(location, x, y, z)
    override fun uniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) = GL20.glUniform4f(location, x, y, z, w)
    override fun uniform1f(location: Int, value: Float) = GL20.glUniform1f(location, value)
    override fun uniform1i(location: Int, value: Int) = GL20.glUniform1i(location, value)

    override fun drawArrays(mode: Int, first: Int, count: Int) = GL11.glDrawArrays(mode, first, count)
    override fun drawElements(mode: Int, count: Int, type: Int, offset: Int) = GL11.glDrawElements(mode, count, type, offset.toLong())

    override fun enable(cap: Int) = GL11.glEnable(cap)
    override fun disable(cap: Int) = GL11.glDisable(cap)
    override fun depthFunc(func: Int) = GL11.glDepthFunc(func)
    override fun depthMask(flag: Boolean) = GL11.glDepthMask(flag)
    override fun blendFunc(sfactor: Int, dfactor: Int) = GL11.glBlendFunc(sfactor, dfactor)
    override fun cullFace(mode: Int) = GL11.glCullFace(mode)
    override fun viewport(x: Int, y: Int, width: Int, height: Int) = GL11.glViewport(x, y, width, height)
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) = GL11.glClearColor(r, g, b, a)
    override fun clear(mask: Int) = GL11.glClear(mask)
}
