package com.unciv.ui.screens.worldscreen.worldmap

object GlobeCityBannerClickPolicy {
    enum class Action {
        OpenCity,
        DelegateToTileClick
    }

    data class Context(
        val selectedCityMatchesClickedCity: Boolean,
        val isReadOnlyRenderMode: Boolean,
        val isViewingOwnCity: Boolean,
        val viewingCivIsSpectator: Boolean,
        val debugVisibleMap: Boolean,
        val selectedUnitIsAirUnitInClickedCity: Boolean
    )

    fun resolve(context: Context): Action {
        if (context.isReadOnlyRenderMode) return Action.DelegateToTileClick
        if (!context.selectedCityMatchesClickedCity) return Action.DelegateToTileClick

        val canEnterCity = context.debugVisibleMap
            || context.viewingCivIsSpectator
            || (context.isViewingOwnCity && !context.selectedUnitIsAirUnitInClickedCity)
        return if (canEnterCity) Action.OpenCity else Action.DelegateToTileClick
    }
}
