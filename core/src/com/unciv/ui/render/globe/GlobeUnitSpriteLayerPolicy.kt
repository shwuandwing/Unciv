package com.unciv.ui.render.globe

object GlobeUnitSpriteLayerPolicy {
    /**
     * Resolve multilayer unit sprite atlas paths:
     * - base path, then
     * - base-1, base-2, ... until the first gap.
     */
    fun resolveLayerLocations(
        baseLocation: String,
        imageExists: (String) -> Boolean
    ): List<String> {
        if (baseLocation.isBlank() || !imageExists(baseLocation)) return emptyList()
        val layers = ArrayList<String>()
        layers += baseLocation

        var index = 1
        while (true) {
            val layerLocation = "$baseLocation-$index"
            if (!imageExists(layerLocation)) break
            layers += layerLocation
            index++
        }
        return layers
    }
}
