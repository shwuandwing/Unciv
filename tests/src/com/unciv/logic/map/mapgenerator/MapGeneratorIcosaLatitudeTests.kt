package com.unciv.logic.map.mapgenerator

import com.unciv.Constants
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(GdxTestRunner::class)
class MapGeneratorIcosaLatitudeTests {

    @Test
    fun icosaIceDistributionBiasesTowardTopAndBottomOfNet() {
        val testGame = TestGame()
        val generator = MapGenerator(testGame.ruleset)

        var polarWaterTiles = 0
        var polarIceTiles = 0
        var midWaterTiles = 0
        var midIceTiles = 0

        for (seed in 1L..10L) {
            val mapParameters = MapParameters().apply {
                shape = MapShape.icosahedron
                type = MapType.pangaea
                mapSize = MapSize.Small
                this.seed = seed
            }
            val map = generator.generateMap(mapParameters)
            val minY = map.values.minOf { it.position.y }
            val maxY = map.values.maxOf { it.position.y }
            val centerY = (minY + maxY) / 2.0

            for (tile in map.values) {
                val terrainType = testGame.ruleset.terrains[tile.baseTerrain]?.type ?: continue
                if (terrainType != TerrainType.Water) continue

                val y = tile.position.y.toDouble()
                val inPolarBand = y <= minY + 2 || y >= maxY - 2
                val inMidBand = abs(y - centerY) <= 2.0
                val hasIce = tile.terrainFeatures.contains(Constants.ice)

                if (inPolarBand) {
                    polarWaterTiles++
                    if (hasIce) polarIceTiles++
                }
                if (inMidBand) {
                    midWaterTiles++
                    if (hasIce) midIceTiles++
                }
            }
        }

        assertTrue("Expected polar water sample set to be non-empty", polarWaterTiles > 0)
        assertTrue("Expected mid-latitude water sample set to be non-empty", midWaterTiles > 0)

        val polarIceRate = polarIceTiles.toDouble() / polarWaterTiles.toDouble()
        val midIceRate = midIceTiles.toDouble() / midWaterTiles.toDouble()
        assertTrue(
            "Expected polar-band ice rate to exceed mid-band ice rate (polar=$polarIceRate mid=$midIceRate)",
            polarIceRate > midIceRate + 0.02
        )
    }

    @Test
    fun naturalWonderLatitudeConstraintUsesNetOrientedLatitude() {
        val testGame = TestGame()
        val mapParameters = MapParameters().apply {
            shape = MapShape.icosahedron
            mapSize = MapSize.Small
            type = MapType.pangaea
            seed = 3L
        }
        val map = GoldbergMapBuilder.build(mapParameters, testGame.ruleset)
        map.gameInfo = testGame.gameInfo
        map.mapParameters = mapParameters
        map.setTransients(testGame.ruleset)

        val topCenterTile = selectTopCenterTile(map.values.toList())
        val centerTile = selectCenterTile(map.values.toList())
        assertTrue(abs(topCenterTile.latitude) > abs(centerTile.latitude))

        val latitudeBoundWonder = Terrain().apply {
            name = "LatitudeBoundWonder"
            type = TerrainType.NaturalWonder
            occursOn.add(topCenterTile.baseTerrain)
            uniques.add("Occurs on latitudes from [45] to [100] percent of distance equator to pole")
        }

        assertTrue(
            "Top-center tile should satisfy high-latitude wonder constraint",
            NaturalWonderGenerator.fitsTerrainUniques(latitudeBoundWonder, topCenterTile)
        )
        assertFalse(
            "Center tile should not satisfy high-latitude wonder constraint",
            NaturalWonderGenerator.fitsTerrainUniques(latitudeBoundWonder, centerTile)
        )
    }

    private fun selectTopCenterTile(tiles: List<Tile>): Tile {
        val minY = tiles.minOf { it.position.y }
        val centerX = (tiles.minOf { it.position.x } + tiles.maxOf { it.position.x }) / 2.0
        return tiles.asSequence()
            .filter { it.position.y == minY }
            .minWithOrNull(
                compareBy<Tile> { abs(it.position.x - centerX) }
                    .thenBy { it.position.x }
                    .thenBy { it.position.y }
            )
            ?: error("No top-row tile found")
    }

    private fun selectCenterTile(tiles: List<Tile>): Tile {
        val centerX = (tiles.minOf { it.position.x } + tiles.maxOf { it.position.x }) / 2.0
        val centerY = (tiles.minOf { it.position.y } + tiles.maxOf { it.position.y }) / 2.0
        return tiles.minWithOrNull(
            compareBy<Tile> {
                val dx = it.position.x - centerX
                val dy = it.position.y - centerY
                dx * dx + dy * dy
            }.thenBy { it.position.x }.thenBy { it.position.y }
        ) ?: error("No center tile found")
    }
}
