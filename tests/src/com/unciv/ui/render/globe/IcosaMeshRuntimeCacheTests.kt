package com.unciv.ui.render.globe

import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.mapgenerator.GoldbergMapBuilder
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import org.junit.Assert
import org.junit.Test
import kotlin.math.abs

class IcosaMeshRuntimeCacheTests {

    private fun basicRuleset(): Ruleset {
        val ruleset = Ruleset()
        ruleset.terrains["Plains"] = Terrain().apply {
            name = "Plains"
            type = TerrainType.Land
        }
        return ruleset
    }

    @Test
    fun cacheBuildsCentersNeighborRingsAndCornersForEveryTile() {
        val map = GoldbergMapBuilder.build(
            MapParameters().apply {
                shape = MapShape.icosahedron
                mapSize = MapSize.Tiny
            },
            basicRuleset()
        )

        val cache = IcosaMeshRuntimeCache.from(map)
        Assert.assertEquals(map.tileList.size, cache.centers.size)
        Assert.assertEquals(map.tileList.size, cache.normals.size)
        Assert.assertEquals(map.tileList.size, cache.neighborRings.size)
        Assert.assertEquals(map.tileList.size, cache.cornerRings.size)

        for (tile in map.tileList) {
            val index = tile.zeroBasedIndex
            val center = cache.centers[index]
            val normal = cache.normals[index]

            Assert.assertEquals("Expected center on unit sphere", 1.0, center.len().toDouble(), 1e-4)
            Assert.assertEquals("Expected normal on unit sphere", 1.0, normal.len().toDouble(), 1e-4)
            Assert.assertTrue("Expected center and normal to align", center.dot(normal) > 0.999f)

            val ring = cache.neighborRings[index]
            val neighbors = tile.neighbors.map { it.zeroBasedIndex }.toSet()
            Assert.assertEquals("Expected ring size to match degree", neighbors.size, ring.size)
            Assert.assertEquals("Expected ring to contain the same neighbors", neighbors, ring.toSet())
            Assert.assertEquals("Expected no duplicate neighbors in ring", ring.size, ring.toSet().size)

            val corners = cache.cornerRings[index]
            Assert.assertEquals("Expected one corner per edge", ring.size, corners.size)
            for (corner in corners) {
                Assert.assertEquals("Expected corners on unit sphere", 1.0, corner.len().toDouble(), 1e-4)
            }

            var winding = 0f
            for (cornerIndex in corners.indices) {
                val a = corners[cornerIndex]
                val b = corners[(cornerIndex + 1) % corners.size]
                winding += center.dot(a.cpy().crs(b))
            }
            Assert.assertTrue(
                "Expected a non-degenerate corner winding for tile $index, got $winding",
                abs(winding) > 0.05f
            )
        }
    }
}
