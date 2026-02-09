package com.unciv.ui.render.globe

import com.badlogic.gdx.math.Vector2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeOverlayFramePolicyTests {

    @Test
    fun regularPointyHexComputesExpectedFrameAtZeroRotation() {
        val polygon = floatArrayOf(
            0f, 1f,
            0.866f, 0.5f,
            0.866f, -0.5f,
            0f, -1f,
            -0.866f, -0.5f,
            -0.866f, 0.5f
        )

        val frame = GlobeOverlayFramePolicy.fromPolygon(
            center = Vector2.Zero,
            polygon = polygon,
            rotationDegrees = 0f,
            minimumSize = 0f
        )

        assertEquals(1.732f, frame.width, 0.01f)
        assertEquals(2f, frame.height, 0.01f)
        assertEquals(0f, frame.centerX, 0.001f)
        assertEquals(0f, frame.centerY, 0.001f)
    }

    @Test
    fun regularPointyHexFrameSwapsAxesAtNinetyDegrees() {
        val polygon = floatArrayOf(
            0f, 1f,
            0.866f, 0.5f,
            0.866f, -0.5f,
            0f, -1f,
            -0.866f, -0.5f,
            -0.866f, 0.5f
        )

        val frame = GlobeOverlayFramePolicy.fromPolygon(
            center = Vector2.Zero,
            polygon = polygon,
            rotationDegrees = 90f,
            minimumSize = 0f
        )

        assertEquals(2f, frame.width, 0.01f)
        assertEquals(1.732f, frame.height, 0.01f)
    }

    @Test
    fun highlyDistortedPolygonStillProducesNonCollapsedFrame() {
        val polygon = floatArrayOf(
            0f, 1.2f,
            1.3f, 0.22f,
            1.0f, -0.06f,
            0f, -0.25f,
            -1.1f, -0.08f,
            -1.35f, 0.2f
        )

        val frame = GlobeOverlayFramePolicy.fromPolygon(
            center = Vector2.Zero,
            polygon = polygon,
            rotationDegrees = 12f
        )

        assertTrue("frame width should be meaningful for horizon-distorted tile", frame.width > 2f)
        assertTrue("frame height should be meaningful for horizon-distorted tile", frame.height > 1f)
    }

    @Test
    fun asymmetricPolygonComputesFrameCenterOffset() {
        val center = Vector2(10f, 20f)
        val polygon = floatArrayOf(
            11.2f, 21.0f,
            12.0f, 20.6f,
            12.4f, 19.6f,
            11.1f, 19.0f,
            9.5f, 19.2f,
            9.0f, 20.2f
        )

        val frame = GlobeOverlayFramePolicy.fromPolygon(
            center = center,
            polygon = polygon,
            rotationDegrees = 0f,
            minimumSize = 0f
        )

        assertTrue("frame center should shift from projected center on asymmetric polygons", kotlin.math.abs(frame.centerX - center.x) > 0.05f)
    }

    @Test
    fun directionalOverlayUsesDirectionalFrameBasis() {
        val center = Vector2(10f, 20f)
        val polygon = floatArrayOf(
            11.2f, 21.0f,
            12.0f, 20.6f,
            12.4f, 19.6f,
            11.1f, 19.0f,
            9.5f, 19.2f,
            9.0f, 20.2f
        )

        val directionalOverlay = GlobeCenterOverlayPolicy.Overlay(
            location = "TileSets/HexaRealm/Tiles/River-Bottom",
            isDirectional = true
        )

        val expectedDirectionalFrame = GlobeOverlayFramePolicy.fromPolygon(
            center = center,
            polygon = polygon,
            rotationDegrees = 72f,
            minimumSize = 0f
        )
        val actual = GlobeOverlayFramePolicy.frameForOverlay(
            center = center,
            polygon = polygon,
            regularRotationDegrees = 12f,
            directionalRotationDegrees = 72f,
            overlay = directionalOverlay,
            minimumSize = 0f
        )

        assertEquals(expectedDirectionalFrame.centerX, actual.centerX, 1e-4f)
        assertEquals(expectedDirectionalFrame.centerY, actual.centerY, 1e-4f)
        assertEquals(expectedDirectionalFrame.width, actual.width, 1e-4f)
        assertEquals(expectedDirectionalFrame.height, actual.height, 1e-4f)
    }

    @Test
    fun regularOverlayUsesRegularFrameBasis() {
        val center = Vector2(10f, 20f)
        val polygon = floatArrayOf(
            11.2f, 21.0f,
            12.0f, 20.6f,
            12.4f, 19.6f,
            11.1f, 19.0f,
            9.5f, 19.2f,
            9.0f, 20.2f
        )

        val regularOverlay = GlobeCenterOverlayPolicy.Overlay(location = "TileSets/HexaRealm/Tiles/Desert")
        val expectedRegularFrame = GlobeOverlayFramePolicy.fromPolygon(
            center = center,
            polygon = polygon,
            rotationDegrees = 12f,
            minimumSize = 0f
        )
        val actual = GlobeOverlayFramePolicy.frameForOverlay(
            center = center,
            polygon = polygon,
            regularRotationDegrees = 12f,
            directionalRotationDegrees = 72f,
            overlay = regularOverlay,
            minimumSize = 0f
        )

        assertEquals(expectedRegularFrame.centerX, actual.centerX, 1e-4f)
        assertEquals(expectedRegularFrame.centerY, actual.centerY, 1e-4f)
        assertEquals(expectedRegularFrame.width, actual.width, 1e-4f)
        assertEquals(expectedRegularFrame.height, actual.height, 1e-4f)
    }
}
