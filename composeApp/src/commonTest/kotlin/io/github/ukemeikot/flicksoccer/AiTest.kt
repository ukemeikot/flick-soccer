package io.github.ukemeikot.flicksoccer

import io.github.ukemeikot.flicksoccer.domain.ai.AiPlanner
import io.github.ukemeikot.flicksoccer.domain.engine.FormationProvider
import io.github.ukemeikot.flicksoccer.domain.model.Body
import io.github.ukemeikot.flicksoccer.domain.model.BodyId
import io.github.ukemeikot.flicksoccer.domain.model.BodyKind
import io.github.ukemeikot.flicksoccer.domain.model.Difficulty
import io.github.ukemeikot.flicksoccer.domain.model.MatchState
import io.github.ukemeikot.flicksoccer.domain.model.PitchSpec
import io.github.ukemeikot.flicksoccer.domain.model.ShotType
import io.github.ukemeikot.flicksoccer.domain.model.Team
import io.github.ukemeikot.flicksoccer.domain.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiTest {

    private val pitch = PitchSpec()
    private val planner = AiPlanner()

    private fun disc(id: Int, team: Team, x: Float, y: Float) = Body(
        BodyId(id),
        if (team == Team.A) BodyKind.TEAM_A_DISC else BodyKind.TEAM_B_DISC,
        Vec2(x, y), Vec2.ZERO, 0f, 0f, radius = 3.2f, mass = 1.2f,
    )

    private fun ball(id: Int, x: Float, y: Float) =
        Body(BodyId(id), BodyKind.BALL, Vec2(x, y), Vec2.ZERO, 0f, 0f, radius = 2f, mass = 0.4f)

    @Test
    fun ai_only_flicks_its_own_disc() {
        val state = MatchState(bodies = FormationProvider(pitch).kickoff(Team.A), turn = Team.B, pitch = pitch)
        val bDiscIds = state.bodies.filter { it.kind == BodyKind.TEAM_B_DISC }.map { it.id }.toSet()
        val decision = planner.plan(state, Team.B, Difficulty.HARD, seed = 1L)
        assertNotNull(decision)
        assertTrue(decision.candidate.discId in bDiscIds, "AI must only flick its own discs")
    }

    @Test
    fun hard_scores_on_an_open_goal() {
        // Team A attacks +y. Disc directly below the ball, clear path to the top goal.
        val state = MatchState(
            bodies = listOf(disc(0, Team.A, pitch.halfWidth, pitch.height - 18f), ball(1, pitch.halfWidth, pitch.height - 8f)),
            turn = Team.A, pitch = pitch,
        )
        val decision = planner.plan(state, Team.A, Difficulty.HARD, seed = 7L)
        assertNotNull(decision)
        assertTrue(decision.score >= 1000f, "Hard should find the goal (score ${decision.score})")
    }

    @Test
    fun easy_never_chips() {
        val state = MatchState(bodies = FormationProvider(pitch).kickoff(Team.A), turn = Team.A, pitch = pitch)
        repeat(5) { seed ->
            val decision = planner.plan(state, Team.A, Difficulty.EASY, seed = seed.toLong())
            assertNotNull(decision)
            assertEquals(ShotType.GROUND, decision.candidate.shotType, "Easy AI must never chip")
        }
    }

    @Test
    fun hard_chooses_a_chip_when_a_wall_blocks_the_ground_path() {
        // Team A: disc below the ball, a full-width wall of B discs just beyond the ball. Only a
        // lofted chip can carry the ball over the wall toward the top goal.
        val bodies = ArrayList<Body>()
        bodies += disc(0, Team.A, pitch.halfWidth, 66f)
        bodies += ball(100, pitch.halfWidth, 74f)
        var id = 1
        var x = 12f
        while (x <= pitch.width - 12f) { bodies += disc(id++, Team.B, x, 84f); x += 7f }
        val state = MatchState(bodies = bodies, turn = Team.A, pitch = pitch)

        val decision = planner.plan(state, Team.A, Difficulty.HARD, seed = 3L)
        assertNotNull(decision)
        assertEquals(ShotType.CHIP, decision.candidate.shotType, "Hard should chip over the wall (score ${decision.score})")
    }

    @Test
    fun easy_and_hard_choices_differ() {
        val state = MatchState(bodies = FormationProvider(pitch).kickoff(Team.A), turn = Team.A, pitch = pitch)
        var anyDifferent = false
        for (seed in 1L..4L) {
            val easy = planner.plan(state, Team.A, Difficulty.EASY, seed)!!
            val hard = planner.plan(state, Team.A, Difficulty.HARD, seed)!!
            if (easy.candidate.discId != hard.candidate.discId || easy.candidate.dragVector != hard.candidate.dragVector) {
                anyDifferent = true
            }
        }
        assertTrue(anyDifferent, "Easy (noisy, random top-50%) should diverge from Hard (deterministic best)")
    }
}
