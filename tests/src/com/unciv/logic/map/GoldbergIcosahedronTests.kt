package com.unciv.logic.map

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapgenerator.GoldbergMapBuilder
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class GoldbergIcosahedronTests {

    private lateinit var testGame: TestGame
    private lateinit var tileMap: TileMap
    private lateinit var civInfo: Civilization

    @Before
    fun setUp() {
        testGame = TestGame()
        tileMap = makeIcosahedronMap(testGame, 2)
        civInfo = testGame.addCiv()
        for (tile in tileMap.values) tile.setExplored(civInfo, true)
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

    private fun findSeamEdge(map: TileMap): Pair<Tile, Tile> {
        for (tile in map.values) {
            for (neighbor in tile.neighbors) {
                if (map.topology.isSeamEdge(tile, neighbor)) return tile to neighbor
            }
        }
        throw AssertionError("No seam edge found in icosahedron map")
    }

    private fun findFarthestFrom(start: Tile, map: TileMap): Tile {
        var best = start
        var bestDistance = -1
        for (tile in map.values) {
            val distance = map.topology.getDistance(start, tile)
            if (distance > bestDistance) {
                bestDistance = distance
                best = tile
            }
        }
        return best
    }

    @Test
    fun seamEdgesAreAdjacentButNonLocalInNet() {
        val (from, to) = findSeamEdge(tileMap)
        Assert.assertTrue(from.neighbors.any { it == to })
        Assert.assertTrue(to.neighbors.any { it == from })
        Assert.assertTrue(tileMap.topology.isSeamEdge(from, to))
        Assert.assertEquals(1, tileMap.topology.getDistance(from, to))
        Assert.assertTrue(HexMath.getDistance(from.position, to.position) > 1)
    }

    @Test
    fun pathfindingCrossesSeamEdges() {
        val (from, to) = findSeamEdge(tileMap)
        val path = MapPathing.getConnection(civInfo, from, to, predicate = { _, _ -> true })
        Assert.assertNotNull(path)
        val resolved = path!!
        Assert.assertEquals(2, resolved.size)
        Assert.assertEquals(from, resolved.first())
        Assert.assertEquals(to, resolved.last())
        Assert.assertTrue(tileMap.topology.isSeamEdge(resolved[0], resolved[1]))
    }

    @Test
    fun unitMovementCrossesSeamEdges() {
        val (from, to) = findSeamEdge(tileMap)
        val unit = testGame.addUnit("Warrior", civInfo, from)
        unit.currentMovement = 10f
        unit.movement.moveToTile(to)
        Assert.assertEquals(to, unit.getTile())
    }

    @Test
    fun pathfindingMatchesTopologyDistance() {
        val start = tileMap.tileList.first()
        val end = findFarthestFrom(start, tileMap)
        val path = MapPathing.getConnection(civInfo, start, end, predicate = { _, _ -> true })
        Assert.assertNotNull(path)
        val resolved = path!!
        val distance = tileMap.topology.getDistance(start, end)
        Assert.assertEquals(distance + 1, resolved.size)
    }
}
