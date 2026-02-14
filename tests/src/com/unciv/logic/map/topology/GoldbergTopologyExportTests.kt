package com.unciv.logic.map.topology

import com.unciv.logic.map.GoldbergMath
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import org.junit.Assert
import org.junit.Test

class GoldbergTopologyExportTests {

    private fun basicRuleset(): Ruleset {
        val ruleset = Ruleset()
        ruleset.terrains["Plains"] = Terrain().apply {
            name = "Plains"
            type = TerrainType.Land
        }
        return ruleset
    }

    @Test
    fun exportHasExpectedTileCountAndDegrees() {
        val frequency = 3
        val dump = GoldbergTopologyDumpBuilder.buildDump(frequency, basicRuleset())

        Assert.assertEquals(GoldbergMath.tileCount(frequency), dump.tileCount)
        Assert.assertEquals(dump.tileCount, dump.tiles.size)

        val degreeCounts = dump.tiles.groupingBy { it.neighbors.size }.eachCount()
        Assert.assertEquals(12, degreeCounts[5])
        Assert.assertEquals(dump.tileCount - 12, degreeCounts[6])
    }

    @Test
    fun exportProvidesCanonicalRiverWriterPerUndirectedEdge() {
        val dump = GoldbergTopologyDumpBuilder.buildDump(2, basicRuleset())
        val seenPairs = HashSet<Pair<Int, Int>>()

        for (edge in dump.edges) {
            val pair = if (edge.a < edge.b) edge.a to edge.b else edge.b to edge.a
            Assert.assertTrue("Duplicate edge pair in dump: $pair", seenPairs.add(pair))
            if (!edge.representable) continue

            val writer = edge.writer
            Assert.assertNotNull("Representable edge missing writer for pair $pair", writer)
            val writerValue = writer!!
            Assert.assertTrue(
                "Writer tile index must be one endpoint for pair $pair",
                writerValue.tileIndex == edge.a || writerValue.tileIndex == edge.b
            )
            Assert.assertTrue(
                "Writer field must be a known river field: ${writerValue.field}",
                writerValue.field in setOf("hasBottomRiver", "hasBottomLeftRiver", "hasBottomRightRiver")
            )
        }

        val undirectedEdgeCountByNeighbors = dump.tiles.sumOf { it.neighbors.size } / 2
        Assert.assertEquals(undirectedEdgeCountByNeighbors, dump.edges.size)
    }
}
