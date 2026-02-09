package com.unciv.ui.render.globe

import com.unciv.testing.TestGame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.unciv.testing.GdxTestRunner

@RunWith(GdxTestRunner::class)
class GlobeVisibilityPolicyTests {

    @Test
    fun fogDisabledKeepsTilesVisibleEvenWhenUnexplored() {
        val game = TestGame()
        game.makeHexagonalMap(1)
        val civ = game.addCiv()
        val tile = game.tileMap.tileList.first()

        val visibility = GlobeVisibilityPolicy.resolve(
            tile,
            GlobeVisibilityPolicy.Context(fogOfWarEnabled = false, viewingCiv = civ)
        )

        assertTrue(visibility.isExplored)
        assertTrue(visibility.isVisible)
        assertTrue(visibility.allowsInteraction)
    }

    @Test
    fun unexploredTileIsHiddenWhenFogIsEnabled() {
        val game = TestGame()
        game.makeHexagonalMap(1)
        val civ = game.addCiv()
        val tile = game.tileMap.tileList.first()
        civ.viewableTiles = emptySet()

        val visibility = GlobeVisibilityPolicy.resolve(
            tile,
            GlobeVisibilityPolicy.Context(fogOfWarEnabled = true, viewingCiv = civ)
        )

        assertFalse(visibility.isExplored)
        assertFalse(visibility.isVisible)
        assertFalse(visibility.allowsInteraction)
        assertFalse(visibility.shouldRenderFogOverlay)
    }

    @Test
    fun exploredButNotVisibleTileReceivesFogOverlayState() {
        val game = TestGame()
        game.makeHexagonalMap(1)
        val civ = game.addCiv()
        val tile = game.tileMap.tileList.first()
        tile.setExplored(civ, true)
        civ.viewableTiles = emptySet()

        val visibility = GlobeVisibilityPolicy.resolve(
            tile,
            GlobeVisibilityPolicy.Context(fogOfWarEnabled = true, viewingCiv = civ)
        )

        assertTrue(visibility.isExplored)
        assertFalse(visibility.isVisible)
        assertTrue(visibility.allowsInteraction)
        assertTrue(visibility.shouldRenderFogOverlay)
    }

    @Test
    fun visibleTileRemainsFullyVisibleWhenExplored() {
        val game = TestGame()
        game.makeHexagonalMap(1)
        val civ = game.addCiv()
        val tile = game.tileMap.tileList.first()
        tile.setExplored(civ, true)
        civ.viewableTiles = setOf(tile)

        val visibility = GlobeVisibilityPolicy.resolve(
            tile,
            GlobeVisibilityPolicy.Context(fogOfWarEnabled = true, viewingCiv = civ)
        )

        assertTrue(visibility.isExplored)
        assertTrue(visibility.isVisible)
        assertTrue(visibility.allowsInteraction)
        assertFalse(visibility.shouldRenderFogOverlay)
    }
}
