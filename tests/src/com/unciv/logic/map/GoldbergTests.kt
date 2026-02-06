package com.unciv.logic.map

import com.unciv.logic.map.mapgenerator.GoldbergMapBuilder
import com.unciv.logic.map.topology.GoldbergTopology
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import org.junit.Assert
import org.junit.Test
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

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
    fun testSeamEdgesGrowWithMapSizeAndStayMinority() {
        val ruleset = basicRuleset()
        var previousSeamEdgeCount = -1
        for (size in MapSize.Predefined.entries) {
            val map = GoldbergMapBuilder.build(
                MapParameters().apply {
                    shape = MapShape.icosahedron
                    mapSize = MapSize(size)
                },
                ruleset
            )
            val seamEdges = collectUndirectedSeamEdges(map)
            val totalUndirectedEdges = map.values.sumOf { it.neighbors.count() } / 2
            Assert.assertTrue("Expected non-zero seam edge count for ${size.name}", seamEdges.isNotEmpty())
            Assert.assertTrue(
                "Expected seam edges to remain minority for ${size.name}: seams=${seamEdges.size}, total=$totalUndirectedEdges",
                seamEdges.size < totalUndirectedEdges / 2
            )
            Assert.assertTrue(
                "Expected seam edge count to increase with map size, got ${seamEdges.size} after $previousSeamEdgeCount",
                seamEdges.size > previousSeamEdgeCount
            )
            previousSeamEdgeCount = seamEdges.size
        }
    }

    @Test
    fun testNonSeamWorldEdgeLengthsAreUniformForMedium() {
        val ruleset = basicRuleset()
        val map = GoldbergMapBuilder.build(
            MapParameters().apply {
                shape = MapShape.icosahedron
                mapSize = MapSize.Medium
            },
            ruleset
        )
        var minEdgeLength = Float.MAX_VALUE
        var maxEdgeLength = 0f
        var checkedEdges = 0
        for (tile in map.values) {
            val fromIndex = tile.zeroBasedIndex
            val fromPos = map.topology.getWorldPosition(tile)
            for (neighbor in tile.neighbors) {
                val toIndex = neighbor.zeroBasedIndex
                if (fromIndex >= toIndex) continue
                if (map.topology.isSeamEdge(tile, neighbor)) continue
                val toPos = map.topology.getWorldPosition(neighbor)
                val dx = fromPos.x - toPos.x
                val dy = fromPos.y - toPos.y
                val length = kotlin.math.sqrt(dx * dx + dy * dy)
                minEdgeLength = min(minEdgeLength, length)
                maxEdgeLength = max(maxEdgeLength, length)
                checkedEdges++
            }
        }

        Assert.assertTrue("Expected to check at least one non-seam edge", checkedEdges > 0)
        Assert.assertTrue("Expected strictly positive minimum non-seam edge length", minEdgeLength > 0f)
        Assert.assertTrue(
            "Non-seam world edge lengths should be near-uniform, got min=$minEdgeLength max=$maxEdgeLength",
            maxEdgeLength / minEdgeLength < 1.02f
        )
    }

    @Test
    fun testIcosaRenderTransformRoundTripsWorldCoords() {
        val ruleset = basicRuleset()
        val map = GoldbergMapBuilder.build(
            MapParameters().apply {
                shape = MapShape.icosahedron
                mapSize = MapSize.Tiny
            },
            ruleset
        )

        val sample = map.tileList.first { tile ->
            val pos = map.topology.getWorldPosition(tile)
            pos.x != 0f || pos.y != 0f
        }
        val worldPos = map.topology.getWorldPosition(sample)
        val renderPos = map.worldToRenderCoords(worldPos)
        val recovered = map.renderToWorldCoords(renderPos)

        Assert.assertEquals(worldPos.x.toDouble(), recovered.x.toDouble(), 1e-5)
        Assert.assertEquals(worldPos.y.toDouble(), recovered.y.toDouble(), 1e-5)
    }

    @Test
    fun testIcosaNeighborDirectionIsRotatedByThirtyDegreesInRenderSpace() {
        val ruleset = basicRuleset()
        val map = GoldbergMapBuilder.build(
            MapParameters().apply {
                shape = MapShape.icosahedron
                mapSize = MapSize.Tiny
            },
            ruleset
        )

        val from = map.tileList.first()
        val to = from.neighbors.first { !map.topology.isSeamEdge(from, it) }
        val fromWorld = map.topology.getWorldPosition(from)
        val toWorld = map.topology.getWorldPosition(to)
        val worldAngle = atan2((toWorld.y - fromWorld.y), (toWorld.x - fromWorld.x))

        val renderVector = map.getNeighborTilePositionAsWorldCoords(from, to)
        val renderAngle = atan2(renderVector.y, renderVector.x)
        val delta = normalizeAngle(renderAngle - worldAngle)

        Assert.assertEquals(
            "Expected render-space neighbor direction to be rotated by +30 degrees",
            (Math.PI / 6.0),
            delta.toDouble(),
            0.03
        )
    }

    @Test
    fun testIcosaDebugFaceMappingHasLabelForEveryFace() {
        val map = GoldbergMapBuilder.build(
            MapParameters().apply {
                shape = MapShape.icosahedron
                mapSize = MapSize.Tiny
            },
            basicRuleset()
        )
        val topology = map.topology as GoldbergTopology
        for (tile in map.values) {
            Assert.assertTrue("Tile ${tile.zeroBasedIndex} has invalid face id", topology.getPrimaryFaceForDebug(tile) >= 0)
        }
        for (face in 0 until 20) {
            val labelIndex = topology.getFaceLabelTileForDebug(face)
            Assert.assertTrue("Missing debug label tile for face $face", labelIndex >= 0)
            val labelTile = map.tileList[labelIndex]
            Assert.assertEquals("Face label tile must belong to its face", face, topology.getPrimaryFaceForDebug(labelTile))
        }
    }

    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle
        val pi = Math.PI.toFloat()
        val tau = (2 * Math.PI).toFloat()
        while (normalized <= -pi) normalized += tau
        while (normalized > pi) normalized -= tau
        return normalized.absoluteValue
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
