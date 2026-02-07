package com.unciv.ui.render.globe

import com.unciv.Constants

object GlobeTerrainDetailResolver {
    val defaultDetailTerrains: Set<String> = setOf(
        Constants.mountain,
        Constants.hill,
        Constants.forest,
        Constants.jungle,
        Constants.ice
    )

    fun resolveLocations(
        baseTerrain: String,
        terrainFeatures: List<String>,
        naturalWonder: String?,
        getTile: (String) -> String,
        orFallback: (String) -> String,
        imageExists: (String) -> Boolean,
        detailTerrains: Set<String> = defaultDetailTerrains
    ): List<String> {
        if (naturalWonder != null) {
            val base = orFallback(baseTerrain)
            val wonder = orFallback(naturalWonder)
            return listOf(base, wonder)
        }

        val details = ArrayList<String>(3)

        if (baseTerrain in detailTerrains) {
            details += orFallback(baseTerrain)
        }
        for (feature in terrainFeatures) {
            if (feature !in detailTerrains) continue
            val combined = getTile("$baseTerrain+$feature")
            val location = if (imageExists(combined)) {
                combined
            } else {
                orFallback(feature)
            }
            details += location
        }

        return details
    }
}

