package com.unciv.ui.render.globe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlobeMovementTurnPreviewPolicyTests {
    @Test
    fun `resolveTurns returns null when target cannot be reached`() {
        var called = false
        val turns = GlobeMovementTurnPreviewPolicy.resolveTurns(
            unit = GlobeMovementTurnPreviewPolicy.UnitContext(
                movesLikeAirUnits = false,
                isPreparingParadrop = false
            ),
            canReach = false,
            shortestPathSizeProvider = {
                called = true
                3
            }
        )

        assertNull(turns)
        assertEquals(false, called)
    }

    @Test
    fun `resolveTurns returns one turn for air units without probing path`() {
        var called = false
        val turns = GlobeMovementTurnPreviewPolicy.resolveTurns(
            unit = GlobeMovementTurnPreviewPolicy.UnitContext(
                movesLikeAirUnits = true,
                isPreparingParadrop = false
            ),
            canReach = true,
            shortestPathSizeProvider = {
                called = true
                5
            }
        )

        assertEquals(1, turns)
        assertEquals(false, called)
    }

    @Test
    fun `resolveTurns returns null when shortest path probing throws`() {
        val turns = GlobeMovementTurnPreviewPolicy.resolveTurns(
            unit = GlobeMovementTurnPreviewPolicy.UnitContext(
                movesLikeAirUnits = false,
                isPreparingParadrop = false
            ),
            canReach = true,
            shortestPathSizeProvider = { throw Exception("Can't reach this tile!") }
        )

        assertNull(turns)
    }

    @Test
    fun `resolveTurns returns path size for reachable land units`() {
        val turns = GlobeMovementTurnPreviewPolicy.resolveTurns(
            unit = GlobeMovementTurnPreviewPolicy.UnitContext(
                movesLikeAirUnits = false,
                isPreparingParadrop = false
            ),
            canReach = true,
            shortestPathSizeProvider = { 4 }
        )

        assertEquals(4, turns)
    }
}
