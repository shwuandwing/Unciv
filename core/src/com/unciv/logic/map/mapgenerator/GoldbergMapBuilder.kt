package com.unciv.logic.map.mapgenerator

import com.unciv.logic.map.GoldbergFrequency
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.topology.GoldbergMeshBuilder
import com.unciv.logic.map.topology.GoldbergNetLayoutBuilder
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.TerrainType

object GoldbergMapBuilder {

    fun build(mapParameters: MapParameters, ruleset: Ruleset): TileMap {
        val frequency = GoldbergFrequency.selectForMapSize(mapParameters.mapSize)
        mapParameters.goldbergFrequency = frequency
        if (mapParameters.goldbergLayout.isEmpty())
            mapParameters.goldbergLayout = GoldbergNetLayoutBuilder.DEFAULT_LAYOUT

        val mesh = GoldbergMeshBuilder.build(frequency)
        val layout = GoldbergNetLayoutBuilder.buildIndexToCoord(frequency, mesh, mapParameters.goldbergLayout)

        val map = TileMap(mesh.vertices.size)
        map.mapParameters = mapParameters
        val firstAvailableLandTerrain = MapLandmassGenerator.getInitializationTerrain(ruleset, TerrainType.Land)
        for (coord in layout.indexToCoord) {
            map.tileList.add(Tile().apply {
                position = coord.asSerializable()
                baseTerrain = firstAvailableLandTerrain
            })
        }
        map.setTransients(ruleset)
        return map
    }
}
