package io.github.ukemeikot.flicksoccer.ui.game.render

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Interleaved vertex data — position(3) + normal(3) + uv(2), stride 8 floats — plus indices. */
class MeshData(
    val vertices: FloatArray,
    val indices: IntArray,
) {
    val indexCount: Int get() = indices.size
    companion object { const val STRIDE_FLOATS = 8 }
}

/** A tiny growable builder so generators stay readable and allocation happens once, up front. */
private class MeshBuilder {
    private val v = ArrayList<Float>()
    private val idx = ArrayList<Int>()
    var count = 0; private set

    fun vertex(px: Float, py: Float, pz: Float, nx: Float, ny: Float, nz: Float, u: Float, w: Float): Int {
        v.add(px); v.add(py); v.add(pz); v.add(nx); v.add(ny); v.add(nz); v.add(u); v.add(w)
        return count++
    }

    fun tri(a: Int, b: Int, c: Int) { idx.add(a); idx.add(b); idx.add(c) }
    fun quad(a: Int, b: Int, c: Int, d: Int) { tri(a, b, c); tri(a, c, d) }

    fun build() = MeshData(v.toFloatArray(), idx.toIntArray())
}

/** Procedural mesh builders (§5.3) — zero asset pipeline. All meshes are unit-ish and scaled per draw. */
object Meshes {

    /** Flat quad on the z = 0 plane, size [width] x [height], centered at origin, normal +z. */
    fun quad(width: Float = 1f, height: Float = 1f): MeshData {
        val hw = width / 2f; val hh = height / 2f
        val b = MeshBuilder()
        val a0 = b.vertex(-hw, -hh, 0f, 0f, 0f, 1f, 0f, 0f)
        val a1 = b.vertex(hw, -hh, 0f, 0f, 0f, 1f, 1f, 0f)
        val a2 = b.vertex(hw, hh, 0f, 0f, 0f, 1f, 1f, 1f)
        val a3 = b.vertex(-hw, hh, 0f, 0f, 0f, 1f, 0f, 1f)
        b.quad(a0, a1, a2, a3)
        return b.build()
    }

    /** Unit cube centered at origin (−0.5..0.5), per-face normals. Used for goal posts/bar and walls. */
    fun box(): MeshData {
        val b = MeshBuilder()
        // Each face: 4 verts + quad. (nx,ny,nz) is the outward normal.
        data class Face(val n: Triple<Float, Float, Float>, val verts: Array<FloatArray>)
        val h = 0.5f
        val faces = listOf(
            Triple(0f, 0f, 1f) to arrayOf(floatArrayOf(-h,-h,h), floatArrayOf(h,-h,h), floatArrayOf(h,h,h), floatArrayOf(-h,h,h)),
            Triple(0f, 0f, -1f) to arrayOf(floatArrayOf(h,-h,-h), floatArrayOf(-h,-h,-h), floatArrayOf(-h,h,-h), floatArrayOf(h,h,-h)),
            Triple(1f, 0f, 0f) to arrayOf(floatArrayOf(h,-h,h), floatArrayOf(h,-h,-h), floatArrayOf(h,h,-h), floatArrayOf(h,h,h)),
            Triple(-1f, 0f, 0f) to arrayOf(floatArrayOf(-h,-h,-h), floatArrayOf(-h,-h,h), floatArrayOf(-h,h,h), floatArrayOf(-h,h,-h)),
            Triple(0f, 1f, 0f) to arrayOf(floatArrayOf(-h,h,h), floatArrayOf(h,h,h), floatArrayOf(h,h,-h), floatArrayOf(-h,h,-h)),
            Triple(0f, -1f, 0f) to arrayOf(floatArrayOf(-h,-h,-h), floatArrayOf(h,-h,-h), floatArrayOf(h,-h,h), floatArrayOf(-h,-h,h)),
        )
        for ((n, vs) in faces) {
            val i0 = b.vertex(vs[0][0], vs[0][1], vs[0][2], n.first, n.second, n.third, 0f, 0f)
            val i1 = b.vertex(vs[1][0], vs[1][1], vs[1][2], n.first, n.second, n.third, 1f, 0f)
            val i2 = b.vertex(vs[2][0], vs[2][1], vs[2][2], n.first, n.second, n.third, 1f, 1f)
            val i3 = b.vertex(vs[3][0], vs[3][1], vs[3][2], n.first, n.second, n.third, 0f, 1f)
            b.quad(i0, i1, i2, i3)
        }
        return b.build()
    }

    /**
     * Disc puck: unit-radius cylinder, height [height], [segments] sides, with top + bottom caps.
     * Base sits on z = 0, top at z = height. Scaled to the disc radius per draw.
     */
    fun cylinder(height: Float = 0.6f, segments: Int = 24): MeshData {
        val b = MeshBuilder()
        val topCenter = b.vertex(0f, 0f, height, 0f, 0f, 1f, 0.5f, 0.5f)
        val botCenter = b.vertex(0f, 0f, 0f, 0f, 0f, -1f, 0.5f, 0.5f)
        val topRing = IntArray(segments)
        val botRing = IntArray(segments)
        val sideTop = IntArray(segments)
        val sideBot = IntArray(segments)
        for (i in 0 until segments) {
            val a = (i.toFloat() / segments) * (2f * PI.toFloat())
            val cx = cos(a); val cy = sin(a)
            topRing[i] = b.vertex(cx, cy, height, 0f, 0f, 1f, 0.5f + 0.5f * cx, 0.5f + 0.5f * cy)
            botRing[i] = b.vertex(cx, cy, 0f, 0f, 0f, -1f, 0.5f + 0.5f * cx, 0.5f + 0.5f * cy)
            sideTop[i] = b.vertex(cx, cy, height, cx, cy, 0f, i.toFloat() / segments, 1f)
            sideBot[i] = b.vertex(cx, cy, 0f, cx, cy, 0f, i.toFloat() / segments, 0f)
        }
        for (i in 0 until segments) {
            val j = (i + 1) % segments
            b.tri(topCenter, topRing[i], topRing[j])     // top cap
            b.tri(botCenter, botRing[j], botRing[i])     // bottom cap
            b.quad(sideBot[i], sideBot[j], sideTop[j], sideTop[i]) // side
        }
        return b.build()
    }

    /** Unit-radius UV sphere ([stacks] latitude bands x [slices] longitude). Scaled per draw. */
    fun uvSphere(stacks: Int = 16, slices: Int = 24): MeshData {
        val b = MeshBuilder()
        val grid = Array(stacks + 1) { IntArray(slices + 1) }
        for (i in 0..stacks) {
            val theta = PI.toFloat() * i / stacks       // 0..pi (from +z pole)
            val st = sin(theta); val ct = cos(theta)
            for (j in 0..slices) {
                val phi = 2f * PI.toFloat() * j / slices
                val x = st * cos(phi); val y = st * sin(phi); val z = ct
                grid[i][j] = b.vertex(x, y, z, x, y, z, j.toFloat() / slices, i.toFloat() / stacks)
            }
        }
        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                b.quad(grid[i][j], grid[i][j + 1], grid[i + 1][j + 1], grid[i + 1][j])
            }
        }
        return b.build()
    }
}
