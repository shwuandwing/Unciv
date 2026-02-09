package com.unciv.ui.render.globe

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeRenderStateColorPolicyTests {

    @Test
    fun tundraUsesLightPaletteWhenTilesetUsesImagesAsBase() {
        val color = GlobeRenderStateAdapter.baseTerrainColor(
            baseTerrain = Constants.tundra,
            terrainColor = Color(124 / 255f, 62 / 255f, 57 / 255f, 1f),
            useColorAsBaseTerrain = false
        )

        assertTrue("tundra should be lighter than brown tint", color.g > color.r)
        assertTrue("tundra should be bright enough for snowy palette", color.r > 0.75f)
    }

    @Test
    fun snowUsesVeryLightPaletteWhenTilesetUsesImagesAsBase() {
        val color = GlobeRenderStateAdapter.baseTerrainColor(
            baseTerrain = Constants.snow,
            terrainColor = Color(1f, 1f, 1f, 1f),
            useColorAsBaseTerrain = false
        )

        assertTrue("snow should render near-white", color.r > 0.9f && color.g > 0.9f && color.b > 0.9f)
    }
}
