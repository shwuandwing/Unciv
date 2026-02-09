package com.unciv.ui.render.globe

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.logic.map.tile.Tile

object GlobeRenderStateAdapter {
    fun tileFillColor(tile: Tile, useColorAsBaseTerrain: Boolean): Color {
        val base = baseTerrainColor(tile.baseTerrain, tile.getBaseTerrain().getColor(), useColorAsBaseTerrain)

        if (tile.terrainFeatures.isNotEmpty()) {
            base.lerp(Color(0.36f, 0.62f, 0.33f, 1f), 0.2f)
        }
        if (tile.naturalWonder != null) {
            base.lerp(Color.GOLD, 0.32f)
        }
        if (tile.tileResource != null) {
            base.lerp(Color(1f, 0.9f, 0.3f, 1f), 0.18f)
        }

        val owner = tile.getOwner()
        if (owner != null && !tile.isWater) {
            base.lerp(owner.nation.getOuterColor().cpy(), 0.28f)
        }
        if (tile.isCityCenter()) {
            val cityOwner = owner
            if (cityOwner != null) {
                return cityOwner.nation.getInnerColor().cpy().lerp(Color.WHITE, 0.18f)
            }
        }

        return base
    }

    fun baseTerrainColor(baseTerrain: String, terrainColor: Color, useColorAsBaseTerrain: Boolean): Color {
        return if (useColorAsBaseTerrain) {
            terrainColor.cpy().lerp(Color.GRAY, 0.42f)
        } else {
            when (baseTerrain) {
                Constants.tundra -> Color(0.84f, 0.88f, 0.84f, 1f)
                Constants.snow -> Color(0.94f, 0.96f, 0.98f, 1f)
                else -> terrainColor.cpy().lerp(Color.GRAY, 0.35f)
            }
        }
    }

    fun borderColor(tile: Tile): Color? = tile.getOwner()?.nation?.getInnerColor()?.cpy()

    fun hasUnitMarker(tile: Tile): Boolean = tile.getUnits().any()

    fun hasCityMarker(tile: Tile): Boolean = tile.isCityCenter()

    fun hasResourceMarker(tile: Tile): Boolean = tile.tileResource != null
}
