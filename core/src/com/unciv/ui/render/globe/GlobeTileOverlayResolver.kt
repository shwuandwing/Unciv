package com.unciv.ui.render.globe

import com.unciv.logic.map.NeighborDirection
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.ui.components.tilegroups.layers.EdgeTileImage

object GlobeTileOverlayResolver {

    data class NeighborEdgeContext(
        val direction: NeighborDirection?,
        val terrainNames: Set<String>,
        val baseTerrainTypeName: String
    )

    fun resolveEdgeLocations(
        originTerrainNames: Set<String>,
        originBaseTerrainTypeName: String,
        neighbors: Sequence<NeighborEdgeContext>,
        edgeImagesByPosition: Map<NeighborDirection, List<EdgeTileImage>>
    ): Sequence<String> {
        return neighbors.flatMap { neighbor ->
            val direction = neighbor.direction ?: return@flatMap emptySequence<String>()
            val possibleEdgeImages = edgeImagesByPosition[direction] ?: return@flatMap emptySequence<String>()
            possibleEdgeImages.asSequence().filter {
                matchesFilterMinimal(originTerrainNames, originBaseTerrainTypeName, it.originTileFilter) &&
                    matchesFilterMinimal(neighbor.terrainNames, neighbor.baseTerrainTypeName, it.destinationTileFilter)
            }.map { it.fileName }
        }
    }

    fun resolveTerrainLayerLocations(
        baseTerrain: String,
        terrainFeatures: List<String>,
        naturalWonder: String?,
        shownImprovement: String?,
        improvementIsPillaged: Boolean,
        resource: String?,
        showPixelImprovements: Boolean,
        canSeeResource: Boolean,
        useColorAsBaseTerrain: Boolean,
        useSummaryImages: Boolean,
        hexagonLocation: String,
        naturalWonderSummaryLocation: String,
        edgeLocations: Sequence<String>,
        getTile: (String) -> String,
        orFallback: (String) -> String,
        imageExists: (String) -> Boolean,
        ruleVariants: Map<String, Array<String>>
    ): List<String> {
        val baseHexagon = if (useColorAsBaseTerrain) mutableListOf(hexagonLocation) else mutableListOf()
        val edge = edgeLocations.toList()

        val shouldShowImprovement = showPixelImprovements && shownImprovement != null
        val shouldShowResource = showPixelImprovements && resource != null && canSeeResource
        val improvementName = if (shouldShowImprovement) {
            if (improvementIsPillaged && imageExists(getTile("$shownImprovement-Pillaged"))) "$shownImprovement-Pillaged"
            else shownImprovement!!
        } else null

        val resourceAndImprovement = sequence {
            if (shouldShowResource) yield(resource!!)
            if (improvementName != null) yield(improvementName)
        }.toList()

        val terrainImages = if (naturalWonder != null) {
            listOf(baseTerrain, naturalWonder)
        } else {
            listOf(baseTerrain) + terrainFeatures
        }

        val allTogether = (terrainImages + resourceAndImprovement).joinToString("+")
        val allTogetherLocation = getTile(allTogether)
        val explicitVariant = ruleVariants[allTogether]

        return when {
            explicitVariant != null -> baseHexagon.apply {
                addAll(explicitVariant.map { getTile(it) })
                addAll(edge)
            }
            imageExists(allTogetherLocation) -> baseHexagon.apply {
                add(allTogetherLocation)
                addAll(edge)
            }
            naturalWonder != null -> {
                val wonder = if (useSummaryImages) {
                    naturalWonderSummaryLocation
                } else {
                    orFallback(naturalWonder)
                }
                baseHexagon.apply {
                    add(wonder)
                    addAll(edge)
                }
            }
            else -> baseHexagon.apply {
                addAll(resolveTerrainImageLocations(terrainImages, getTile, orFallback, imageExists, ruleVariants))
                addAll(edge)
                addAll(resolveImprovementAndResourceImages(resourceAndImprovement, getTile, orFallback, imageExists))
            }
        }
    }

    fun resolveRiverLocations(
        hasBottomRightRiver: Boolean,
        hasBottomRiver: Boolean,
        hasBottomLeftRiver: Boolean,
        bottomRightRiverLocation: String,
        bottomRiverLocation: String,
        bottomLeftRiverLocation: String
    ): List<String> {
        val rivers = ArrayList<String>(3)
        if (hasBottomRightRiver) rivers += bottomRightRiverLocation
        if (hasBottomRiver) rivers += bottomRiverLocation
        if (hasBottomLeftRiver) rivers += bottomLeftRiverLocation
        return rivers
    }

    fun resolveBorderOverlayLocations(
        originTerrainNames: Set<String>,
        originBaseTerrainTypeName: String,
        neighbors: Sequence<NeighborEdgeContext>,
        edgeImagesByPosition: Map<NeighborDirection, List<EdgeTileImage>>,
        hasBottomRightRiver: Boolean,
        hasBottomRiver: Boolean,
        hasBottomLeftRiver: Boolean,
        bottomRightRiverLocation: String,
        bottomRiverLocation: String,
        bottomLeftRiverLocation: String
    ): List<String> {
        val edges = resolveEdgeLocations(
            originTerrainNames = originTerrainNames,
            originBaseTerrainTypeName = originBaseTerrainTypeName,
            neighbors = neighbors,
            edgeImagesByPosition = edgeImagesByPosition
        ).toList()
        val rivers = resolveRiverLocations(
            hasBottomRightRiver = hasBottomRightRiver,
            hasBottomRiver = hasBottomRiver,
            hasBottomLeftRiver = hasBottomLeftRiver,
            bottomRightRiverLocation = bottomRightRiverLocation,
            bottomRiverLocation = bottomRiverLocation,
            bottomLeftRiverLocation = bottomLeftRiverLocation
        )
        return edges + rivers
    }

    fun resolveRoadStatus(roadStatus: RoadStatus, neighborRoadStatus: RoadStatus): RoadStatus {
        if (roadStatus == RoadStatus.None || neighborRoadStatus == RoadStatus.None) return RoadStatus.None
        if (roadStatus == RoadStatus.Road || neighborRoadStatus == RoadStatus.Road) return RoadStatus.Road
        return RoadStatus.Railroad
    }

    private fun resolveTerrainImageLocations(
        terrainSequence: List<String>,
        getTile: (String) -> String,
        orFallback: (String) -> String,
        imageExists: (String) -> Boolean,
        ruleVariants: Map<String, Array<String>>
    ): List<String> {
        val allTerrains = terrainSequence.joinToString("+")
        val variant = ruleVariants[allTerrains]
        if (variant != null) return variant.map { getTile(it) }

        val allTerrainTile = getTile(allTerrains)
        if (imageExists(allTerrainTile)) return listOf(allTerrainTile)
        return terrainSequence.map { orFallback(it) }
    }

    private fun resolveImprovementAndResourceImages(
        resourceAndImprovementSequence: List<String>,
        getTile: (String) -> String,
        orFallback: (String) -> String,
        imageExists: (String) -> Boolean
    ): List<String> {
        if (resourceAndImprovementSequence.isEmpty()) return emptyList()
        val allTogether = getTile(resourceAndImprovementSequence.joinToString("+"))
        if (imageExists(allTogether)) return listOf(allTogether)
        return resourceAndImprovementSequence.map { orFallback(it) }
    }

    private fun matchesFilterMinimal(
        terrainNames: Set<String>,
        baseTerrainTypeName: String,
        filter: String
    ): Boolean {
        if (terrainNames.contains(filter)) return true
        if (baseTerrainTypeName == filter) return true
        return false
    }
}
