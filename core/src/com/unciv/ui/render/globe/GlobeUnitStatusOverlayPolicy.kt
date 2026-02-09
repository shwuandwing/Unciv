package com.unciv.ui.render.globe

import com.unciv.logic.map.mapunit.MapUnit

object GlobeUnitStatusOverlayPolicy {
    data class Context(
        val isSleeping: Boolean,
        val improvementInProgress: String?,
        val canBuildImprovementInProgress: Boolean,
        val isEscorting: Boolean,
        val isMoving: Boolean,
        val isExploring: Boolean,
        val isAutomated: Boolean,
        val isSetUpForSiege: Boolean
    )

    fun actionIconLocation(unit: MapUnit): String? {
        val improvementInProgress = unit.getTile().improvementInProgress
        return actionIconLocation(
            Context(
                isSleeping = unit.isSleeping(),
                improvementInProgress = improvementInProgress,
                canBuildImprovementInProgress =
                    improvementInProgress != null
                        && unit.canBuildImprovement(unit.getTile().getTileImprovementInProgress()!!),
                isEscorting = unit.isEscorting(),
                isMoving = unit.isMoving(),
                isExploring = unit.isExploring(),
                isAutomated = unit.isAutomated(),
                isSetUpForSiege = unit.isSetUpForSiege()
            )
        )
    }

    fun actionIconLocation(context: Context): String? {
        return when {
            context.isSleeping -> "UnitActionIcons/Sleep"
            context.improvementInProgress != null && context.canBuildImprovementInProgress ->
                "ImprovementIcons/${context.improvementInProgress}"
            context.isEscorting -> "UnitActionIcons/Escort"
            context.isMoving -> "UnitActionIcons/MoveTo"
            context.isExploring -> "UnitActionIcons/Explore"
            context.isAutomated -> "UnitActionIcons/Automate"
            context.isSetUpForSiege -> "UnitActionIcons/SetUp"
            else -> null
        }
    }
}

