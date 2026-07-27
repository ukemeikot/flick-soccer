package io.github.ukemeikot.flicksoccer.ui.game.render

/**
 * GLSL sources in the GL 3.3 core / GLSL ES 3.00 common subset. A per-platform [preamble]
 * (e.g. "#version 330 core" on desktop, "#version 300 es\nprecision highp float;" on mobile) is
 * prepended at compile time (§5.1). Normals use `mat3(uModel)` — fine for our near-uniform scales.
 */
object Shaders {

    fun withPreamble(preamble: String, body: String): String = preamble + "\n" + body

    /** Lit program. uMode 0 = solid color; uMode 1 = procedural pitch (stripes + markings). */
    const val LIT_VERTEX = """
        layout(location = 0) in vec3 aPos;
        layout(location = 1) in vec3 aNormal;
        layout(location = 2) in vec2 aUv;
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
        uniform int uMode;
        out vec4 fragColor;

        vec3 pitchColor(vec3 base) {
            // Mown stripes along the pitch length.
            float stripe = step(0.5, fract(vUv.y * 8.0));
            vec3 c = mix(base * 0.86, base * 1.06, stripe);
            // White markings: outer border, halfway line, and a rough center circle.
            float border = min(min(vUv.x, 1.0 - vUv.x), min(vUv.y, 1.0 - vUv.y));
            float line = smoothstep(0.012, 0.0, border);
            float half = smoothstep(0.006, 0.0, abs(vUv.y - 0.5));
            float d = distance(vUv, vec2(0.5, 0.5));
            float circle = smoothstep(0.006, 0.0, abs(d - 0.12));
            float mark = clamp(line + half + circle, 0.0, 1.0);
            return mix(c, vec3(0.92), mark * 0.8);
        }

        void main() {
            vec3 albedo = (uMode == 1) ? pitchColor(uColor) : uColor;
            float ndl = max(dot(normalize(vNormal), normalize(uLightDir)), 0.0);
            vec3 lit = albedo * (0.4 + 0.6 * ndl);
            fragColor = vec4(lit, 1.0);
        }
    """

    /** Unlit, flat RGBA — blob shadows, aim dots, translucent net. */
    const val UNLIT_VERTEX = """
        layout(location = 0) in vec3 aPos;
        uniform mat4 uViewProj;
        uniform mat4 uModel;
        void main() {
            gl_Position = uViewProj * uModel * vec4(aPos, 1.0);
        }
    """

    const val UNLIT_FRAGMENT = """
        uniform vec4 uRgba;
        out vec4 fragColor;
        void main() {
            fragColor = uRgba;
        }
    """
}
