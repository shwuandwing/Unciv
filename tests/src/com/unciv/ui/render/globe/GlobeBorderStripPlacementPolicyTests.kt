package com.unciv.ui.render.globe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeBorderStripPlacementPolicyTests {
    @Test
    fun resolvesPlacementWithHexFrameAnchoring() {
        val frame = GlobeOverlayFramePolicy.Frame(
            width = 120f,
            height = 100f,
            centerX = 300f,
            centerY = 220f
        )

        val placement = GlobeBorderStripPlacementPolicy.resolve(
            frame = frame,
            regionWidth = 81,
            regionHeight = 15,
            rotationDegrees = 63f
        )

        assertEquals(120f, placement.width, 0.0001f)
        assertEquals(120f * (15f / 81f), placement.height, 0.0001f)
        assertEquals(300f - 60f, placement.x, 0.0001f)
        assertEquals(220f - 50f, placement.y, 0.0001f)
        assertEquals(60f, placement.originX, 0.0001f)
        assertEquals(50f, placement.originY, 0.0001f)
        assertEquals(63f, placement.rotation, 0.0001f)
    }

    @Test
    fun stripPlacementRemainsEdgeLocalInUnrotatedFrame() {
        val frame = GlobeOverlayFramePolicy.Frame(
            width = 140f,
            height = 120f,
            centerX = 400f,
            centerY = 310f
        )

        val placement = GlobeBorderStripPlacementPolicy.resolve(
            frame = frame,
            regionWidth = 81,
            regionHeight = 15,
            rotationDegrees = 0f
        )

        // Border strip should remain much thinner than the full tile frame
        // and be anchored to the frame edge band before rotation.
        assertTrue(placement.height < frame.height * 0.25f)
        assertTrue(placement.y <= frame.centerY - frame.height / 2f + 0.0001f)
        assertTrue(placement.y + placement.height < frame.centerY)
    }

}
