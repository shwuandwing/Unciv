package com.unciv.ui.render.globe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

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

    @Test
    fun resolvesProjectedEdgeFacingNeighborDirection() {
        val centerX = 0f
        val centerY = 0f
        val polygon = floatArrayOf(
            0f, 10f,
            8.660254f, 5f,
            8.660254f, -5f,
            0f, -10f,
            -8.660254f, -5f,
            -8.660254f, 5f
        )

        val edge = GlobeBorderStripPlacementPolicy.resolveEdgeFacingNeighbor(
            polygon = polygon,
            centerX = centerX,
            centerY = centerY,
            neighborX = 20f,
            neighborY = 0f
        )

        assertTrue(edge != null)
        assertEquals(8.660254f, edge!!.midX, 0.0005f)
        assertEquals(0f, edge.midY, 0.0005f)
        assertEquals(10f, edge.length, 0.0005f)
    }

    @Test
    fun edgePlacementAnchorsBottomCenterOnEdgeMidpoint() {
        val edge = GlobeBorderStripPlacementPolicy.Edge(
            startX = 10f,
            startY = 5f,
            endX = 10f,
            endY = -5f,
            midX = 10f,
            midY = 0f,
            length = 10f
        )

        val placement = GlobeBorderStripPlacementPolicy.resolve(
            edge = edge,
            tileCenterX = 0f,
            tileCenterY = 0f,
            regionWidth = 81,
            regionHeight = 15,
            preferredRotationDegrees = 270f
        )

        val expectedWidth = 10f * sqrt(3f)
        val expectedHeight = expectedWidth * (15f / 81f)
        assertEquals(expectedWidth, placement.width, 0.0005f)
        assertEquals(expectedHeight, placement.height, 0.0005f)
        assertEquals(10f, placement.x + placement.originX, 0.0005f)
        assertEquals(0f, placement.y + placement.originY, 0.0005f)
        // Even with opposite preferred direction, placement chooses the orientation whose
        // local +Y points toward the tile center (inward fill).
        assertEquals(90f, placement.rotation, 0.0005f)
    }

}
