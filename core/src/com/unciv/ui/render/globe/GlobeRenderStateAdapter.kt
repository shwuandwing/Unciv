package com.unciv.ui.render.globe

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.map.tile.Tile

object GlobeRenderStateAdapter {
    fun tileFillColor(tile: Tile): Color {
        val base = tile.getBaseTerrain().getColor().cpy().lerp(Color.GRAY, 0.42f)

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

    fun borderColor(tile: Tile): Color? = tile.getOwner()?.nation?.getInnerColor()?.cpy()

    fun hasUnitMarker(tile: Tile): Boolean = tile.getUnits().any()

    fun hasCityMarker(tile: Tile): Boolean = tile.isCityCenter()

    fun hasResourceMarker(tile: Tile): Boolean = tile.tileResource != null
}
