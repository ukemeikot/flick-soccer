package io.github.ukemeikot.flicksoccer.platform.gl

import android.opengl.GLES20
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Android [Gl] implementation over GLES 3.0 (GLES30/GLES20). One-line delegations only. Array data
 * is wrapped in direct, native-order NIO buffers as GLES requires.
 */
class GlesGl : Gl {
    private fun fb(a: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

    private fun ib(a: IntArray): IntBuffer =
        ByteBuffer.allocateDirect(a.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().apply { put(a); position(0) }

    override fun createShader(type: Int) = GLES20.glCreateShader(type)
    override fun shaderSource(shader: Int, source: String) = GLES20.glShaderSource(shader, source)
    override fun compileShader(shader: Int) = GLES20.glCompileShader(shader)
    override fun getShaderCompileStatus(shader: Int): Boolean {
        val out = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, out, 0); return out[0] == GLES20.GL_TRUE
    }
    override fun getShaderInfoLog(shader: Int): String = GLES20.glGetShaderInfoLog(shader)
    override fun deleteShader(shader: Int) = GLES20.glDeleteShader(shader)
    override fun createProgram() = GLES20.glCreateProgram()
    override fun attachShader(program: Int, shader: Int) = GLES20.glAttachShader(program, shader)
    override fun linkProgram(program: Int) = GLES20.glLinkProgram(program)
    override fun getProgramLinkStatus(program: Int): Boolean {
        val out = IntArray(1); GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, out, 0); return out[0] == GLES20.GL_TRUE
    }
    override fun getProgramInfoLog(program: Int): String = GLES20.glGetProgramInfoLog(program)
    override fun useProgram(program: Int) = GLES20.glUseProgram(program)

    override fun genBuffer(): Int { val a = IntArray(1); GLES20.glGenBuffers(1, a, 0); return a[0] }
    override fun bindBuffer(target: Int, buffer: Int) = GLES20.glBindBuffer(target, buffer)
    override fun bufferData(target: Int, data: FloatArray, usage: Int) =
        GLES20.glBufferData(target, data.size * 4, fb(data), usage)
    override fun bufferDataInt(target: Int, data: IntArray, usage: Int) =
        GLES20.glBufferData(target, data.size * 4, ib(data), usage)
    override fun genVertexArray(): Int { val a = IntArray(1); GLES30.glGenVertexArrays(1, a, 0); return a[0] }
    override fun bindVertexArray(vao: Int) = GLES30.glBindVertexArray(vao)
    override fun enableVertexAttribArray(index: Int) = GLES20.glEnableVertexAttribArray(index)
    override fun vertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) =
        GLES20.glVertexAttribPointer(index, size, type, normalized, stride, offset)

    override fun genTexture(): Int { val a = IntArray(1); GLES20.glGenTextures(1, a, 0); return a[0] }
    override fun bindTexture(target: Int, texture: Int) = GLES20.glBindTexture(target, texture)
    override fun texParameteri(target: Int, pname: Int, param: Int) = GLES20.glTexParameteri(target, pname, param)
    override fun texImage2D(target: Int, level: Int, internalFormat: Int, width: Int, height: Int, format: Int, type: Int, pixels: ByteArray?) {
        val buf = pixels?.let { ByteBuffer.allocateDirect(it.size).order(ByteOrder.nativeOrder()).apply { put(it); position(0) } }
        GLES20.glTexImage2D(target, level, internalFormat, width, height, 0, format, type, buf)
    }
    override fun activeTexture(unit: Int) = GLES20.glActiveTexture(unit)

    override fun getUniformLocation(program: Int, name: String) = GLES20.glGetUniformLocation(program, name)
    override fun getAttribLocation(program: Int, name: String) = GLES20.glGetAttribLocation(program, name)
    override fun uniformMatrix4fv(location: Int, transpose: Boolean, value: FloatArray) =
        GLES20.glUniformMatrix4fv(location, 1, transpose, value, 0)
    override fun uniform3f(location: Int, x: Float, y: Float, z: Float) = GLES20.glUniform3f(location, x, y, z)
    override fun uniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) = GLES20.glUniform4f(location, x, y, z, w)
    override fun uniform1f(location: Int, value: Float) = GLES20.glUniform1f(location, value)
    override fun uniform1i(location: Int, value: Int) = GLES20.glUniform1i(location, value)

    override fun drawArrays(mode: Int, first: Int, count: Int) = GLES20.glDrawArrays(mode, first, count)
    override fun drawElements(mode: Int, count: Int, type: Int, offset: Int) = GLES20.glDrawElements(mode, count, type, offset)

    override fun enable(cap: Int) = GLES20.glEnable(cap)
    override fun disable(cap: Int) = GLES20.glDisable(cap)
    override fun depthFunc(func: Int) = GLES20.glDepthFunc(func)
    override fun depthMask(flag: Boolean) = GLES20.glDepthMask(flag)
    override fun blendFunc(sfactor: Int, dfactor: Int) = GLES20.glBlendFunc(sfactor, dfactor)
    override fun cullFace(mode: Int) = GLES20.glCullFace(mode)
    override fun viewport(x: Int, y: Int, width: Int, height: Int) = GLES20.glViewport(x, y, width, height)
    override fun clearColor(r: Float, g: Float, b: Float, a: Float) = GLES20.glClearColor(r, g, b, a)
    override fun clear(mask: Int) = GLES20.glClear(mask)
}
