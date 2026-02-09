package com.unciv.ui.render.globe

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobeCityStatusOverlayPolicyTests {
    @Test
    fun `blockade takes precedence over city connection`() {
        val icons = GlobeCityStatusOverlayPolicy.resolveStatusIcons(
            GlobeCityStatusOverlayPolicy.Context(
                sameAsSelectedCiv = true,
                isCapital = false,
                isBlockaded = true,
                isConnectedToCapital = true,
                isInResistance = false,
                isPuppet = false,
                isBeingRazed = false,
                isWeLoveTheKingDayActive = false
            )
        )
        assertEquals(listOf("OtherIcons/Blockade"), icons)
    }

    @Test
    fun `city connection shown for selected non-capital city`() {
        val icons = GlobeCityStatusOverlayPolicy.resolveStatusIcons(
            GlobeCityStatusOverlayPolicy.Context(
                sameAsSelectedCiv = true,
                isCapital = false,
                isBlockaded = false,
                isConnectedToCapital = true,
                isInResistance = false,
                isPuppet = false,
                isBeingRazed = false,
                isWeLoveTheKingDayActive = false
            )
        )
        assertEquals(listOf("StatIcons/CityConnection"), icons)
    }

    @Test
    fun `status icons follow 2d ordering`() {
        val icons = GlobeCityStatusOverlayPolicy.resolveStatusIcons(
            GlobeCityStatusOverlayPolicy.Context(
                sameAsSelectedCiv = true,
                isCapital = false,
                isBlockaded = false,
                isConnectedToCapital = false,
                isInResistance = true,
                isPuppet = true,
                isBeingRazed = true,
                isWeLoveTheKingDayActive = true
            )
        )
        assertEquals(
            listOf(
                "StatIcons/Resistance",
                "OtherIcons/Puppet",
                "OtherIcons/Fire",
                "OtherIcons/WLTKD"
            ),
            icons
        )
    }

    @Test
    fun `non selected city omits selected-only statuses`() {
        val icons = GlobeCityStatusOverlayPolicy.resolveStatusIcons(
            GlobeCityStatusOverlayPolicy.Context(
                sameAsSelectedCiv = false,
                isCapital = false,
                isBlockaded = true,
                isConnectedToCapital = true,
                isInResistance = false,
                isPuppet = false,
                isBeingRazed = false,
                isWeLoveTheKingDayActive = true
            )
        )
        assertEquals(emptyList<String>(), icons)
    }
}

