package com.unciv.ui.render.globe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlobeUnitStatusOverlayPolicyTests {
    @Test
    fun `sleep action takes highest priority`() {
        val location = GlobeUnitStatusOverlayPolicy.actionIconLocation(
            GlobeUnitStatusOverlayPolicy.Context(
                isSleeping = true,
                improvementInProgress = "Farm",
                canBuildImprovementInProgress = true,
                isEscorting = true,
                isMoving = true,
                isExploring = true,
                isAutomated = true,
                isSetUpForSiege = true
            )
        )
        assertEquals("UnitActionIcons/Sleep", location)
    }

    @Test
    fun `improvement icon appears when unit can build current improvement`() {
        val location = GlobeUnitStatusOverlayPolicy.actionIconLocation(
            GlobeUnitStatusOverlayPolicy.Context(
                isSleeping = false,
                improvementInProgress = "Farm",
                canBuildImprovementInProgress = true,
                isEscorting = false,
                isMoving = false,
                isExploring = false,
                isAutomated = false,
                isSetUpForSiege = false
            )
        )
        assertEquals("ImprovementIcons/Farm", location)
    }

    @Test
    fun `improvement icon is skipped when current improvement cannot be built`() {
        val location = GlobeUnitStatusOverlayPolicy.actionIconLocation(
            GlobeUnitStatusOverlayPolicy.Context(
                isSleeping = false,
                improvementInProgress = "Farm",
                canBuildImprovementInProgress = false,
                isEscorting = true,
                isMoving = false,
                isExploring = false,
                isAutomated = false,
                isSetUpForSiege = false
            )
        )
        assertEquals("UnitActionIcons/Escort", location)
    }

    @Test
    fun `returns null when no action applies`() {
        val location = GlobeUnitStatusOverlayPolicy.actionIconLocation(
            GlobeUnitStatusOverlayPolicy.Context(
                isSleeping = false,
                improvementInProgress = null,
                canBuildImprovementInProgress = false,
                isEscorting = false,
                isMoving = false,
                isExploring = false,
                isAutomated = false,
                isSetUpForSiege = false
            )
        )
        assertNull(location)
    }
}

