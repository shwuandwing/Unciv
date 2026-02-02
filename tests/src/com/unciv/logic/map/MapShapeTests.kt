package com.unciv.logic.map

import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MapShapeTests {
    private lateinit var ruleset: Ruleset

    @Before
    fun setUp() {
        ruleset = Ruleset()
        ruleset.terrains["Plains"] = Terrain().apply {
            name = "Plains"
            type = TerrainType.Land
        }
    }

    @Test
    fun testNumberOfTiles() {
        val params = MapParameters()
        params.mapSize = MapSize.Tiny // radius 10, width 23, height 15

        params.shape = MapShape.hexagonal
        // 1 + 3 * R * (R-1) = 1 + 3 * 10 * 9 = 1 + 270 = 271
        Assert.assertEquals(271, params.numberOfTiles())

        params.shape = MapShape.flatEarth
        Assert.assertEquals(271, params.numberOfTiles())

        params.shape = MapShape.rectangular
        // W * H = 23 * 15 = 345
        Assert.assertEquals(345, params.numberOfTiles())
    }

    @Test
    fun testGetArea() {
        val params = MapParameters()
        params.mapSize = MapSize.Tiny // radius 10, width 23, height 15

        params.shape = MapShape.hexagonal
        // getNumberOfTilesInHexagon(radius) = 1 + 6 * R * (R+1) / 2 = 1 + 3 * 10 * 11 = 331
        // Wait, MapParameters.kt:
        // fun getArea() = when {
        //    shape == MapShape.hexagonal || shape == MapShape.flatEarth -> getNumberOfTilesInHexagon(mapSize.radius)
        // HexMath.kt:
        // fun getNumberOfTilesInHexagon(size: Int): Int {
        //    if (size < 0) return 0
        //    return 1 + 6 * size * (size + 1) / 2
        // }
        Assert.assertEquals(331, params.getArea())

        params.shape = MapShape.flatEarth
        Assert.assertEquals(331, params.getArea())

        params.shape = MapShape.rectangular
        Assert.assertEquals(345, params.getArea())

        // Test worldWrap with odd width
        params.worldWrap = true
        params.mapSize.width = 23
        // worldWrap && mapSize.width % 2 != 0 -> (mapSize.width - 1) * mapSize.height = 22 * 15 = 330
        Assert.assertEquals(330, params.getArea())
        
        params.mapSize.width = 24
        // else -> 24 * 15 = 360
        Assert.assertEquals(360, params.getArea())
    }

    @Test
    fun testDistanceFromEdgeRectangular() {
        val params = MapParameters()
        params.shape = MapShape.rectangular
        params.mapSize.width = 10
        params.mapSize.height = 10
        params.worldWrap = false

        // Center is roughly at column 0, row 0.
        // getTileCoordsFromColumnRow(column, row)
        // column 0, row 0 -> (0,0)
        val center = HexMath.getTileCoordsFromColumnRow(0, 0)
        
        // getDistanceFromEdge uses:
        // left = width / 2 - (x - y)  = 5 - (0 - 0) = 5
        // right = (width - 1) / 2 - (y - x) = 4 - (0 - 0) = 4
        // top = height / 2 - (x + y) / 2 = 5 - 0 = 5
        // bottom = (x + y - 1) / 2 + (height - 1) / 2 = -1 / 2 + 4 = 4 (int division rounds to 0 for -0.5)
        // min(5, 4, 5, 4) = 4
        Assert.assertEquals(4, HexMath.getDistanceFromEdge(center, params))

        // Left edge: column -5, row 0
        val leftEdge = HexMath.getTileCoordsFromColumnRow(-5, 0)
        // column = y-x = -5, row = (y+x)/2 = 0
        // y-x = -5, y+x = 0 => 2y = -5, y = -2.5, x = 2.5
        // getTileCoordsFromColumnRow(-5, 0) -> twoRows = 0, x = (0 - (-5))/2 = 2, y = (0 + (-5))/2 = -2
        // wait, getTileCoordsFromColumnRow(-5, 0):
        // column = -5 (odd)
        // twoRows = 0 * 2 + 1 = 1
        // x = (1 - (-5)) / 2 = 3
        // y = (1 + (-5)) / 2 = -2
        // x-y = 5. left = 5 - (3 - (-2)) = 5 - 5 = 0.
        Assert.assertEquals(0, HexMath.getDistanceFromEdge(leftEdge, params))
    }

    @Test
    fun testDistanceFromEdgeHexagonal() {
        val params = MapParameters()
        params.shape = MapShape.hexagonal
        params.mapSize.radius = 10
        params.worldWrap = false

        Assert.assertEquals(10, HexMath.getDistanceFromEdge(HexCoord.Zero, params))
        
        // Edge tile at distance 10
        val edgeTile = HexCoord.of(10, 0)
        Assert.assertEquals(0, HexMath.getDistanceFromEdge(edgeTile, params))
    }

    @Test
    fun testTileMapConstructionRectangular() {
        val width = 10
        val height = 10
        val tileMap = TileMap(width, height, ruleset, false)
        tileMap.mapParameters.shape = MapShape.rectangular
        Assert.assertEquals(MapShape.rectangular, tileMap.mapParameters.shape)
        Assert.assertEquals(100, tileMap.tileList.size)
        
        // Check bounds
        val minX = tileMap.tileList.minOf { it.position.x }
        val maxX = tileMap.tileList.maxOf { it.position.x }
        val minY = tileMap.tileList.minOf { it.position.y }
        val maxY = tileMap.tileList.maxOf { it.position.y }
        
        Assert.assertTrue(tileMap.contains(HexMath.getTileCoordsFromColumnRow(-5, -5)))
        Assert.assertTrue(tileMap.contains(HexMath.getTileCoordsFromColumnRow(4, 4)))
        Assert.assertFalse(tileMap.contains(HexMath.getTileCoordsFromColumnRow(-6, 0)))
        Assert.assertFalse(tileMap.contains(HexMath.getTileCoordsFromColumnRow(5, 0)))
    }

    @Test
    fun testTileMapConstructionHexagonal() {
        val radius = 5
        val tileMap = TileMap(radius, ruleset, false)
        tileMap.mapParameters.shape = MapShape.hexagonal
        Assert.assertEquals(MapShape.hexagonal, tileMap.mapParameters.shape)
        // 1 + 6 * 5 * 6 / 2 = 1 + 90 = 91
        Assert.assertEquals(91, tileMap.tileList.size)
        
        Assert.assertTrue(tileMap.contains(HexCoord.Zero))
        Assert.assertTrue(tileMap.contains(HexCoord.of(5, 0)))
        Assert.assertTrue(tileMap.contains(HexCoord.of(-5, -5)))
        Assert.assertFalse(tileMap.contains(HexCoord.of(6, 0)))
    }

    @Test
    fun testWorldWrapRectangular() {
        val width = 40 // minimum is 32 for fixUndesiredSizes but TileMap doesn't enforce it in constructor
        val height = 20
        val tileMap = TileMap(width, height, ruleset, true)
        tileMap.mapParameters.worldWrap = true
        tileMap.mapParameters.shape = MapShape.rectangular
        tileMap.mapParameters.mapSize.width = width
        
        // column -20 to 19
        val leftColumn = -20
        val rightColumn = 19
        
        val leftTileCoords = HexMath.getTileCoordsFromColumnRow(leftColumn, 0)
        val rightTileCoords = HexMath.getTileCoordsFromColumnRow(rightColumn, 0)
        
        Assert.assertTrue(tileMap.contains(leftTileCoords))
        Assert.assertTrue(tileMap.contains(rightTileCoords))
        
        // Wrap around
        // getIfTileExistsOrNull(x, y)
        // radius = width / 2 = 20
        // x + radius, y - radius
        
        val beyondRight = HexMath.getTileCoordsFromColumnRow(rightColumn + 1, 0)
        Assert.assertFalse(tileMap.contains(beyondRight.x, beyondRight.y))
        val wrappedBeyondRight = tileMap.getIfTileExistsOrNull(beyondRight.x, beyondRight.y)
        Assert.assertNotNull("Should wrap around from right to left", wrappedBeyondRight)
        Assert.assertEquals(leftTileCoords, wrappedBeyondRight!!.position)
        
        val beyondLeft = HexMath.getTileCoordsFromColumnRow(leftColumn - 1, 0)
        Assert.assertFalse(tileMap.contains(beyondLeft.x, beyondLeft.y))
        val wrappedBeyondLeft = tileMap.getIfTileExistsOrNull(beyondLeft.x, beyondLeft.y)
        Assert.assertNotNull("Should wrap around from left to right", wrappedBeyondLeft)
        Assert.assertEquals(rightTileCoords, wrappedBeyondLeft!!.position)
    }

    @Test
    fun testWorldWrapHexagonal() {
        val radius = 10
        val tileMap = TileMap(radius, ruleset, true)
        tileMap.mapParameters.worldWrap = true
        tileMap.mapParameters.shape = MapShape.hexagonal
        tileMap.mapParameters.mapSize.radius = radius
        
        // Edge at (10, 0)
        val edgeTile = HexCoord.of(10, 0)
        Assert.assertTrue(tileMap.contains(edgeTile))
        
        // Beyond edge at (11, 0)
        // getIfTileExistsOrNull(11, 0)
        // radius = 10
        // A. x+10, y-10 -> (21, -10)
        // B. x-10, y+10 -> (1, 10)
        // (1, 10) distance to (0,0) is max(1, 10) = 10 (since x,y same sign)
        // So (1, 10) is on the other side of the map.
        
        val beyondEdge = HexCoord.of(11, 0)
        Assert.assertFalse(tileMap.contains(beyondEdge))
        val wrapped = tileMap.getIfTileExistsOrNull(beyondEdge.x, beyondEdge.y)
        Assert.assertNotNull(wrapped)
        Assert.assertEquals(HexCoord.of(1, 10), wrapped!!.position)
    }

    @Test
    fun testNeighborClockPositionWithWrap() {
        val width = 40
        val height = 20
        val tileMap = TileMap(width, height, ruleset, true)
        tileMap.mapParameters.worldWrap = true
        tileMap.mapParameters.shape = MapShape.rectangular
        tileMap.mapParameters.mapSize.width = width
        
        val leftEdgeTile = tileMap[HexMath.getTileCoordsFromColumnRow(-20, 0)]
        val rightEdgeTile = tileMap[HexMath.getTileCoordsFromColumnRow(19, 0)]
        
        // From right edge, going "right" should lead to left edge.
        // In hex coords, "right" is top-right (2) or bottom-right (4)
        // Rectangular map column = y-x.
        // y-x = 19.
        // Neighbors:
        // (x, y+1) -> y+1-x = 20 (wraps to -20)  - Clock 2 (top-right)
        // (x-1, y) -> y-(x-1) = y-x+1 = 20 (wraps to -20) - Clock 4 (bottom-right)
        
        val clockPos = tileMap.getNeighborTileClockPosition(rightEdgeTile, leftEdgeTile)
        // Expected: 2 or 4.
        // Let's see getNeighborTileClockPosition logic:
        // x1=rightEdgeTile.x, y1=rightEdgeTile.y
        // x2=leftEdgeTile.x, y2=leftEdgeTile.y
        // radius = 20
        // xWrapDifferenceBottom = x1 - (x2 - 20) = x1 - x2 + 20
        // yWrapDifferenceBottom = y1 - (y2 - 20) = y1 - y2 + 20
        // ...
        Assert.assertTrue(clockPos == 2 || clockPos == 4)
    }

    @Test
    fun testGetTilesAtDistance() {
        val radius = 5
        val tileMap = TileMap(radius, ruleset, false)
        
        val origin = HexCoord.Zero
        
        // Distance 0: only origin
        val distance0 = tileMap.getTilesAtDistance(origin, 0).toList()
        Assert.assertEquals(1, distance0.size)
        Assert.assertEquals(origin, distance0[0].position)
        
        // Distance 1: 6 neighbors
        val distance1 = tileMap.getTilesAtDistance(origin, 1).toList()
        Assert.assertEquals(6, distance1.size)
        
        // Distance 5: on the edge
        val distance5 = tileMap.getTilesAtDistance(origin, 5).toList()
        // A ring at distance R has 6*R tiles
        Assert.assertEquals(30, distance5.size)
        
        // Distance 6: outside the map
        val distance6 = tileMap.getTilesAtDistance(origin, 6).toList()
        Assert.assertEquals(0, distance6.size)
    }

    @Test
    fun testGetTilesInDistance() {
        val radius = 5
        val tileMap = TileMap(radius, ruleset, false)
        
        val origin = HexCoord.Zero
        
        // Distance 0: only origin
        val inDistance0 = tileMap.getTilesInDistance(origin, 0).toList()
        Assert.assertEquals(1, inDistance0.size)
        
        // Distance 1: origin + 6 neighbors = 7
        val inDistance1 = tileMap.getTilesInDistance(origin, 1).toList()
        Assert.assertEquals(7, inDistance1.size)
        
        // Distance 5: entire map
        val inDistance5 = tileMap.getTilesInDistance(origin, 5).toList()
        Assert.assertEquals(91, inDistance5.size)
        
        // Distance 10: still entire map (filtered by map boundaries)
        val inDistance10 = tileMap.getTilesInDistance(origin, 10).toList()
        Assert.assertEquals(91, inDistance10.size)
    }

    @Test
    fun testGetUnwrappedPosition() {
        val radius = 10
        val tileMap = TileMap(radius, ruleset, true)
        tileMap.mapParameters.worldWrap = true
        tileMap.mapParameters.shape = MapShape.hexagonal
        tileMap.mapParameters.mapSize.radius = radius

        // Center tile (0,0)
        val center = HexCoord.Zero
        val unwrappedCenter = tileMap.getUnwrappedPosition(center)
        // vectorUnwrappedLeft = (10, -10), squareSum = 200
        // vectorUnwrappedRight = (-10, 10), squareSum = 200
        // It returns vectorUnwrappedLeft (10, -10) because of the else.
        Assert.assertEquals(HexCoord.of(10, -10), unwrappedCenter)
        
        // Tile at (5, 0)
        val tile50 = HexCoord.of(5, 0)
        val unwrapped50 = tileMap.getUnwrappedPosition(tile50)
        // vectorUnwrappedLeft = (15, -10), squareSum = 225 + 100 = 325
        // vectorUnwrappedRight = (-5, 10), squareSum = 25 + 100 = 125
        // Should return (-5, 10)
        Assert.assertEquals(HexCoord.of(-5, 10), unwrapped50)
    }

    @Test
    fun testGetViewableTiles() {
        val radius = 5
        val tileMap = TileMap(radius, ruleset, false)
        
        // All tiles are Plains (0 height)
        val origin = HexCoord.Zero
        val viewableTiles = tileMap.getViewableTiles(origin, 2)
        // Plains have 0 height by default.
        // Sight range 2 should see origin + dist 1 + dist 2.
        // 1 + 6 + 12 = 19 tiles.
        Assert.assertEquals(19, viewableTiles.size)
        
        // Define "Forest" that blocks sight
        ruleset.terrains["Forest"] = Terrain().apply {
            name = "Forest"
            type = TerrainType.TerrainFeature
            uniques = arrayListOf("Has an elevation of [1] for visibility calculations", "Blocks line-of-sight from tiles at same elevation")
        }
        tileMap.tileUniqueMapCache.clear()
        
        for (tile in tileMap.getTilesAtDistance(origin, 1)) {
            tile.addTerrainFeature("Forest")
            tile.setTerrainTransients()
        }
        
        // Forest at distance 1 should block sight to all distance 2 if we are at height 0.
        val viewableTilesWithForest = tileMap.getViewableTiles(origin, 2)
        Assert.assertEquals("Should only see origin and distance 1", 7, viewableTilesWithForest.size)
            
        // Now put the unit on a Hill
        ruleset.terrains["Hill"] = Terrain().apply {
            name = "Hill"
            type = TerrainType.Land
            uniques = arrayListOf("Has an elevation of [1] for visibility calculations")
        }
        tileMap.tileUniqueMapCache.clear()
        val originTile = tileMap[origin]
        originTile.baseTerrain = "Hill"
        originTile.setTerrainTransients()
        
        // Unit at (0,0) now has unitHeight 1.
        // Forest at distance 1 still has tileHeight 2.
        // Still unitHeight (1) < tileHeight (2), so distance 2 should still be blocked.
        val viewableTilesFromHill = tileMap.getViewableTiles(origin, 2)
        Assert.assertEquals("Should still only see origin and distance 1 from hill", 7, viewableTilesFromHill.size)

        // Give unit +2 sight elevation by using a different terrain type
        ruleset.terrains["HighHill"] = Terrain().apply {
            name = "HighHill"
            type = TerrainType.Land
            uniques = arrayListOf("Has an elevation of [2] for visibility calculations")
        }
        tileMap.tileUniqueMapCache.clear()
        originTile.baseTerrain = "HighHill"
        originTile.setTerrainTransients()
        
        // Unit unitHeight is now 2.
        // Forest tileHeight is 2.
        // unitHeight (2) >= tileHeight (2), so distance 2 should be visible!
        val viewableTilesFromHighHill = tileMap.getViewableTiles(origin, 2)
        Assert.assertEquals("Distance 2 should be visible from high hill", 19, viewableTilesFromHighHill.size)
    }

    @Test
    fun testMapSizeFixUndesiredSizes() {
        // Custom size too small
        val size = MapSize(1, 1) // constructor sets radius to equivalent of 1x1
        size.fixUndesiredSizes(false)
        Assert.assertTrue("Radius should be at least 2", size.radius >= 2)
        Assert.assertTrue("Width should be at least 3", size.width >= 3)
        Assert.assertTrue("Height should be at least 3", size.height >= 3)
        
        // World wrap requires width at least 32
        val smallWrap = MapSize(10, 10)
        smallWrap.fixUndesiredSizes(true)
        Assert.assertTrue("World wrap width should be at least 32", smallWrap.width >= 32)
        
        // World wrap width must be even
        val oddWrap = MapSize(33, 10)
        oddWrap.fixUndesiredSizes(true)
        Assert.assertEquals("World wrap width should be even", 32, oddWrap.width)
    }
}
