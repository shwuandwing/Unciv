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
class BorderEdgeGeometryTests {
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
    fun firstFoundingDoesNotCreateCityCenterBorderEdges() {
        val borderNeighbors = cityCenter.neighbors.filter { shouldDrawBorder(cityCenter, it) }
        assertTrue("City center should have no border edges after first founding", borderNeighbors.none())
    }

    @Test
    fun firstRingBorderEdgesExcludeCityCenterAndFaceBoundaryNeighbor() {
        val ownedRingTiles = cityCenter.neighbors.filter { it.getOwner() == civ }.toList()
        assertEquals(6, ownedRingTiles.size)

        for (ownedTile in ownedRingTiles) {
            val borderNeighbors = ownedTile.neighbors.filter { shouldDrawBorder(ownedTile, it) }
            assertFalse("Owned first-ring tile should have external border neighbors", borderNeighbors.none())
            assertFalse(
                "Border edges should never be drawn toward city center tile",
                borderNeighbors.any { it == cityCenter }
            )

            for (neighbor in borderNeighbors) {
                val angleDirection = getWorldDirection(ownedTile, neighbor)
                val renderDirection = tileMap.getNeighborTilePositionAsWorldCoords(ownedTile, neighbor)
                assertTrue(
                    "Border segment should face its boundary neighbor, not inward",
                    BorderEdgeGeometry.isSegmentFacingNeighbor(
                        angleDirection = angleDirection,
                        neighborDirectionInRenderSpace = renderDirection,
                        baseImageRotationDegrees = 30f
                    )
                )

                val offset = BorderEdgeGeometry.getOffsetTowardsNeighbor(renderDirection, 1f)
                assertTrue(
                    "Border offset should move toward boundary neighbor",
                    offset.dot(renderDirection) > 0f
                )
            }
        }
    }

    private fun shouldDrawBorder(tile: Tile, neighbor: Tile): Boolean {
        val tileOwner = tile.getOwner()
        val neighborOwner = neighbor.getOwner()
        return tileOwner != null && tileOwner != neighborOwner
    }

    private fun getWorldDirection(from: Tile, to: Tile): Vector2 {
        val fromWorld = tileMap.topology.getWorldPosition(from)
        val toWorld = tileMap.topology.getWorldPosition(to)
        return Vector2(toWorld.x - fromWorld.x, toWorld.y - fromWorld.y)
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
