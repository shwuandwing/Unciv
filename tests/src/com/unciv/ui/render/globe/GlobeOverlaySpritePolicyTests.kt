package com.unciv.ui.render.globe

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobeOverlaySpritePolicyTests {

    @Test
    fun appliesIcosaTextureRotationOffset() {
        assertEquals(30f, GlobeOverlaySpritePolicy.overlayRotationDegrees(0f), 0.0001f)
        assertEquals(75f, GlobeOverlaySpritePolicy.overlayRotationDegrees(45f), 0.0001f)
    }

    @Test
    fun directionalOverlaysDoNotInsetTextureUv() {
        val inset = GlobeOverlaySpritePolicy.textureInsetTexels(
            GlobeCenterOverlayPolicy.Overlay(location = "TileSets/HexaRealm/Tiles/River-Bottom", isDirectional = true)
        )
        assertEquals(0f, inset, 0.0001f)
    }

    @Test
    fun regularOverlaysUseDefaultInsetTextureUv() {
        val inset = GlobeOverlaySpritePolicy.textureInsetTexels(
            GlobeCenterOverlayPolicy.Overlay(location = "TileSets/HexaRealm/Tiles/Grassland")
        )
        assertEquals(1.25f, inset, 0.0001f)
    }

    @Test
    fun verticalUvWindowReversesUnflippedRegionsForYUpMapping() {
        val window = GlobeOverlaySpritePolicy.verticalUvWindow(
            rawStart = 0.2f,
            rawEnd = 0.8f,
            inset = 0f
        )
        assertEquals(0.8f, window.start, 0.0001f)
        assertEquals(0.2f, window.end, 0.0001f)
    }

    @Test
    fun verticalUvWindowKeepsFlippedRegionsConsistent() {
        val window = GlobeOverlaySpritePolicy.verticalUvWindow(
            rawStart = 0.8f,
            rawEnd = 0.2f,
            inset = 0f
        )
        assertEquals(0.2f, window.start, 0.0001f)
        assertEquals(0.8f, window.end, 0.0001f)
    }
}
