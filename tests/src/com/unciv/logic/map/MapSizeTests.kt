package com.unciv.logic.map

import org.junit.Assert
import org.junit.Test

class MapSizeTests {

    @Test
    fun testPredefinedSizes() {
        val tiny = MapSize.Tiny
        Assert.assertEquals("Tiny", tiny.name)
        Assert.assertEquals(10, tiny.radius)
        Assert.assertEquals(23, tiny.width)
        Assert.assertEquals(15, tiny.height)

        val huge = MapSize.Huge
        Assert.assertEquals("Huge", huge.name)
        Assert.assertEquals(40, huge.radius)
        Assert.assertEquals(87, huge.width)
        Assert.assertEquals(57, huge.height)
    }

    @Test
    fun testCustomSizeFromRadius() {
        val custom = MapSize(25)
        Assert.assertEquals(MapSize.custom, custom.name)
        Assert.assertEquals(25, custom.radius)
        // HexMath.getEquivalentRectangularSize(25) -> nTiles = 1 + 6 * 25 * 26 / 2 = 1 + 6 * 325 = 1951
        // width = sqrt(1951 / 0.65) = sqrt(3001.5) approx 54.7 -> 55
        // height = 55 * 0.65 = 35.75 -> 36
        Assert.assertEquals(55, custom.width)
        Assert.assertEquals(36, custom.height)
    }

    @Test
    fun testGetPredefinedOrNextSmaller() {
        Assert.assertEquals(MapSize.Predefined.Tiny, MapSize.Tiny.getPredefinedOrNextSmaller())
        Assert.assertEquals(MapSize.Predefined.Huge, MapSize.Huge.getPredefinedOrNextSmaller())
        
        Assert.assertEquals(MapSize.Predefined.Small, MapSize(15).getPredefinedOrNextSmaller())
        Assert.assertEquals(MapSize.Predefined.Small, MapSize(16).getPredefinedOrNextSmaller())
        Assert.assertEquals(MapSize.Predefined.Medium, MapSize(20).getPredefinedOrNextSmaller())
        Assert.assertEquals(MapSize.Predefined.Huge, MapSize(100).getPredefinedOrNextSmaller())
        Assert.assertEquals(MapSize.Predefined.Tiny, MapSize(5).getPredefinedOrNextSmaller())
    }

    @Test
    fun testFixUndesiredSizes() {
        val tooSmall = MapSize(1)
        Assert.assertEquals("The provided map dimensions were too small", tooSmall.fixUndesiredSizes(false))
        Assert.assertEquals(2, tooSmall.radius)

        val tooBig = MapSize(1000)
        Assert.assertEquals("The provided map dimensions were too big", tooBig.fixUndesiredSizes(false))
        Assert.assertEquals(500, tooBig.radius)

        val worldWrapTooSmall = MapSize(10)
        Assert.assertEquals("World wrap requires a minimum width of 32 tiles", worldWrapTooSmall.fixUndesiredSizes(true))
        Assert.assertEquals(15, worldWrapTooSmall.radius)

        val worldWrapOddWidth = MapSize(40, 40)
        worldWrapOddWidth.width = 33
        Assert.assertNull(worldWrapOddWidth.fixUndesiredSizes(true))
        Assert.assertEquals(32, worldWrapOddWidth.width)
    }
}
