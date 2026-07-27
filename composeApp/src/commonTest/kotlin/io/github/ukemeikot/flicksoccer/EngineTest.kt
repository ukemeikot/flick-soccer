package io.github.ukemeikot.flicksoccer

import io.github.ukemeikot.flicksoccer.domain.engine.GameEngine
import io.github.ukemeikot.flicksoccer.domain.engine.Rules
import io.github.ukemeikot.flicksoccer.domain.model.BodyKind
import io.github.ukemeikot.flicksoccer.domain.model.Team
import kotlin.test.Test
import kotlin.test.assertEquals

class EngineTest {

    @Test
    fun kickoff_has_five_discs_per_team_plus_one_ball() {
        val engine = GameEngine()
        val bodies = engine.state.bodies
        assertEquals(Rules.DISCS_PER_TEAM, bodies.count { it.kind == BodyKind.TEAM_A_DISC })
        assertEquals(Rules.DISCS_PER_TEAM, bodies.count { it.kind == BodyKind.TEAM_B_DISC })
        assertEquals(1, bodies.count { it.kind == BodyKind.BALL })
    }

    @Test
    fun ball_starts_at_pitch_center_on_the_ground() {
        val engine = GameEngine()
        val ball = engine.state.bodies.first { it.kind == BodyKind.BALL }
        val pitch = engine.state.pitch
        assertEquals(pitch.halfWidth, ball.position.x, 1e-3f)
        assertEquals(pitch.halfHeight, ball.position.y, 1e-3f)
        assertEquals(0f, ball.z, 1e-6f)
    }

    @Test
    fun end_turn_alternates_team_and_advances_turn_number() {
        val engine = GameEngine()
        assertEquals(Team.A, engine.state.turn)
        engine.endTurn()
        assertEquals(Team.B, engine.state.turn)
        assertEquals(2, engine.state.turnNumber)
    }
}
