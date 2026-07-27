package io.github.ukemeikot.flicksoccer.ui.game.render

/** Interleaved vertex data (position + normal + uv) plus an index buffer for one procedural mesh. */
data class MeshData(
    val vertices: FloatArray,
    val indices: IntArray,
) {
    val indexCount: Int get() = indices.size

    override fun equals(other: Any?): Boolean =
        this === other || (other is MeshData && vertices.contentEquals(other.vertices) && indices.contentEquals(other.indices))

    override fun hashCode(): Int = 31 * vertices.contentHashCode() + indices.contentHashCode()
}

/**
 * Procedural mesh builders (§5.3) — zero asset pipeline. Filled in during **M2**: a flat quad
 * (pitch), a beveled cylinder (disc), a UV sphere (ball), and a goal frame (posts + crossbar).
 */
object Meshes {
    fun quad(width: Float, height: Float): MeshData {
        val hw = width / 2f
        val hh = height / 2f
        // pos(3) + normal(3) + uv(2)
        val vertices = floatArrayOf(
            -hw, -hh, 0f, 0f, 0f, 1f, 0f, 0f,
            hw, -hh, 0f, 0f, 0f, 1f, 1f, 0f,
            hw, hh, 0f, 0f, 0f, 1f, 1f, 1f,
            -hw, hh, 0f, 0f, 0f, 1f, 0f, 1f,
        )
        val indices = intArrayOf(0, 1, 2, 0, 2, 3)
        return MeshData(vertices, indices)
    }

    // TODO(M2): cylinder(segments), uvSphere(stacks, slices), goalFrame(spec).
}
