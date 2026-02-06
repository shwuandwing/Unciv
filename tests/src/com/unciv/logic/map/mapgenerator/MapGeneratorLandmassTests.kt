package com.unciv.logic.map.mapgenerator

import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.logic.map.TileMap
import com.unciv.models.ruleset.tile.TerrainType
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import com.unciv.ui.screens.mapeditorscreen.MapGeneratorSteps
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class MapGeneratorLandmassTests {
    private val threeContinentsMajorShareThreshold = 0.15f

    @Test
    fun twoContinentsIcosahedronKeepsWaterInFullGeneration() {
        val testGame = TestGame()
        val generator = MapGenerator(testGame.ruleset)
        val waterPercents = mutableListOf<Float>()

        for (seed in 1L..10L) {
            val mapParameters = MapParameters().apply {
                shape = MapShape.icosahedron
                type = MapType.twoContinents
                mapSize = MapSize.Small
                this.seed = seed
            }
            val map = generator.generateMap(mapParameters)
            val waterCount = map.values.count {
                testGame.ruleset.terrains[it.baseTerrain]?.type == TerrainType.Water
            }
            waterPercents += waterCount.toFloat() / map.values.size.toFloat()
        }

        val min = waterPercents.minOrNull() ?: 0f
        val max = waterPercents.maxOrNull() ?: 0f
        assertTrue("Two Continents on icosa should include water (min ratio=$min, all=$waterPercents)", min > 0.02f)
        assertTrue("Two Continents on icosa should include land (max ratio=$max, all=$waterPercents)", max < 0.98f)
    }

    @Test
    fun twoContinentsIcosahedronKeepsWaterInLandmassStepGeneration() {
        val testGame = TestGame()
        val generator = MapGenerator(testGame.ruleset)
        val waterPercents = mutableListOf<Float>()

        for (seed in 1L..10L) {
            val mapParameters = MapParameters().apply {
                shape = MapShape.icosahedron
                type = MapType.twoContinents
                mapSize = MapSize.Small
                this.seed = seed
            }
            val map = GoldbergMapBuilder.build(mapParameters, testGame.ruleset)
            map.gameInfo = testGame.gameInfo
            map.mapParameters = mapParameters
            map.setTransients(testGame.ruleset)

            generator.generateSingleStep(map, MapGeneratorSteps.Landmass)

            val waterCount = map.values.count {
                testGame.ruleset.terrains[it.baseTerrain]?.type == TerrainType.Water
            }
            waterPercents += waterCount.toFloat() / map.values.size.toFloat()
        }

        val min = waterPercents.minOrNull() ?: 0f
        val max = waterPercents.maxOrNull() ?: 0f
        assertTrue("Landmass step Two Continents on icosa should include water (min ratio=$min, all=$waterPercents)", min > 0.02f)
        assertTrue("Landmass step Two Continents on icosa should include land (max ratio=$max, all=$waterPercents)", max < 0.98f)
    }

    @Test
    fun threeContinentsIcosahedronHasThreeMajorContinentsInFullGeneration() {
        val testGame = TestGame()
        val generator = MapGenerator(testGame.ruleset)
        val majorCounts = mutableListOf<Int>()
        val largestShares = mutableListOf<Float>()
        val sizeSnapshots = mutableListOf<List<Int>>()

        for (seed in 1L..20L) {
            val mapParameters = MapParameters().apply {
                shape = MapShape.icosahedron
                type = MapType.threeContinents
                mapSize = MapSize.Small
                this.seed = seed
            }
            val map = generator.generateMap(mapParameters)
            val continentSizes = map.continentSizes.values.sortedDescending()
            val totalLand = continentSizes.sum().coerceAtLeast(1)
            val majorThreshold = (totalLand * threeContinentsMajorShareThreshold).toInt().coerceAtLeast(8)
            majorCounts += continentSizes.count { it >= majorThreshold }
            sizeSnapshots += continentSizes.take(6)
            val largest = continentSizes.firstOrNull() ?: 0
            largestShares += largest.toFloat() / totalLand.toFloat()
        }

        val minMajorContinents = majorCounts.minOrNull() ?: 0
        val maxLargestShare = largestShares.maxOrNull() ?: 1f
        assertTrue(
            "Three Continents on icosa should keep at least 3 major continents (majorCounts=$majorCounts, sizes=$sizeSnapshots)",
            minMajorContinents >= 3
        )
        assertTrue(
            "Three Continents on icosa should not collapse into one dominant continent (largestShares=$largestShares, sizes=$sizeSnapshots)",
            maxLargestShare < 0.55f
        )
    }

    @Test
    fun threeContinentsIcosahedronHasThreeMajorContinentsInLandmassStep() {
        val testGame = TestGame()
        val generator = MapGenerator(testGame.ruleset)
        val majorCounts = mutableListOf<Int>()
        val sizeSnapshots = mutableListOf<List<Int>>()

        for (seed in 1L..20L) {
            val mapParameters = MapParameters().apply {
                shape = MapShape.icosahedron
                type = MapType.threeContinents
                mapSize = MapSize.Small
                this.seed = seed
            }
            val map = GoldbergMapBuilder.build(mapParameters, testGame.ruleset)
            map.gameInfo = testGame.gameInfo
            map.mapParameters = mapParameters
            map.setTransients(testGame.ruleset)

            generator.generateSingleStep(map, MapGeneratorSteps.Landmass)
            map.assignContinents(TileMap.AssignContinentsMode.Assign)

            val continentSizes = map.continentSizes.values.sortedDescending()
            val totalLand = continentSizes.sum().coerceAtLeast(1)
            val majorThreshold = (totalLand * threeContinentsMajorShareThreshold).toInt().coerceAtLeast(8)
            majorCounts += continentSizes.count { it >= majorThreshold }
            sizeSnapshots += continentSizes.take(6)
        }

        val minMajorContinents = majorCounts.minOrNull() ?: 0
        assertTrue(
            "Three Continents landmass step on icosa should keep at least 3 major continents (majorCounts=$majorCounts, sizes=$sizeSnapshots)",
            minMajorContinents >= 3
        )
    }
}
