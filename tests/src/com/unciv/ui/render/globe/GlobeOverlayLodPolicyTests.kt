package com.unciv.ui.render.globe

import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeOverlayLodPolicyTests {

    @Test
    fun largeFrontFacingTileKeepsHighDetailAlpha() {
        val alpha = GlobeOverlayLodPolicy.overlayAlpha(
            frameWidth = 24f,
            frameHeight = 22f,
            facingDotCamera = 0.85f
        )
        assertTrue("large front-facing tile should keep overlay detail", alpha > 0.95f)
    }

    @Test
    fun tinyNearLimbTileFadesOverlayDetail() {
        val alpha = GlobeOverlayLodPolicy.overlayAlpha(
            frameWidth = 4.5f,
            frameHeight = 4f,
            facingDotCamera = 0.08f
        )
        assertTrue("tiny horizon tile should fade out detail overlays", alpha < 0.05f)
    }

    @Test
    fun alphaIncreasesWithTileSpanAndFacing() {
        val low = GlobeOverlayLodPolicy.overlayAlpha(
            frameWidth = 6f,
            frameHeight = 5.8f,
            facingDotCamera = 0.12f
        )
        val high = GlobeOverlayLodPolicy.overlayAlpha(
            frameWidth = 12f,
            frameHeight = 11f,
            facingDotCamera = 0.26f
        )
        assertTrue("detail alpha should increase as tiles get larger and face camera", high > low)
    }

    @Test
    fun baseTerrainFadesEarlierNearLimbThanDetailOverlays() {
        val detail = GlobeOverlayLodPolicy.overlayAlpha(
            frameWidth = 10f,
            frameHeight = 9f,
            facingDotCamera = 0.2f
        )
        val base = GlobeOverlayLodPolicy.baseTerrainAlpha(
            frameWidth = 10f,
            frameHeight = 9f,
            facingDotCamera = 0.2f
        )
        assertTrue("base terrain should fade earlier to suppress horizon seams", base < detail)
    }

    @Test
    fun gridLineAlphaScaleDropsNearHorizon() {
        val limb = GlobeOverlayLodPolicy.gridLineAlphaScale(0.09f)
        val center = GlobeOverlayLodPolicy.gridLineAlphaScale(0.6f)
        assertTrue("grid line alpha should be lower near limb", limb < center)
    }
}
