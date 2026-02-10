package com.unciv.ui.render.globe

import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobeYieldOverlayPolicyTests {
    @Test
    fun `resolve keeps only positive integer yields`() {
        val stats = Stats(
            production = 2.9f,
            food = 1.2f,
            gold = 0.8f,
            science = -1f
        )

        val icons = GlobeYieldOverlayPolicy.resolve(stats, maxIcons = 7)
        assertEquals(
            listOf(Stat.Production, Stat.Food),
            icons.map { it.stat }
        )
        assertEquals(listOf(2, 1), icons.map { it.value })
    }

    @Test
    fun `resolve limits icon count and sorts by value then stat order`() {
        val stats = Stats(
            production = 2f,
            food = 2f,
            gold = 3f,
            science = 1f
        )

        val icons = GlobeYieldOverlayPolicy.resolve(stats, maxIcons = 3)
        assertEquals(
            listOf(Stat.Gold, Stat.Production, Stat.Food),
            icons.map { it.stat }
        )
    }

    @Test
    fun `resolve emits stat icon locations`() {
        val stats = Stats(food = 3f)
        val icons = GlobeYieldOverlayPolicy.resolve(stats, maxIcons = 1)
        assertEquals("StatIcons/Food", icons.single().iconLocation)
    }

    @Test
    fun `markerOffsets returns 2-icon vertical pair for value 2`() {
        val offsets = GlobeYieldOverlayPolicy.markerOffsets(2)
        assertEquals(2, offsets.size)
        assertEquals(0f, offsets[0].x, 0.0001f)
        assertEquals(0.5f, offsets[0].y, 0.0001f)
        assertEquals(0f, offsets[1].x, 0.0001f)
        assertEquals(-0.5f, offsets[1].y, 0.0001f)
    }

    @Test
    fun `markerOffsets clamps values above 4 to 2x2 pattern`() {
        val offsets = GlobeYieldOverlayPolicy.markerOffsets(9)
        assertEquals(4, offsets.size)
        assertEquals(-0.5f, offsets[0].x, 0.0001f)
        assertEquals(0.5f, offsets[0].y, 0.0001f)
        assertEquals(0.5f, offsets[1].x, 0.0001f)
        assertEquals(0.5f, offsets[1].y, 0.0001f)
    }
}
