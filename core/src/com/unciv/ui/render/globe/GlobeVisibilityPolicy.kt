package com.unciv.ui.render.globe

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile

object GlobeVisibilityPolicy {
    data class Context(
        val fogOfWarEnabled: Boolean = false,
        val viewingCiv: Civilization? = null
    )

    data class TileVisibility(
        val isExplored: Boolean = true,
        val isVisible: Boolean = true
    ) {
        val allowsInteraction: Boolean get() = isExplored
        val shouldRenderFogOverlay: Boolean get() = isExplored && !isVisible
    }

    fun resolve(tile: Tile, context: Context): TileVisibility {
        if (!context.fogOfWarEnabled) return TileVisibility()
        val civ = context.viewingCiv ?: return TileVisibility()
        val isExplored = tile.isExplored(civ)
        val isVisible = isExplored && tile.isVisible(civ)
        return TileVisibility(isExplored = isExplored, isVisible = isVisible)
    }
}
