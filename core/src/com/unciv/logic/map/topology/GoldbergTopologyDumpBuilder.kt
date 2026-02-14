package com.unciv.logic.map.topology

import com.unciv.logic.map.MapGeneratedMainType
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType

/** Build machine-readable Goldberg topology metadata used by external tooling. */
object GoldbergTopologyDumpBuilder {

    data class RiverWriter(
        var tileIndex: Int = -1,
        var field: String = ""
    )

    data class EdgeEntry(
        var a: Int = -1,
        var b: Int = -1,
        var representable: Boolean = false,
        var clockFromA: Int = -1,
        var writer: RiverWriter? = null
    )

    data class TileEntry(
        var index: Int = -1,
        var x: Int = 0,
        var y: Int = 0,
        var latitude: Double = 0.0,
        var longitude: Double = 0.0,
        var neighbors: IntArray = intArrayOf()
    )

    data class Dump(
        var frequency: Int = 0,
        var layoutId: String = GoldbergNetLayoutBuilder.DEFAULT_LAYOUT,
        var tileCount: Int = 0,
        var ruleset: String = BaseRuleset.Civ_V_GnK.fullName,
        var tiles: List<TileEntry> = listOf(),
        var edges: List<EdgeEntry> = listOf(),
        var mapParametersTemplate: MapParameters = MapParameters()
    )

    fun buildDump(
        frequency: Int,
        ruleset: Ruleset,
        layoutId: String = GoldbergNetLayoutBuilder.DEFAULT_LAYOUT,
        mapName: String = "Earth-Icosahedron"
    ): Dump {
        val tileMap = buildTileMap(frequency, ruleset, layoutId, mapName)
        return buildDumpFromMap(tileMap)
    }

    fun buildTileMap(
        frequency: Int,
        ruleset: Ruleset,
        layoutId: String = GoldbergNetLayoutBuilder.DEFAULT_LAYOUT,
        mapName: String = "Earth-Icosahedron"
    ): TileMap {
        require(frequency > 0) { "Frequency must be > 0" }

        val mapParameters = MapParameters().apply {
            name = mapName
            type = MapGeneratedMainType.custom
            shape = MapShape.icosahedron
            mapSize = MapSize.Medium
            worldWrap = false
            mirroring = com.unciv.logic.map.MirroringType.none
            goldbergFrequency = frequency
            goldbergLayout = layoutId
            baseRuleset = BaseRuleset.Civ_V_GnK.fullName
        }

        val mesh = GoldbergMeshBuilder.build(frequency)
        val layout = GoldbergNetLayoutBuilder.buildIndexToCoord(frequency, mesh, layoutId)

        val tileMap = TileMap(mesh.vertices.size)
        tileMap.mapParameters = mapParameters
        val landTerrain = pickInitialLandTerrain(ruleset)

        for (coord in layout.indexToCoord) {
            tileMap.tileList.add(Tile().apply {
                position = coord.asSerializable()
                baseTerrain = landTerrain
            })
        }
        tileMap.setTransients(ruleset)
        return tileMap
    }

    fun buildDumpFromMap(tileMap: TileMap): Dump {
        val tiles = tileMap.tileList
        val tileEntries = ArrayList<TileEntry>(tiles.size)
        val edgeEntries = ArrayList<EdgeEntry>(tiles.size * 3)

        for (tile in tiles) {
            val neighborList = tile.neighbors.map { it.zeroBasedIndex }.sorted().toList()
            val neighbors = IntArray(neighborList.size) { idx -> neighborList[idx] }
            tileEntries += TileEntry(
                index = tile.zeroBasedIndex,
                x = tile.position.x,
                y = tile.position.y,
                latitude = tile.latitude / 1000.0,
                longitude = tile.longitude / 1000.0,
                neighbors = neighbors,
            )
        }

        for (tile in tiles) {
            for (neighbor in tile.neighbors) {
                if (tile.zeroBasedIndex >= neighbor.zeroBasedIndex) continue
                val clockFromA = tile.tileMap.getNeighborTileClockPosition(tile, neighbor)
                val writer = resolveWriter(tile, neighbor, clockFromA)
                edgeEntries += EdgeEntry(
                    a = tile.zeroBasedIndex,
                    b = neighbor.zeroBasedIndex,
                    representable = writer != null,
                    clockFromA = clockFromA,
                    writer = writer,
                )
            }
        }

        return Dump(
            frequency = tileMap.mapParameters.goldbergFrequency,
            layoutId = tileMap.mapParameters.goldbergLayout,
            tileCount = tiles.size,
            ruleset = tileMap.mapParameters.baseRuleset,
            tiles = tileEntries,
            edges = edgeEntries,
            mapParametersTemplate = tileMap.mapParameters.clone(),
        )
    }

    private fun resolveWriter(tile: Tile, neighbor: Tile, clockFromTile: Int): RiverWriter? {
        val (ownerIndex, fieldName) = when (clockFromTile) {
            4 -> tile.zeroBasedIndex to "hasBottomRightRiver"
            6 -> tile.zeroBasedIndex to "hasBottomRiver"
            8 -> tile.zeroBasedIndex to "hasBottomLeftRiver"
            2 -> neighbor.zeroBasedIndex to "hasBottomLeftRiver"
            10 -> neighbor.zeroBasedIndex to "hasBottomRightRiver"
            12 -> neighbor.zeroBasedIndex to "hasBottomRiver"
            else -> return null
        }
        return RiverWriter(ownerIndex, fieldName)
    }

    private fun pickInitialLandTerrain(ruleset: Ruleset): String {
        if (ruleset.terrains.containsKey("Grassland")) return "Grassland"
        return ruleset.terrains.values.firstOrNull { it.type == TerrainType.Land }?.name
            ?: error("Ruleset has no land terrain")
    }
}
