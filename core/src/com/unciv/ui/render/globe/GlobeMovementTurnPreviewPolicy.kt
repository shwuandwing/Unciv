package com.unciv.ui.render.globe

/**
 * Resolves how many turns to show in the 3D hovered movement preview without throwing.
 */
object GlobeMovementTurnPreviewPolicy {
    data class UnitContext(
        val movesLikeAirUnits: Boolean,
        val isPreparingParadrop: Boolean,
    )

    fun resolveTurns(
        unit: UnitContext,
        canReach: Boolean,
        shortestPathSizeProvider: () -> Int
    ): Int? {
        if (!canReach) return null
        if (unit.movesLikeAirUnits || unit.isPreparingParadrop) return 1

        return try {
            val shortestPathSize = shortestPathSizeProvider()
            if (shortestPathSize <= 0) null else shortestPathSize
        } catch (_: Exception) {
            // Hover preview must never crash render if path probing fails.
            null
        }
    }
}
