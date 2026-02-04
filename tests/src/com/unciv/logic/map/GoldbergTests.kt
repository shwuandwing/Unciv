package com.unciv.logic.map

import com.unciv.logic.map.mapgenerator.GoldbergMapBuilder
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import org.junit.Assert
import org.junit.Test

class GoldbergTests {

    private fun basicRuleset(): Ruleset {
        val ruleset = Ruleset()
        ruleset.terrains["Plains"] = Terrain().apply {
            name = "Plains"
            type = TerrainType.Land
        }
        return ruleset
    }

    @Test
    fun testGoldbergTileCountAndDegrees() {
        val ruleset = basicRuleset()
        val mapParameters = MapParameters().apply {
            shape = MapShape.icosahedron
            mapSize = MapSize.Medium
        }
        val map = GoldbergMapBuilder.build(mapParameters, ruleset)
        val expectedCount = GoldbergMath.tileCount(GoldbergFrequency.selectForMapSize(mapParameters.mapSize))
        Assert.assertEquals(expectedCount, map.values.size)
        Assert.assertEquals(expectedCount, map.values.map { it.position }.toSet().size)

        val degreeCounts = map.values.groupingBy { it.neighbors.count() }.eachCount()
        Assert.assertEquals(12, degreeCounts[5])
        Assert.assertEquals(expectedCount - 12, degreeCounts[6])
    }

    @Test
    fun testGoldbergTileCountIncreasesWithPredefinedMapSizes() {
        var previousTileCount = -1
        for (size in MapSize.Predefined.entries) {
            val mapSize = MapSize(size)
            val frequency = GoldbergFrequency.selectForMapSize(mapSize)
            val tileCount = GoldbergMath.tileCount(frequency)
            Assert.assertTrue(
                "Expected tile count to increase for ${size.name}, got $tileCount after $previousTileCount",
                tileCount > previousTileCount
            )
            previousTileCount = tileCount
        }
    }

    @Test
    fun testGoldbergTileCountRoughlyMatchesHexagonalByPredefinedSize() {
        for (size in MapSize.Predefined.entries) {
            val mapSize = MapSize(size)
            val frequency = GoldbergFrequency.selectForMapSize(mapSize)
            val goldbergTileCount = GoldbergMath.tileCount(frequency)
            val hexTileCount = 1 + 3 * mapSize.radius * (mapSize.radius - 1)
            val ratio = goldbergTileCount.toFloat() / hexTileCount.toFloat()
            Assert.assertTrue(
                "Expected ${size.name} Goldberg tiles to be within 15% of hex count, got ratio=$ratio ($goldbergTileCount/$hexTileCount)",
                ratio in 0.85f..1.15f
            )
        }
    }

    @Test
    fun testGoldbergMapBuilderRespectsMapSizeChangesOnSameParametersObject() {
        val ruleset = basicRuleset()
        val mapParameters = MapParameters().apply { shape = MapShape.icosahedron }
        val sizes = listOf(MapSize.Tiny, MapSize.Medium, MapSize.Huge)

        var previousTileCount = -1
        for (size in sizes) {
            mapParameters.mapSize = size
            val map = GoldbergMapBuilder.build(mapParameters, ruleset)
            val tileCount = map.values.size
            Assert.assertEquals(
                GoldbergMath.tileCount(GoldbergFrequency.selectForMapSize(size)),
                tileCount
            )
            Assert.assertTrue(
                "Expected generated tile count to increase with map size, got $tileCount after $previousTileCount",
                tileCount > previousTileCount
            )
            previousTileCount = tileCount
        }
    }

    @Test
    fun testSeamEdgeCountMatchesFrequencyFormula() {
        val ruleset = basicRuleset()
        for (size in MapSize.Predefined.entries) {
            val map = GoldbergMapBuilder.build(
                MapParameters().apply {
                    shape = MapShape.icosahedron
                    mapSize = MapSize(size)
                },
                ruleset
            )
            val frequency = map.mapParameters.goldbergFrequency
            val seamEdges = collectUndirectedSeamEdges(map)
            val expected = 10 * frequency * frequency + 11 * frequency - 4
            Assert.assertEquals(
                "Unexpected seam edge count for ${size.name} (f=$frequency)",
                expected,
                seamEdges.size
            )
        }
    }

    @Test
    fun testSeamDegreeDistributionStableForMedium() {
        val ruleset = basicRuleset()
        val map = GoldbergMapBuilder.build(
            MapParameters().apply {
                shape = MapShape.icosahedron
                mapSize = MapSize.Medium
            },
            ruleset
        )
        val degreeDist = map.values
            .map { tile -> tile.neighbors.count { map.topology.isSeamEdge(tile, it) } }
            .groupingBy { it }
            .eachCount()
            .toSortedMap()
        Assert.assertEquals(mapOf(2 to 991, 3 to 212, 4 to 9), degreeDist)
    }

    private fun collectUndirectedSeamEdges(map: TileMap): List<Pair<Int, Int>> {
        val edges = ArrayList<Pair<Int, Int>>()
        for (tile in map.values) {
            val from = tile.zeroBasedIndex
            for (neighbor in tile.neighbors) {
                val to = neighbor.zeroBasedIndex
                if (from >= to) continue
                if (map.topology.isSeamEdge(tile, neighbor)) {
                    edges.add(from to to)
                }
            }
        }
        return edges
    }

}
