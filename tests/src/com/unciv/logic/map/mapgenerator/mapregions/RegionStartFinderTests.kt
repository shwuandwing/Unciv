package com.unciv.logic.map.mapgenerator.mapregions

import com.badlogic.gdx.math.Rectangle
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class RegionStartFinderTests {

    @Test
    fun fallbackStartDoesNotCrashForEmptyRegion() {
        val testGame = TestGame()
        testGame.makeHexagonalMap(2)
        val tileMap = testGame.tileMap

        val region = Region(tileMap, Rectangle(99999f, 99999f, 1f, 1f), -1)
        region.updateTiles()

        val tileData = TileDataMap()
        for (tile in tileMap.values) {
            tileData[tile.position] = MapGenTileData(tile, region, testGame.ruleset)
        }

        val methods = RegionStartFinder::class.java.declaredMethods
        val method = methods.firstOrNull { candidate ->
            candidate.name.startsWith("findStart") &&
                candidate.parameterTypes.size == 2 &&
                candidate.parameterTypes[0] == Region::class.java &&
                candidate.parameterTypes[1] == TileDataMap::class.java
        } ?: throw NoSuchMethodException("findStart")
        method.isAccessible = true
        method.invoke(RegionStartFinder, region, tileData)

        val start = region.startPosition
        assertNotNull(start)
        assertTrue(tileMap.getIfTileExistsOrNull(start!!.x, start.y) != null)
    }
}
