package io.github.ukemeikot.flicksoccer.ui.game.render

/**
 * GLSL sources written once in the GL 3.3 core / GLSL ES 3.00 common subset. A per-platform
 * [preamble] prepends the right `#version` + precision qualifiers (§5.1). Bodies land in **M2**.
 */
object Shaders {
    /** Prepend before compiling: desktop = "#version 330 core", mobile = "#version 300 es\nprecision …". */
    fun withPreamble(preamble: String, body: String): String = preamble + "\n" + body

    const val LIT_VERTEX = """
        in vec3 aPos;
        in vec3 aNormal;
        in vec2 aUv;
        uniform mat4 uViewProj;
        uniform mat4 uModel;
        out vec3 vNormal;
        out vec2 vUv;
        void main() {
            vNormal = mat3(uModel) * aNormal;
            vUv = aUv;
            gl_Position = uViewProj * uModel * vec4(aPos, 1.0);
        }
    """

    const val LIT_FRAGMENT = """
        in vec3 vNormal;
        in vec2 vUv;
        uniform vec3 uLightDir;
        uniform vec3 uColor;
        out vec4 fragColor;
        void main() {
            float ndl = max(dot(normalize(vNormal), normalize(uLightDir)), 0.0);
            vec3 lit = uColor * (0.35 + 0.65 * ndl);
            fragColor = vec4(lit, 1.0);
        }
    """
}
