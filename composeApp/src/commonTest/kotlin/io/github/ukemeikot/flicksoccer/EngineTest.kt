package io.github.ukemeikot.flicksoccer

import io.github.ukemeikot.flicksoccer.domain.engine.GameEngine
import io.github.ukemeikot.flicksoccer.domain.engine.Rules
import io.github.ukemeikot.flicksoccer.domain.model.BodyKind
import io.github.ukemeikot.flicksoccer.domain.model.MatchPhase
import io.github.ukemeikot.flicksoccer.domain.model.ShotType
import io.github.ukemeikot.flicksoccer.domain.model.Team
import io.github.ukemeikot.flicksoccer.domain.model.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun cannot_flick_an_opponent_disc() {
        val engine = GameEngine() // Team A to move
        val bDisc = engine.discsOf(Team.B).first()
        assertFalse(engine.canFlick(bDisc.id))
        assertFalse(engine.flick(bDisc.id, Vec2(0f, -10f), ShotType.GROUND))
        assertEquals(MatchPhase.AIMING, engine.state.phase)
    }

    @Test
    fun legal_flick_enters_simulating() {
        val engine = GameEngine()
        val aDisc = engine.discsOf(Team.A).first()
        assertTrue(engine.canFlick(aDisc.id))
        assertTrue(engine.flick(aDisc.id, Vec2(0f, -20f), ShotType.GROUND))
        assertEquals(MatchPhase.SIMULATING, engine.state.phase)
    }

    @Test
    fun tiny_drag_below_min_power_is_rejected() {
        val engine = GameEngine()
        val aDisc = engine.discsOf(Team.A).first()
        // Drag length far below MIN_POWER_TO_FLICK * MAX_DRAG_LEN.
        assertFalse(engine.flick(aDisc.id, Vec2(0f, -0.5f), ShotType.GROUND))
        assertEquals(MatchPhase.AIMING, engine.state.phase)
    }

    @Test
    fun settle_turn_alternates_team_and_advances_turn_number() {
        val engine = GameEngine()
        val aDisc = engine.discsOf(Team.A).first()
        engine.flick(aDisc.id, Vec2(0f, -20f), ShotType.GROUND)
        engine.settleTurn()
        assertEquals(Team.B, engine.state.turn)
        assertEquals(2, engine.state.turnNumber)
        assertEquals(MatchPhase.AIMING, engine.state.phase)
    }

    @Test
    fun reaching_the_turn_limit_ends_the_match() {
        val engine = GameEngine()
        var guard = 0
        while (engine.state.phase != MatchPhase.MATCH_OVER && guard++ < 100) {
            val disc = engine.discsOf(engine.state.turn).first()
            engine.flick(disc.id, Vec2(0f, if (engine.state.turn == Team.A) -20f else 20f), ShotType.GROUND)
            engine.settleTurn()
        }
        assertEquals(MatchPhase.MATCH_OVER, engine.state.phase)
        assertTrue(engine.state.turnNumber >= Rules.TURN_LIMIT)
    }
}
