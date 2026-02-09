package com.unciv.ui.render.globe

import org.junit.Assert.assertEquals
import org.junit.Test

class GlobeUnitFlagStylePolicyTests {
    @Test
    fun `embarked style has embarked assets`() {
        val style = GlobeUnitFlagStylePolicy.resolve(
            GlobeUnitFlagStylePolicy.Context(
                isEmbarked = true,
                isFortified = false,
                isGuarding = false,
                isCivilian = false
            )
        )
        assertEquals("UnitFlagIcons/UnitFlagEmbark", style.baseLocation)
        assertEquals("UnitFlagIcons/UnitFlagSelectionEmbark", style.selectionLocation)
    }

    @Test
    fun `fortified style has fortify assets`() {
        val style = GlobeUnitFlagStylePolicy.resolve(
            GlobeUnitFlagStylePolicy.Context(
                isEmbarked = false,
                isFortified = true,
                isGuarding = false,
                isCivilian = false
            )
        )
        assertEquals("UnitFlagIcons/UnitFlagFortify", style.baseLocation)
        assertEquals("UnitFlagIcons/UnitFlagSelectionFortify", style.selectionLocation)
    }

    @Test
    fun `civilian style has civilian assets`() {
        val style = GlobeUnitFlagStylePolicy.resolve(
            GlobeUnitFlagStylePolicy.Context(
                isEmbarked = false,
                isFortified = false,
                isGuarding = false,
                isCivilian = true
            )
        )
        assertEquals("UnitFlagIcons/UnitFlagCivilian", style.baseLocation)
        assertEquals("UnitFlagIcons/UnitFlagSelectionCivilian", style.selectionLocation)
    }
}

