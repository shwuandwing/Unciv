package com.unciv.ui.render.globe

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization

object GlobeCityStatusOverlayPolicy {
    data class Context(
        val sameAsSelectedCiv: Boolean,
        val isCapital: Boolean,
        val isBlockaded: Boolean,
        val isConnectedToCapital: Boolean,
        val isInResistance: Boolean,
        val isPuppet: Boolean,
        val isBeingRazed: Boolean,
        val isWeLoveTheKingDayActive: Boolean
    )

    fun resolveStatusIcons(city: City, selectedCiv: Civilization?): List<String> {
        val context = Context(
            sameAsSelectedCiv = selectedCiv != null && city.civ == selectedCiv,
            isCapital = city.isCapital(),
            isBlockaded = city.isBlockaded(),
            isConnectedToCapital = city.isConnectedToCapital(),
            isInResistance = city.isInResistance(),
            isPuppet = city.isPuppet,
            isBeingRazed = city.isBeingRazed,
            isWeLoveTheKingDayActive = city.isWeLoveTheKingDayActive()
        )
        return resolveStatusIcons(context)
    }

    fun resolveStatusIcons(context: Context): List<String> {
        val icons = ArrayList<String>(5)

        if (context.sameAsSelectedCiv) {
            if (context.isBlockaded) icons += "OtherIcons/Blockade"
            else if (!context.isCapital && context.isConnectedToCapital) icons += "StatIcons/CityConnection"
        }

        if (context.isInResistance) icons += "StatIcons/Resistance"
        if (context.isPuppet) icons += "OtherIcons/Puppet"
        if (context.isBeingRazed) icons += "OtherIcons/Fire"
        if (context.sameAsSelectedCiv && context.isWeLoveTheKingDayActive) icons += "OtherIcons/WLTKD"

        return icons
    }
}

