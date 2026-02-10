package com.unciv.ui.render.globe

import com.badlogic.gdx.graphics.Color
import com.unciv.ui.components.extensions.brighten

/**
 * Mirrors world-map selected-unit tile highlight decisions used by the 2D path
 * (`WorldMapTileUpdater.updateTilesForSelectedUnit`) and exposes them for globe rendering.
 */
object GlobeMovementHighlightPolicy {
    data class UnitContext(
        val movesLikeAirUnits: Boolean,
        val isPreparingParadrop: Boolean
    )

    data class TileContext(
        val isReachableThisTurn: Boolean,
        val canMoveTo: Boolean,
        val assumePassableUnknown: Boolean
    )

    data class Settings(
        val useCirclesToIndicateMovableTiles: Boolean,
        val singleTapMove: Boolean
    )

    data class Highlight(
        val color: Color,
        val alpha: Float,
        val drawAsCircle: Boolean
    )

    fun resolve(unit: UnitContext, tile: TileContext, settings: Settings): Highlight? {
        if (!tile.isReachableThisTurn) return null

        val shouldHighlight = tile.canMoveTo
            || (tile.assumePassableUnknown && !unit.movesLikeAirUnits)
        if (!shouldHighlight) return null

        val moveTileOverlayColor = if (unit.isPreparingParadrop) Color.BLUE else Color.WHITE
        if (settings.useCirclesToIndicateMovableTiles) {
            val alpha = if (settings.singleTapMove) 0.7f else 0.3f
            return Highlight(
                color = moveTileOverlayColor.cpy(),
                alpha = alpha,
                drawAsCircle = true
            )
        }

        // 2D overlayTerrain(color) behavior: brighten(0.3f) + fixed alpha 0.4
        return Highlight(
            color = moveTileOverlayColor.cpy().brighten(0.3f),
            alpha = 0.4f,
            drawAsCircle = false
        )
    }
}
