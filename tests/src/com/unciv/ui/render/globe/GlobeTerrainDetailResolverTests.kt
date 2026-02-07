package com.unciv.ui.render.globe

import com.unciv.Constants
import org.junit.Assert
import org.junit.Test

class GlobeTerrainDetailResolverTests {

    @Test
    fun prefersCombinedFeatureImageWhenAvailable() {
        val resolved = GlobeTerrainDetailResolver.resolveLocations(
            baseTerrain = Constants.plains,
            terrainFeatures = listOf(Constants.forest),
            naturalWonder = null,
            getTile = { key -> "tile/$key" },
            orFallback = { key -> "fallback/$key" },
            imageExists = { path -> path == "tile/${Constants.plains}+${Constants.forest}" }
        )

        Assert.assertEquals(listOf("tile/${Constants.plains}+${Constants.forest}"), resolved)
    }

    @Test
    fun fallsBackToFeatureImageWhenCombinedMissing() {
        val resolved = GlobeTerrainDetailResolver.resolveLocations(
            baseTerrain = Constants.plains,
            terrainFeatures = listOf(Constants.forest),
            naturalWonder = null,
            getTile = { key -> "tile/$key" },
            orFallback = { key -> "fallback/$key" },
            imageExists = { false }
        )

        Assert.assertEquals(listOf("fallback/${Constants.forest}"), resolved)
    }

    @Test
    fun includesBaseDetailTerrainAndFeatureLayer() {
        val resolved = GlobeTerrainDetailResolver.resolveLocations(
            baseTerrain = Constants.mountain,
            terrainFeatures = listOf(Constants.forest),
            naturalWonder = null,
            getTile = { key -> "tile/$key" },
            orFallback = { key -> "fallback/$key" },
            imageExists = { path -> path == "tile/${Constants.mountain}+${Constants.forest}" }
        )

        Assert.assertEquals(
            listOf("fallback/${Constants.mountain}", "tile/${Constants.mountain}+${Constants.forest}"),
            resolved
        )
    }

    @Test
    fun naturalWonderAlwaysReturnsBaseAndWonderLayers() {
        val resolved = GlobeTerrainDetailResolver.resolveLocations(
            baseTerrain = Constants.desert,
            terrainFeatures = listOf(Constants.forest),
            naturalWonder = "Barringer Crater",
            getTile = { key -> "tile/$key" },
            orFallback = { key -> "fallback/$key" },
            imageExists = { false }
        )

        Assert.assertEquals(listOf("fallback/${Constants.desert}", "fallback/Barringer Crater"), resolved)
    }
}
