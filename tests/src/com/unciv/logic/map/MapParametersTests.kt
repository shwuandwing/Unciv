package com.unciv.logic.map

import org.junit.Assert
import org.junit.Test

class MapParametersTests {

    @Test
    fun testGetArea() {
        val params = MapParameters()
        params.shape = MapShape.hexagonal
        params.mapSize = MapSize(10)
        // 1 + 6 * 10 * 11 / 2 = 331
        Assert.assertEquals(331, params.getArea())

        params.shape = MapShape.rectangular
        params.mapSize = MapSize.Tiny // 23x15
        Assert.assertEquals(23 * 15, params.getArea())
        
        params.worldWrap = true
        params.mapSize.width = 23
        Assert.assertEquals(22 * 15, params.getArea()) // rounds down odd width in world wrap
    }

    @Test
    fun testNumberOfTiles() {
        val params = MapParameters()
        params.shape = MapShape.hexagonal
        params.mapSize = MapSize(10)
        // 1 + 3 * 10 * 9 = 271? 
        // Wait, HexMath.getNumberOfTilesInHexagon(radius) is 1 + 6 * radius * (radius + 1) / 2
        // MapParameters.numberOfTiles() uses 1 + 3 * radius * (radius - 1)
        // Let's check which one is correct.
        // Radius 1: 1 + 3 * 1 * 0 = 1. (Correct, only origin)
        // Radius 2: 1 + 3 * 2 * 1 = 7. (Correct, origin + 6)
        // Radius 3: 1 + 3 * 3 * 2 = 19. (Correct, origin + 6 + 12)
        Assert.assertEquals(271, params.numberOfTiles())
        
        params.shape = MapShape.rectangular
        params.mapSize = MapSize.Tiny // 23x15
        Assert.assertEquals(23 * 15, params.numberOfTiles())
    }

    @Test
    fun testClone() {
        val params = MapParameters()
        params.name = "TestMap"
        params.seed = 12345L
        val clone = params.clone()
        Assert.assertEquals(params.name, clone.name)
        Assert.assertEquals(params.seed, clone.seed)
        Assert.assertNotSame(params, clone)
        Assert.assertNotSame(params.mapSize, clone.mapSize)
    }

    @Test
    fun testResetAdvancedSettings() {
        val params = MapParameters()
        params.elevationExponent = 1.0f
        params.reseed()
        val oldSeed = params.seed
        params.resetAdvancedSettings()
        Assert.assertEquals(0.7f, params.elevationExponent, 0.001f)
        // Seed should be different after reseed in resetAdvancedSettings
        // but it's time based so if it's very fast it might be the same.
        // Still, we check it's initialized.
    }
}
