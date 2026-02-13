package com.unciv.ui.screens.worldscreen.worldmap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExploredRegionClampPolicyTests {

    @Test
    fun returnsNullWhenBoundsAreFullyAboveScrollDomain() {
        val bounds = ExploredRegionClampPolicy.sanitizeVerticalBounds(
            top = -4805f,
            bottom = -2435f,
            maxY = 4010f
        )
        assertNull(bounds)
    }

    @Test
    fun returnsNullWhenBoundsAreFullyBelowScrollDomain() {
        val bounds = ExploredRegionClampPolicy.sanitizeVerticalBounds(
            top = 5000f,
            bottom = 7000f,
            maxY = 4010f
        )
        assertNull(bounds)
    }

    @Test
    fun clampsPartiallyOverlappingBounds() {
        val bounds = ExploredRegionClampPolicy.sanitizeVerticalBounds(
            top = -100f,
            bottom = 1200f,
            maxY = 1000f
        )
        assertNotNull(bounds)
        assertEquals(0f, bounds!!.top, 0.001f)
        assertEquals(1000f, bounds.bottom, 0.001f)
    }
}

