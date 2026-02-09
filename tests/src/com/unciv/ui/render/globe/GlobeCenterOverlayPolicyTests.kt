package com.unciv.ui.render.globe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeCenterOverlayPolicyTests {

    @Test
    fun removesHexagonBaseLayer() {
        val overlays = GlobeCenterOverlayPolicy.classify(
            fullLayers = listOf("hex", "tile/Tundra", "tile/Salt"),
            hexagonLocation = "hex",
            baseTerrainTiles = setOf("tile/Tundra")
        )

        assertEquals(2, overlays.size)
        assertTrue(overlays.none { it.location == "hex" })
    }

    @Test
    fun baseTerrainLayerUsesUnmodifiedStamp() {
        val overlays = GlobeCenterOverlayPolicy.classify(
            fullLayers = listOf("tile/Tundra"),
            hexagonLocation = "hex",
            baseTerrainTiles = setOf("tile/Tundra")
        )

        assertEquals(1, overlays.size)
        val overlay = overlays.single()
        assertEquals("tile/Tundra", overlay.location)
        assertEquals(1.035f, overlay.scale)
        assertEquals(1f, overlay.alpha)
        assertTrue(overlay.isBaseTerrain)
    }

    @Test
    fun resourceOrImprovementLayersRemainUnmodified() {
        val overlays = GlobeCenterOverlayPolicy.classify(
            fullLayers = listOf("tile/Salt", "tile/Ancient ruins-Snow"),
            hexagonLocation = "hex",
            baseTerrainTiles = setOf("tile/Tundra")
        )

        assertEquals(2, overlays.size)
        overlays.forEach {
            assertEquals(1f, it.scale)
            assertEquals(1f, it.alpha)
            assertTrue(!it.isBaseTerrain)
        }
    }
}
