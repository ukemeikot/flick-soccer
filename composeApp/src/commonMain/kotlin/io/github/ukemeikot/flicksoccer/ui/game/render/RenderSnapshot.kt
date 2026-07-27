package io.github.ukemeikot.flicksoccer.ui.game.render

import io.github.ukemeikot.flicksoccer.domain.model.AimState
import io.github.ukemeikot.flicksoccer.domain.model.BodyKind
import io.github.ukemeikot.flicksoccer.domain.model.MatchPhase

/** A single body's transform for one rendered frame. */
data class BodyTransform(
    val id: Int,
    val kind: BodyKind,
    val x: Float,
    val y: Float,
    val z: Float,
    val radius: Float,
    val spin: Float, // cosmetic ball spin angle
)

/**
 * Immutable, self-contained description of one frame to draw. Published by the game loop into an
 * atomic reference and read by the GL thread (§5.2) — it carries no game logic and no references
 * back into mutable engine state.
 */
data class RenderSnapshot(
    val bodies: List<BodyTransform>,
    val aim: AimState?,
    val phase: MatchPhase,
    val scoreFlashSeconds: Float,
    val goalCamPunchSeconds: Float,
)
