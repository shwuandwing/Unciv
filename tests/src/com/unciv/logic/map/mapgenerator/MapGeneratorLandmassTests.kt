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
}
