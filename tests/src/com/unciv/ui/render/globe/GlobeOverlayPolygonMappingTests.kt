package com.unciv.ui.render.globe

import com.badlogic.gdx.math.Vector2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobeOverlayPolygonMappingTests {

    @Test
    fun triangleFanForHexBuildsExpectedIndexCount() {
        val triangles = GlobeOverlayPolygonMapping.triangleFan(6)
        assertEquals(12, triangles.size)
        assertEquals(0, triangles[0].toInt())
        assertEquals(1, triangles[1].toInt())
        assertEquals(2, triangles[2].toInt())
        assertEquals(0, triangles[9].toInt())
        assertEquals(4, triangles[10].toInt())
        assertEquals(5, triangles[11].toInt())
    }

    @Test
    fun uvMappingMatchesUnrotatedFrameAxes() {
        val center = Vector2(10f, 10f)
        val width = 4f
        val height = 6f

        val uvCenter = GlobeOverlayPolygonMapping.uvForPoint(10f, 10f, center.x, center.y, width, height, 0f)
        val uvTop = GlobeOverlayPolygonMapping.uvForPoint(10f, 13f, center.x, center.y, width, height, 0f)
        val uvRight = GlobeOverlayPolygonMapping.uvForPoint(12f, 10f, center.x, center.y, width, height, 0f)

        assertEquals(0.5f, uvCenter.x, 1e-4f)
        assertEquals(0.5f, uvCenter.y, 1e-4f)
        assertEquals(1f, uvTop.y, 1e-4f)
        assertEquals(1f, uvRight.x, 1e-4f)
    }

    @Test
    fun uvMappingKeepsDistortedPolygonVerticesInsideFrame() {
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
            rotationDegrees = 18f
        )

        for (i in 0 until polygon.size / 2) {
            val uv = GlobeOverlayPolygonMapping.uvForPoint(
                pointX = polygon[i * 2],
                pointY = polygon[i * 2 + 1],
                frameCenterX = frame.centerX,
                frameCenterY = frame.centerY,
                frameWidth = frame.width,
                frameHeight = frame.height,
                rotationDegrees = 18f
            )
            assertTrue("u must stay within [0,1]", uv.x in 0f..1f)
            assertTrue("v must stay within [0,1]", uv.y in 0f..1f)
        }
    }
}

