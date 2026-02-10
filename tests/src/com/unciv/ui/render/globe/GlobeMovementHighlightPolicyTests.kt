package com.unciv.ui.render.globe

import com.badlogic.gdx.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlobeMovementHighlightPolicyTests {
    @Test
    fun `resolve returns null when tile is not reachable this turn`() {
        val result = GlobeMovementHighlightPolicy.resolve(
            unit = GlobeMovementHighlightPolicy.UnitContext(
                movesLikeAirUnits = false,
                isPreparingParadrop = false
            ),
            tile = GlobeMovementHighlightPolicy.TileContext(
                isReachableThisTurn = false,
                canMoveTo = true,
                assumePassableUnknown = false
            ),
            settings = GlobeMovementHighlightPolicy.Settings(
                useCirclesToIndicateMovableTiles = false,
                singleTapMove = false
            )
        )

        assertNull(result)
    }

    @Test
    fun `resolve returns circle highlight when circles are enabled`() {
        val result = GlobeMovementHighlightPolicy.resolve(
            unit = GlobeMovementHighlightPolicy.UnitContext(
                movesLikeAirUnits = false,
                isPreparingParadrop = false
            ),
            tile = GlobeMovementHighlightPolicy.TileContext(
                isReachableThisTurn = true,
                canMoveTo = true,
                assumePassableUnknown = false
            ),
            settings = GlobeMovementHighlightPolicy.Settings(
                useCirclesToIndicateMovableTiles = true,
                singleTapMove = true
            )
        )!!

        assertEquals(true, result.drawAsCircle)
        assertEquals(0.7f, result.alpha, 0.0001f)
        assertEquals(Color.WHITE.r, result.color.r, 0.0001f)
        assertEquals(Color.WHITE.g, result.color.g, 0.0001f)
        assertEquals(Color.WHITE.b, result.color.b, 0.0001f)
    }

    @Test
    fun `resolve returns brightened fill highlight when circles are disabled`() {
        val result = GlobeMovementHighlightPolicy.resolve(
            unit = GlobeMovementHighlightPolicy.UnitContext(
                movesLikeAirUnits = false,
                isPreparingParadrop = false
            ),
            tile = GlobeMovementHighlightPolicy.TileContext(
                isReachableThisTurn = true,
                canMoveTo = true,
                assumePassableUnknown = false
            ),
            settings = GlobeMovementHighlightPolicy.Settings(
                useCirclesToIndicateMovableTiles = false,
                singleTapMove = false
            )
        )!!

        assertEquals(false, result.drawAsCircle)
        assertEquals(0.4f, result.alpha, 0.0001f)
        assertEquals(1f, result.color.r, 0.0001f)
        assertEquals(1f, result.color.g, 0.0001f)
        assertEquals(1f, result.color.b, 0.0001f)
    }

    @Test
    fun `resolve allows unknown passable highlights only for non-air units`() {
        val airResult = GlobeMovementHighlightPolicy.resolve(
            unit = GlobeMovementHighlightPolicy.UnitContext(
                movesLikeAirUnits = true,
                isPreparingParadrop = false
            ),
            tile = GlobeMovementHighlightPolicy.TileContext(
                isReachableThisTurn = true,
                canMoveTo = false,
                assumePassableUnknown = true
            ),
            settings = GlobeMovementHighlightPolicy.Settings(
                useCirclesToIndicateMovableTiles = false,
                singleTapMove = false
            )
        )
        assertNull(airResult)

        val landResult = GlobeMovementHighlightPolicy.resolve(
            unit = GlobeMovementHighlightPolicy.UnitContext(
                movesLikeAirUnits = false,
                isPreparingParadrop = false
            ),
            tile = GlobeMovementHighlightPolicy.TileContext(
                isReachableThisTurn = true,
                canMoveTo = false,
                assumePassableUnknown = true
            ),
            settings = GlobeMovementHighlightPolicy.Settings(
                useCirclesToIndicateMovableTiles = false,
                singleTapMove = false
            )
        )
        assertEquals(false, landResult?.drawAsCircle)
    }

    @Test
    fun `resolve uses blue move color for paradrop preparation`() {
        val result = GlobeMovementHighlightPolicy.resolve(
            unit = GlobeMovementHighlightPolicy.UnitContext(
                movesLikeAirUnits = false,
                isPreparingParadrop = true
            ),
            tile = GlobeMovementHighlightPolicy.TileContext(
                isReachableThisTurn = true,
                canMoveTo = true,
                assumePassableUnknown = false
            ),
            settings = GlobeMovementHighlightPolicy.Settings(
                useCirclesToIndicateMovableTiles = true,
                singleTapMove = false
            )
        )!!

        assertEquals(Color.BLUE.r, result.color.r, 0.0001f)
        assertEquals(Color.BLUE.g, result.color.g, 0.0001f)
        assertEquals(Color.BLUE.b, result.color.b, 0.0001f)
    }
}
