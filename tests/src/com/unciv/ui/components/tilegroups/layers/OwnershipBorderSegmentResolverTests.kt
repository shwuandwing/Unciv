package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.city.managers.CityFounder
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.GoldbergMapBuilder
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class OwnershipBorderSegmentResolverTests {
    private lateinit var testGame: TestGame
    private lateinit var tileMap: TileMap
    private lateinit var civ: Civilization
    private lateinit var cityCenter: Tile

    @Before
    fun setUp() {
        testGame = TestGame()
        tileMap = makeIcosahedronMap(testGame, frequency = 2)
        civ = testGame.addCiv()
        val cityTile = tileMap.values.first {
            it.neighbors.count() == 6 && !it.isWater && it.neighbors.all { neighbor -> !neighbor.isWater }
        }
        cityCenter = CityFounder().foundCity(civ, cityTile.position).getCenterTile()
    }

    @Test
    fun `border shape string mapping stays stable`() {
        assertEquals("Concave", OwnershipBorderSegmentResolver.borderShapeString(isLeftConcave = true, isRightConcave = true))
        assertEquals("Convex", OwnershipBorderSegmentResolver.borderShapeString(isLeftConcave = false, isRightConcave = false))
        assertEquals("ConvexConcave", OwnershipBorderSegmentResolver.borderShapeString(isLeftConcave = false, isRightConcave = true))
        assertEquals("ConcaveConvex", OwnershipBorderSegmentResolver.borderShapeString(isLeftConcave = true, isRightConcave = false))
    }

    @Test
    fun `first founding produces no city-center border segments`() {
        assertTrue(OwnershipBorderSegmentResolver.resolve(cityCenter).isEmpty())

        val ownedRingTiles = cityCenter.neighbors.filter { it.getOwner() == civ }.toList()
        assertEquals(6, ownedRingTiles.size)

        for (ownedTile in ownedRingTiles) {
            val segments = OwnershipBorderSegmentResolver.resolve(ownedTile)
            assertFalse("Owned first-ring tile should have external border segments", segments.isEmpty())
            assertFalse(
                "Border segments should never point to the city-center tile",
                segments.any { it.neighbor == cityCenter }
            )
        }
    }

    @Test
    fun `icosa angle direction follows main-map convention`() {
        val ownedRingTiles = cityCenter.neighbors.filter { it.getOwner() == civ }.toList()
        assertEquals(6, ownedRingTiles.size)

        for (ownedTile in ownedRingTiles) {
            for (segment in OwnershipBorderSegmentResolver.resolve(ownedTile)) {
                val tileWorldPosition = tileMap.topology.getWorldPosition(ownedTile)
                val neighborWorldPosition = tileMap.topology.getWorldPosition(segment.neighbor)
                val tileToNeighborWorldDirection = Vector2(
                    neighborWorldPosition.x - tileWorldPosition.x,
                    neighborWorldPosition.y - tileWorldPosition.y
                )
                assertTrue("Angle direction must be non-zero", !segment.angleDirection.isZero)
                assertTrue(
                    "Main-map icosa angle direction should oppose tile->neighbor world direction",
                    segment.angleDirection.dot(tileToNeighborWorldDirection) < 0f
                )
            }
        }
    }

    private fun makeIcosahedronMap(testGame: TestGame, frequency: Int): TileMap {
        val mapParameters = MapParameters().apply {
            shape = MapShape.icosahedron
            goldbergFrequency = frequency
        }
        val map = GoldbergMapBuilder.build(mapParameters, testGame.ruleset)
        map.gameInfo = testGame.gameInfo
        testGame.gameInfo.tileMap = map
        return map
    }
}

