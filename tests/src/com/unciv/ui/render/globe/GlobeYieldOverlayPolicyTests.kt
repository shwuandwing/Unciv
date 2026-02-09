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
}
