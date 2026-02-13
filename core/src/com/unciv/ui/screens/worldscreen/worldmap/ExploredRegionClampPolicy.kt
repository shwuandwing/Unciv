package com.unciv.ui.screens.worldscreen.worldmap

object ExploredRegionClampPolicy {
    data class VerticalBounds(val top: Float, val bottom: Float)

    /**
     * Convert explored-region Y bounds into a valid scroll clamp range.
     * Returns null when the bounds are invalid or completely outside the scroll domain,
     * in which case callers should skip Y clamping.
     */
    fun sanitizeVerticalBounds(top: Float, bottom: Float, maxY: Float): VerticalBounds? {
        if (!top.isFinite() || !bottom.isFinite() || !maxY.isFinite()) return null
        if (top > bottom) return null
        if (maxY < 0f) return null

        // Explored region is fully outside the valid scroll domain.
        if (bottom < 0f || top > maxY) return null

        val clampedTop = top.coerceIn(0f, maxY)
        val clampedBottom = bottom.coerceIn(0f, maxY)
        if (clampedTop > clampedBottom) return null

        return VerticalBounds(clampedTop, clampedBottom)
    }
}

