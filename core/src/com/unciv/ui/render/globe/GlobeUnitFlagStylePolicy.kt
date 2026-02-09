package com.unciv.ui.render.globe

import com.unciv.logic.map.mapunit.MapUnit

object GlobeUnitFlagStylePolicy {
    data class Context(
        val isEmbarked: Boolean,
        val isFortified: Boolean,
        val isGuarding: Boolean,
        val isCivilian: Boolean
    )

    data class Style(
        val baseLocation: String,
        val innerLocation: String?,
        val selectionLocation: String
    )

    fun resolve(unit: MapUnit): Style {
        return resolve(
            Context(
                isEmbarked = unit.isEmbarked(),
                isFortified = unit.isFortified(),
                isGuarding = unit.isGuarding(),
                isCivilian = unit.isCivilian()
            )
        )
    }

    fun resolve(context: Context): Style {
        return when {
            context.isEmbarked -> Style(
                baseLocation = "UnitFlagIcons/UnitFlagEmbark",
                innerLocation = "UnitFlagIcons/UnitFlagEmbarkInner",
                selectionLocation = "UnitFlagIcons/UnitFlagSelectionEmbark"
            )
            context.isFortified || context.isGuarding -> Style(
                baseLocation = "UnitFlagIcons/UnitFlagFortify",
                innerLocation = "UnitFlagIcons/UnitFlagFortifyInner",
                selectionLocation = "UnitFlagIcons/UnitFlagSelectionFortify"
            )
            context.isCivilian -> Style(
                baseLocation = "UnitFlagIcons/UnitFlagCivilian",
                innerLocation = "UnitFlagIcons/UnitFlagCivilianInner",
                selectionLocation = "UnitFlagIcons/UnitFlagSelectionCivilian"
            )
            else -> Style(
                baseLocation = "UnitFlagIcons/UnitFlag",
                innerLocation = "UnitFlagIcons/UnitFlagInner",
                selectionLocation = "UnitFlagIcons/UnitFlagSelection"
            )
        }
    }
}
