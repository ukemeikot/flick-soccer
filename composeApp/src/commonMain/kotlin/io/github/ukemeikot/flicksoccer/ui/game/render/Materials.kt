package io.github.ukemeikot.flicksoccer.ui.game.render

/** RGB material tint (0..1). Team palettes map to disc colors (§8.3). */
data class Material(val r: Float, val g: Float, val b: Float)

/** The four selectable team palettes (blue/red default). Expanded in M6. */
object Palettes {
    val teamA = listOf(
        Material(0.20f, 0.45f, 0.95f), // blue
        Material(0.10f, 0.70f, 0.55f), // teal
        Material(0.55f, 0.35f, 0.90f), // purple
        Material(0.95f, 0.75f, 0.20f), // gold
    )
    val teamB = listOf(
        Material(0.92f, 0.28f, 0.28f), // red
        Material(0.95f, 0.55f, 0.15f), // orange
        Material(0.85f, 0.20f, 0.55f), // magenta
        Material(0.40f, 0.75f, 0.25f), // green
    )
    val pitch = Material(0.16f, 0.55f, 0.24f)
    val ball = Material(0.96f, 0.96f, 0.96f)
}
