package com.unciv.ui.render.globe

import com.unciv.logic.files.MapSaver
import com.unciv.logic.map.NeighborDirection
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.ui.components.tilegroups.layers.EdgeTileImage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class GlobeTileOverlayResolverTests {

    @Test
    fun resolvesRuleVariantForTundraAncientRuins() {
        val locations = GlobeTileOverlayResolver.resolveTerrainLayerLocations(
            baseTerrain = "Tundra",
            terrainFeatures = emptyList(),
            naturalWonder = null,
            shownImprovement = "Ancient ruins",
            improvementIsPillaged = false,
            resource = null,
            showPixelImprovements = true,
            canSeeResource = true,
            useColorAsBaseTerrain = false,
            useSummaryImages = false,
            hexagonLocation = "hex",
            naturalWonderSummaryLocation = "wonder",
            edgeLocations = emptySequence(),
            getTile = { key -> "tile/$key" },
            orFallback = { key -> "fallback/$key" },
            imageExists = { false },
            ruleVariants = mapOf("Tundra+Ancient ruins" to arrayOf("Tundra", "Ancient ruins-Snow"))
        )

        assertEquals(listOf("tile/Tundra", "tile/Ancient ruins-Snow"), locations)
    }

    @Test
    fun resolvesEdgeLocationsUsingMinimalTerrainFilters() {
        val edges = GlobeTileOverlayResolver.resolveEdgeLocations(
            originTerrainNames = setOf("Tundra", "Forest"),
            originBaseTerrainTypeName = "Land",
            neighbors = sequenceOf(
                GlobeTileOverlayResolver.NeighborEdgeContext(
                    direction = NeighborDirection.BottomRight,
                    terrainNames = setOf("Coast"),
                    baseTerrainTypeName = "Water"
                )
            ),
            edgeImagesByPosition = mapOf(
                NeighborDirection.BottomRight to listOf(
                    EdgeTileImage("edge-forest-water", "Forest", "Water", NeighborDirection.BottomRight),
                    EdgeTileImage("edge-desert-water", "Desert", "Water", NeighborDirection.BottomRight)
                )
            )
        ).toList()

        assertEquals(listOf("edge-forest-water"), edges)
    }

    @Test
    fun resolvesRiverLocationsByFlags() {
        val rivers = GlobeTileOverlayResolver.resolveRiverLocations(
            hasBottomRightRiver = true,
            hasBottomRiver = false,
            hasBottomLeftRiver = true,
            bottomRightRiverLocation = "river-br",
            bottomRiverLocation = "river-b",
            bottomLeftRiverLocation = "river-bl"
        )

        assertEquals(listOf("river-br", "river-bl"), rivers)
    }

    @Test
    fun resolvesBorderOverlaysIncludingEdgesAndRivers() {
        val overlays = GlobeTileOverlayResolver.resolveBorderOverlayLocations(
            originTerrainNames = setOf("Tundra", "Forest"),
            originBaseTerrainTypeName = "Land",
            neighbors = sequenceOf(
                GlobeTileOverlayResolver.NeighborEdgeContext(
                    direction = NeighborDirection.BottomRight,
                    terrainNames = setOf("Coast"),
                    baseTerrainTypeName = "Water"
                )
            ),
            edgeImagesByPosition = mapOf(
                NeighborDirection.BottomRight to listOf(
                    EdgeTileImage("edge-forest-water", "Forest", "Water", NeighborDirection.BottomRight)
                )
            ),
            hasBottomRightRiver = true,
            hasBottomRiver = false,
            hasBottomLeftRiver = true,
            bottomRightRiverLocation = "river-br",
            bottomRiverLocation = "river-b",
            bottomLeftRiverLocation = "river-bl"
        )

        assertEquals(listOf("edge-forest-water", "river-br", "river-bl"), overlays)
    }

    @Test
    fun roadStatusResolutionFollowsRoadOverRailroadRule() {
        assertEquals(
            RoadStatus.Road,
            GlobeTileOverlayResolver.resolveRoadStatus(RoadStatus.Road, RoadStatus.Railroad)
        )
        assertEquals(
            RoadStatus.Railroad,
            GlobeTileOverlayResolver.resolveRoadStatus(RoadStatus.Railroad, RoadStatus.Railroad)
        )
        assertEquals(
            RoadStatus.None,
            GlobeTileOverlayResolver.resolveRoadStatus(RoadStatus.None, RoadStatus.Railroad)
        )
    }

    @Test
    fun testMapTundraAncientRuinsTileResolvesToSnowRuinsVariant() {
        val mapPath = if (Files.exists(Paths.get("maps/Test"))) Paths.get("maps/Test") else Paths.get("android/assets/maps/Test")
        val mapText = Files.readString(mapPath)
        val map = MapSaver.mapFromSavedString(mapText)
        val tile = map.tileList.first { it.position.x == 102 && it.position.y == 61 }

        assertEquals("Tundra", tile.baseTerrain)
        assertEquals("Ancient ruins", tile.improvement)

        val locations = GlobeTileOverlayResolver.resolveTerrainLayerLocations(
            baseTerrain = tile.baseTerrain,
            terrainFeatures = tile.terrainFeatures,
            naturalWonder = tile.naturalWonder,
            shownImprovement = tile.improvement,
            improvementIsPillaged = tile.improvementIsPillaged,
            resource = tile.resource,
            showPixelImprovements = true,
            canSeeResource = true,
            useColorAsBaseTerrain = false,
            useSummaryImages = false,
            hexagonLocation = "hex",
            naturalWonderSummaryLocation = "wonder",
            edgeLocations = emptySequence(),
            getTile = { key -> "tile/$key" },
            orFallback = { key -> "fallback/$key" },
            imageExists = { false },
            ruleVariants = mapOf("Tundra+Ancient ruins" to arrayOf("Tundra", "Ancient ruins-Snow"))
        )

        assertEquals(listOf("tile/Tundra", "tile/Ancient ruins-Snow"), locations)
    }

    @Test
    fun testMapRiverTile105_46ResolvesBottomAndBottomLeftEdges() {
        val mapPath = if (Files.exists(Paths.get("maps/Test"))) Paths.get("maps/Test") else Paths.get("android/assets/maps/Test")
        val mapText = Files.readString(mapPath)
        val map = MapSaver.mapFromSavedString(mapText)
        val tile = map.tileList.first { it.position.x == 105 && it.position.y == 46 }

        val locations = GlobeTileOverlayResolver.resolveRiverLocations(
            hasBottomRightRiver = tile.hasBottomRightRiver,
            hasBottomRiver = tile.hasBottomRiver,
            hasBottomLeftRiver = tile.hasBottomLeftRiver,
            bottomRightRiverLocation = "river-br",
            bottomRiverLocation = "river-b",
            bottomLeftRiverLocation = "river-bl"
        )

        assertEquals(false, tile.hasBottomRightRiver)
        assertEquals(true, tile.hasBottomRiver)
        assertEquals(true, tile.hasBottomLeftRiver)
        assertEquals(listOf("river-b", "river-bl"), locations)
    }
}
