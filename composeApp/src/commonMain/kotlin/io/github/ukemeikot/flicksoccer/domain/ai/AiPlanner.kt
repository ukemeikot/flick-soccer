package io.github.ukemeikot.flicksoccer.domain.ai

import io.github.ukemeikot.flicksoccer.domain.model.BodyId
import io.github.ukemeikot.flicksoccer.domain.model.Difficulty
import io.github.ukemeikot.flicksoccer.domain.model.MatchState
import io.github.ukemeikot.flicksoccer.domain.model.ShotType
import io.github.ukemeikot.flicksoccer.domain.model.Team
import io.github.ukemeikot.flicksoccer.domain.model.Vec2

/** A candidate flick the AI evaluates by cloning the world and simulating it. */
data class ShotCandidate(
    val discId: BodyId,
    val dragVector: Vec2,
    val power: Float,
    val shotType: ShotType,
)

/** The plan the AI commits to for its turn. */
data class AiDecision(
    val candidate: ShotCandidate,
    val score: Float,
)

/**
 * Simulation-based candidate search (§6 of the design brief). Enumerates shots, clones the world,
 * simulates ~4s, scores the terminal state, and picks per difficulty. Deterministic given a seed so
 * behaviour is reproducible. Fully implemented in **M5**.
 */
class AiPlanner(
    private val evaluator: Evaluator = Evaluator(),
) {
    /**
     * Choose a shot for [team]. Runs off the main thread (Dispatchers.Default) in the ViewModel,
     * with a ~1.5s budget. Returns null only if there are no legal shots.
     */
    fun plan(state: MatchState, team: Team, difficulty: Difficulty, seed: Long): AiDecision? {
        // TODO(M5): enumerate candidates (discs × dirs × powers × shot types), prune chips outside
        //  the striker→ball→goal corridor, simulate each, score, and select per difficulty.
        return null
    }
}

/** Scores a terminal simulated [MatchState] from the AI's perspective (§6 weights). */
class Evaluator {
    fun score(terminal: MatchState, forTeam: Team): Float {
        // TODO(M5): +goal / −own-goal / +progress / +distance-to-mouth / −out-of-shape / −crossbar.
        return 0f
    }
}
