package com.unciv.ui.render.globe

import com.unciv.logic.city.managers.CityFounder
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.GoldbergMapBuilder
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import com.unciv.ui.components.tilegroups.layers.OwnershipBorderSegmentResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class GlobeOwnershipSegmentCachePolicyTests {
    private lateinit var testGame: TestGame
    private lateinit var tileMap: TileMap
    private lateinit var civA: Civilization

    @Before
    fun setUp() {
        testGame = TestGame()
        tileMap = makeIcosahedronMap(testGame, frequency = 2)
        civA = testGame.addCiv()

        val cityTile = tileMap.values.first {
            it.neighbors.count() == 6 && !it.isWater && it.neighbors.all { neighbor -> !neighbor.isWater }
        }
        CityFounder().foundCity(civA, cityTile.position)
    }

    @Test
    fun signatureChangesWhenSharedNeighborOwnerChanges() {
        val ownedTile = tileMap.values.first {
            it.getOwner() == civA && OwnershipBorderSegmentResolver.resolve(it).isNotEmpty()
        }
        val segment = OwnershipBorderSegmentResolver.resolve(ownedTile).firstOrNull { candidate ->
            sharedNeighbors(ownedTile, candidate.neighbor).isNotEmpty()
        } ?: throw IllegalStateException("Expected at least one segment with a shared neighbor")
        val sharedNeighbor = sharedNeighbors(ownedTile, segment.neighbor).first()

        val civB = testGame.addCiv()
        val protectedTiles = buildSet {
            add(ownedTile)
            add(segment.neighbor)
            add(sharedNeighbor)
            addAll(ownedTile.neighbors.toList())
            addAll(segment.neighbor.neighbors.toList())
        }
        val civBCityTile = tileMap.values.first { tile ->
            !tile.isWater &&
                tile.getOwner() == null &&
                tile !in protectedTiles
        }
        val civBCity = CityFounder().foundCity(civB, civBCityTile.position)

        val tileOwnerBefore = ownedTile.getOwner()
        val neighborOwnerBefore = segment.neighbor.getOwner()
        val before = GlobeOwnershipSegmentCachePolicy.signature(ownedTile)

        sharedNeighbor.setOwningCity(civBCity)

        assertEquals(tileOwnerBefore, ownedTile.getOwner())
        assertEquals(neighborOwnerBefore, segment.neighbor.getOwner())

        val after = GlobeOwnershipSegmentCachePolicy.signature(ownedTile)
        assertNotEquals(
            "Shared-neighbor ownership must invalidate ownership border segment cache",
            before,
            after
        )
    }

    private fun sharedNeighbors(tile: Tile, neighbor: Tile): List<Tile> {
        val tileNeighborSet = tile.neighbors.toHashSet()
        return neighbor.neighbors
            .filter { candidate -> candidate != tile && candidate != neighbor && candidate in tileNeighborSet }
            .toList()
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
