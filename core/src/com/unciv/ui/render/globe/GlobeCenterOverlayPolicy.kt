package com.unciv.ui.render.globe

object GlobeCenterOverlayPolicy {
    data class Overlay(
        val location: String,
        val scale: Float = 1f,
        val alpha: Float = 1f,
        val isBaseTerrain: Boolean = false,
        val isDirectional: Boolean = false,
        val textureInsetTexelsOverride: Float? = null
    )

    fun classify(
        fullLayers: List<String>,
        hexagonLocation: String,
        baseTerrainTiles: Set<String>
    ): List<Overlay> {
        return fullLayers.mapNotNull { location ->
            when {
                location == hexagonLocation -> null
                location in baseTerrainTiles -> Overlay(
                    location = location,
                    scale = 1.035f,
                    isBaseTerrain = true
                )
                else -> Overlay(location = location)
            }
        }
    }
}
