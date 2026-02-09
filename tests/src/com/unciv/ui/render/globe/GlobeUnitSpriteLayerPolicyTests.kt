package com.unciv.ui.render.globe

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobeUnitSpriteLayerPolicyTests {
    @Test
    fun `returns empty when base image is missing`() {
        val layers = GlobeUnitSpriteLayerPolicy.resolveLayerLocations(
            baseLocation = "TileSets/FantasyHex/Units/Warrior",
            imageExists = { false }
        )
        assertEquals(emptyList<String>(), layers)
    }

    @Test
    fun `returns contiguous sprite layers until first gap`() {
        val available = setOf(
            "TileSets/FantasyHex/Units/Warrior",
            "TileSets/FantasyHex/Units/Warrior-1",
            "TileSets/FantasyHex/Units/Warrior-2",
            "TileSets/FantasyHex/Units/Warrior-4"
        )
        val layers = GlobeUnitSpriteLayerPolicy.resolveLayerLocations(
            baseLocation = "TileSets/FantasyHex/Units/Warrior",
            imageExists = { it in available }
        )
        assertEquals(
            listOf(
                "TileSets/FantasyHex/Units/Warrior",
                "TileSets/FantasyHex/Units/Warrior-1",
                "TileSets/FantasyHex/Units/Warrior-2"
            ),
            layers
        )
    }
}
