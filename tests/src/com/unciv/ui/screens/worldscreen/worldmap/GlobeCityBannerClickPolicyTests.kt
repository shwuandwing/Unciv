package com.unciv.ui.screens.worldscreen.worldmap

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobeCityBannerClickPolicyTests {
    @Test
    fun opensCityWhenSameCityAlreadySelectedAndEntryAllowed() {
        val action = GlobeCityBannerClickPolicy.resolve(
            GlobeCityBannerClickPolicy.Context(
                selectedCityMatchesClickedCity = true,
                isReadOnlyRenderMode = false,
                isViewingOwnCity = true,
                viewingCivIsSpectator = false,
                debugVisibleMap = false,
                selectedUnitIsAirUnitInClickedCity = false
            )
        )

        assertEquals(GlobeCityBannerClickPolicy.Action.OpenCity, action)
    }

    @Test
    fun doesNotOpenCityWhenReadOnlyModeIsActive() {
        val action = GlobeCityBannerClickPolicy.resolve(
            GlobeCityBannerClickPolicy.Context(
                selectedCityMatchesClickedCity = true,
                isReadOnlyRenderMode = true,
                isViewingOwnCity = true,
                viewingCivIsSpectator = false,
                debugVisibleMap = true,
                selectedUnitIsAirUnitInClickedCity = false
            )
        )

        assertEquals(GlobeCityBannerClickPolicy.Action.DelegateToTileClick, action)
    }

    @Test
    fun doesNotOpenCityWhenSelectingAirUnitInsideCity() {
        val action = GlobeCityBannerClickPolicy.resolve(
            GlobeCityBannerClickPolicy.Context(
                selectedCityMatchesClickedCity = true,
                isReadOnlyRenderMode = false,
                isViewingOwnCity = true,
                viewingCivIsSpectator = false,
                debugVisibleMap = false,
                selectedUnitIsAirUnitInClickedCity = true
            )
        )

        assertEquals(GlobeCityBannerClickPolicy.Action.DelegateToTileClick, action)
    }

    @Test
    fun firstClickDelegatesToTileSelectionWhenCityNotSelectedYet() {
        val action = GlobeCityBannerClickPolicy.resolve(
            GlobeCityBannerClickPolicy.Context(
                selectedCityMatchesClickedCity = false,
                isReadOnlyRenderMode = false,
                isViewingOwnCity = true,
                viewingCivIsSpectator = false,
                debugVisibleMap = false,
                selectedUnitIsAirUnitInClickedCity = false
            )
        )

        assertEquals(GlobeCityBannerClickPolicy.Action.DelegateToTileClick, action)
    }
}
