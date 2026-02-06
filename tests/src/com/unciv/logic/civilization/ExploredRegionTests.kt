package com.unciv.logic.civilization

import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.mapgenerator.GoldbergMapBuilder
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max

@RunWith(GdxTestRunner::class)
class ExploredRegionTests {

    @Test
    fun hexExploredRegionTracksLatLong() {
        val testGame = TestGame()
        testGame.makeHexagonalMap(3)
        val civ = testGame.addCiv(isPlayer = true)
        civ.exploredRegion.setMapParameters(testGame.tileMap.mapParameters, testGame.tileMap)

        val first = testGame.tileMap[com.unciv.logic.map.HexCoord(1, 0)]
        val second = testGame.tileMap[com.unciv.logic.map.HexCoord(2, 0)]
        first.setExplored(civ, true, first.position)
        second.setExplored(civ, true, first.position)

        assertNull(civ.exploredRegion.getWorldBounds())
        assertEquals(2, civ.exploredRegion.getWidth())
        assertEquals(2, civ.exploredRegion.getHeight())
    }

    @Test
    fun icosahedronExploredRegionTracksWorldBounds() {
        val testGame = TestGame()
        val mapParameters = MapParameters().apply {
            shape = MapShape.icosahedron
            mapSize = MapSize.Tiny
        }
        val map = GoldbergMapBuilder.build(mapParameters, testGame.ruleset)
        map.gameInfo = testGame.gameInfo
        testGame.gameInfo.tileMap = map

        val civ = testGame.addCiv(isPlayer = true)
        civ.exploredRegion.setMapParameters(map.mapParameters, map)

        val first = map.tileList.first { tile ->
            val pos = map.topology.getWorldPosition(tile)
            pos.x != 0f || pos.y != 0f
        }
        val second = first.neighbors.firstOrNull { !map.topology.isSeamEdge(first, it) } ?: first.neighbors.first()
        first.setExplored(civ, true)
        second.setExplored(civ, true)

        val bounds = civ.exploredRegion.getWorldBounds()
        assertNotNull(bounds)
        val resolvedBounds = bounds!!

        val firstPos = map.topology.getWorldPosition(first)
        val secondPos = map.topology.getWorldPosition(second)
        val firstX = civ.exploredRegion.unwrapWorldLongitudeForRegion(firstPos.x)
        val secondX = civ.exploredRegion.unwrapWorldLongitudeForRegion(secondPos.x)

        assertTrue(firstX >= resolvedBounds.x && firstX <= resolvedBounds.x + resolvedBounds.width)
        assertTrue(firstPos.y >= resolvedBounds.y && firstPos.y <= resolvedBounds.y + resolvedBounds.height)
        assertTrue(secondX >= resolvedBounds.x && secondX <= resolvedBounds.x + resolvedBounds.width)
        assertTrue(secondPos.y >= resolvedBounds.y && secondPos.y <= resolvedBounds.y + resolvedBounds.height)

        val outside = map.values.firstOrNull { tile ->
            val pos = map.topology.getWorldPosition(tile)
            val x = civ.exploredRegion.unwrapWorldLongitudeForRegion(pos.x)
            x < resolvedBounds.x || x > resolvedBounds.x + resolvedBounds.width ||
                pos.y < resolvedBounds.y || pos.y > resolvedBounds.y + resolvedBounds.height
        }
        assertNotNull(outside)
        assertFalse(civ.exploredRegion.isPositionInRegion(outside!!.position))
    }

    @Test
    fun icosahedronSeamNeighborsDoNotExplodeExploredWidth() {
        val testGame = TestGame()
        val mapParameters = MapParameters().apply {
            shape = MapShape.icosahedron
            mapSize = MapSize.Tiny
        }
        val map = GoldbergMapBuilder.build(mapParameters, testGame.ruleset)
        map.gameInfo = testGame.gameInfo
        testGame.gameInfo.tileMap = map

        val civ = testGame.addCiv(isPlayer = true)
        civ.exploredRegion.setMapParameters(map.mapParameters, map)

        val seamStart = map.tileList.firstOrNull { tile ->
            tile.neighbors.any { map.topology.isSeamEdge(tile, it) }
        } ?: throw IllegalStateException("No seam edge found on Goldberg map")
        val seamNeighbor = seamStart.neighbors.first { map.topology.isSeamEdge(seamStart, it) }

        seamStart.setExplored(civ, true, seamStart.position)
        seamNeighbor.setExplored(civ, true, seamStart.position)

        val exploredBounds = civ.exploredRegion.getWorldBounds() ?: throw IllegalStateException("No world bounds")
        val mapBounds = map.topology.getWorldBounds()
        assertTrue(
            "Seam-adjacent exploration should stay local; got explored width=${exploredBounds.width}, map width=${mapBounds.width}",
            exploredBounds.width < mapBounds.width * 0.5f
        )
    }

    @Test
    fun icosahedronSequentialSeamExplorationKeepsMinimalCircularWidth() {
        val testGame = TestGame()
        val mapParameters = MapParameters().apply {
            shape = MapShape.icosahedron
            mapSize = MapSize.Tiny
        }
        val map = GoldbergMapBuilder.build(mapParameters, testGame.ruleset)
        map.gameInfo = testGame.gameInfo
        testGame.gameInfo.tileMap = map

        val civ = testGame.addCiv(isPlayer = true)
        civ.exploredRegion.setMapParameters(map.mapParameters, map, civ)

        val seamStart = map.tileList.firstOrNull { tile ->
            tile.neighbors.any { map.topology.isSeamEdge(tile, it) }
        } ?: throw IllegalStateException("No seam edge found on Goldberg map")

        val visited = LinkedHashSet<Tile>()
        val queue = ArrayDeque<Pair<Tile, Int>>()
        queue.add(seamStart to 0)
        visited.add(seamStart)
        while (queue.isNotEmpty()) {
            val (tile, distance) = queue.removeFirst()
            if (distance >= 3) continue
            for (neighbor in tile.neighbors) {
                if (visited.add(neighbor)) queue.add(neighbor to distance + 1)
            }
        }

        val byX = visited.sortedBy { map.topology.getWorldPosition(it).x }
        val explorationOrder = ArrayList<Tile>(byX.size)
        var left = 0
        var right = byX.lastIndex
        while (left <= right) {
            explorationOrder.add(byX[left++])
            if (left <= right) explorationOrder.add(byX[right--])
        }

        val period = map.topology.getWorldBounds().width
        for ((index, tile) in explorationOrder.withIndex()) {
            tile.setExplored(civ, true, seamStart.position)

            val exploredX = map.values.filter { it.isExplored(civ) }.map { map.topology.getWorldPosition(it).x }
            val expectedWidth = minimalCircularWidth(exploredX, period)
            val actualWidth = civ.exploredRegion.getWorldBounds()?.width ?: 0f

            assertTrue(
                "Step $index explored width should remain near minimal circular interval. expected<=${expectedWidth + 0.01f}, actual=$actualWidth",
                actualWidth <= expectedWidth + 0.01f
            )
        }
    }

    private fun minimalCircularWidth(values: List<Float>, period: Float): Float {
        if (values.isEmpty()) return 0f
        if (values.size == 1 || period <= 0f) return 0f

        val normalizedSorted = values
            .map {
                var value = it % period
                if (value < 0f) value += period
                value
            }
            .sorted()

        val n = normalizedSorted.size
        var maxGap = 0f
        for (i in 0 until n - 1) {
            maxGap = max(maxGap, normalizedSorted[i + 1] - normalizedSorted[i])
        }
        maxGap = max(maxGap, normalizedSorted[0] + period - normalizedSorted[n - 1])
        return period - maxGap
    }
}
