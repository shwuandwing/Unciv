package com.unciv.logic.map

import com.badlogic.gdx.math.Rectangle
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class TileMapTests {

    val testGame = TestGame()

    @Before
    fun init() {
        testGame.makeHexagonalMap(5)
    }

    @Test
    fun testGetTilesAtDistance() {
        val origin = HexCoord.Zero
        val tilesAtDistance1 = testGame.tileMap.getTilesAtDistance(origin, 1).toList()
        Assert.assertEquals(6, tilesAtDistance1.size)
        
        val expectedCoords = listOf(
            HexCoord.of(-1, -1), HexCoord.of(1, 1),
            HexCoord.of(0, -1), HexCoord.of(0, 1),
            HexCoord.of(1, 0), HexCoord.of(-1, 0)
        )
        val actualCoords = tilesAtDistance1.map { it.position.toHexCoord() }
        Assert.assertTrue(actualCoords.containsAll(expectedCoords))
    }

    @Test
    fun testGetTilesInDistance() {
        val origin = HexCoord.Zero
        val tilesInDistance2 = testGame.tileMap.getTilesInDistance(origin, 2).toList()
        // 1 (origin) + 6 (dist 1) + 12 (dist 2) = 19
        Assert.assertEquals(19, tilesInDistance2.size)
    }

    @Test
    fun testGetIfTileExistsOrNull() {
        val tileMap = testGame.tileMap
        Assert.assertNotNull(tileMap.getIfTileExistsOrNull(0, 0))
        Assert.assertNotNull(tileMap.getIfTileExistsOrNull(5, 5))
        Assert.assertNotNull(tileMap.getIfTileExistsOrNull(-5, -5))
        Assert.assertNull(tileMap.getIfTileExistsOrNull(6, 6))
    }

    @Test
    fun testWorldWrapGetIfTileExistsOrNull() {
        val width = 40
        val height = 20
        testGame.makeRectangularMap(height, width)
        testGame.tileMap.mapParameters.worldWrap = true
        testGame.tileMap.mapParameters.shape = MapShape.rectangular
        testGame.tileMap.mapParameters.mapSize.width = width
        
        // column -20 to 19
        val leftColumn = -20
        val rightColumn = 19
        
        val leftTileCoords = HexMath.getTileCoordsFromColumnRow(leftColumn, 0)
        val rightTileCoords = HexMath.getTileCoordsFromColumnRow(rightColumn, 0)
        
        // Beyond right edge
        val beyondRight = HexMath.getTileCoordsFromColumnRow(rightColumn + 1, 0)
        val wrappedBeyondRight = testGame.tileMap.getIfTileExistsOrNull(beyondRight.x, beyondRight.y)
        Assert.assertNotNull(wrappedBeyondRight)
        Assert.assertEquals(leftTileCoords, wrappedBeyondRight!!.position.toHexCoord())
    }

    @Test
    fun testNeighborTileClockPosition() {
        val tileMap = testGame.tileMap
        val origin = tileMap[0, 0]
        
        Assert.assertEquals(12, tileMap.getNeighborTileClockPosition(origin, tileMap[1, 1]))
        Assert.assertEquals(6, tileMap.getNeighborTileClockPosition(origin, tileMap[-1, -1]))
        Assert.assertEquals(2, tileMap.getNeighborTileClockPosition(origin, tileMap[0, 1]))
        Assert.assertEquals(8, tileMap.getNeighborTileClockPosition(origin, tileMap[0, -1]))
        Assert.assertEquals(4, tileMap.getNeighborTileClockPosition(origin, tileMap[-1, 0]))
        Assert.assertEquals(10, tileMap.getNeighborTileClockPosition(origin, tileMap[1, 0]))
        
        Assert.assertEquals(-1, tileMap.getNeighborTileClockPosition(origin, tileMap[2, 2]))
    }

    @Test
    fun testGetClockPositionNeighborTile() {
        val tileMap = testGame.tileMap
        val origin = tileMap[0, 0]
        
        Assert.assertEquals(tileMap[1, 1], tileMap.getClockPositionNeighborTile(origin, 12))
        Assert.assertEquals(tileMap[-1, -1], tileMap.getClockPositionNeighborTile(origin, 6))
    }

    @Test
    fun testGetTilesInRectangle() {
        testGame.makeRectangularMap(10, 10)
        // Rectangle(x, y, width, height)
        // These are world column/row numbers.
        // col 0, row 0 is HexCoord.of(0, 0) if even column.
        // Let's just check the count.
        val rect = Rectangle(0f, 0f, 2f, 2f)
        val tiles = testGame.tileMap.getTilesInRectangle(rect).toList()
        Assert.assertEquals(4, tiles.size)
    }
}
