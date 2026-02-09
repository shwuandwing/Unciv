package com.unciv.ui.render.globe

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.mapgenerator.GoldbergMapBuilder
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class GlobeOverlayOrientationTests {

    private fun basicRuleset(): Ruleset {
        val ruleset = Ruleset()
        ruleset.terrains["Plains"] = Terrain().apply {
            name = "Plains"
            type = TerrainType.Land
        }
        return ruleset
    }

    private fun angularDifferenceRadians(a: Float, b: Float): Float {
        var diff = abs(a - b) % (2f * PI.toFloat())
        if (diff > PI.toFloat()) diff = 2f * PI.toFloat() - diff
        return diff
    }

    @Test
    fun localNorthOffsetDirectionStaysOnUnitSphere() {
        val normal = Vector3(0.42f, 0.63f, 0.65f).nor()
        val offset = GlobeOverlayOrientation.localNorthOffsetDirection(normal)

        assertTrue("offset direction should remain unit length", abs(offset.len() - 1f) < 1e-4f)
        assertTrue("offset should still point outward from sphere", offset.dot(normal) > 0.95f)
    }

    @Test
    fun localNorthTangentIsContinuousForSmallNormalChanges() {
        val n1 = Vector3(0.2f, 0.7f, 0.68f).nor()
        val n2 = Vector3(0.2f, 0.702f, 0.679f).nor()

        val t1 = GlobeOverlayOrientation.localNorthTangent(n1)
        val t2 = GlobeOverlayOrientation.localNorthTangent(n2)

        assertTrue("local-north tangent should change smoothly for nearby normals", t1.dot(t2) > 0.95f)
    }

    @Test
    fun screenRotationUsesLocalNorthAsUpDirection() {
        val center = Vector2(100f, 100f)
        val north = Vector2(100f, 150f)
        val east = Vector2(150f, 100f)

        val northRotation = GlobeOverlayOrientation.screenRotationDegrees(center, north)
        val eastRotation = GlobeOverlayOrientation.screenRotationDegrees(center, east)

        assertTrue("north-pointing up should require ~0 degrees rotation", abs(northRotation) < 1e-4f)
        assertTrue("east-pointing should rotate close to -90 degrees", abs(eastRotation + 90f) < 1e-4f)
    }

    @Test
    fun polygonTopVertexRotationMatchesPointyTopHex() {
        val center = Vector2(0f, 0f)
        // Pointy-top hex with top vertex at (0, 1)
        val polygon = floatArrayOf(
            0f, 1f,
            0.866f, 0.5f,
            0.866f, -0.5f,
            0f, -1f,
            -0.866f, -0.5f,
            -0.866f, 0.5f
        )

        val rotation = GlobeOverlayOrientation.screenRotationFromPolygonTopVertex(center, polygon)
        assertTrue("pointy-top polygon should map to ~0 degrees rotation", abs(rotation) < 1e-3f)
    }

    @Test
    fun polygonTopVertexRotationMatchesFlatTopHex() {
        val center = Vector2(0f, 0f)
        // Flat-top hex: topmost vertices are upper-left and upper-right.
        val polygon = floatArrayOf(
            1f, 0f,
            0.5f, 0.866f,
            -0.5f, 0.866f,
            -1f, 0f,
            -0.5f, -0.866f,
            0.5f, -0.866f
        )

        val rotation = GlobeOverlayOrientation.screenRotationFromPolygonTopVertex(center, polygon)
        // Deterministic tie-break picks upper-left vertex, which implies +30 degrees.
        assertTrue("flat-top polygon should map to ~30 degrees rotation", abs(rotation - 30f) < 1e-3f)
    }

    @Test
    fun polygonNearestRotationChoosesEdgeAlignedCandidateClosestToReference() {
        val center = Vector2(0f, 0f)
        val polygon = floatArrayOf(
            0f, 1f,
            0.866f, 0.5f,
            0.866f, -0.5f,
            0f, -1f,
            -0.866f, -0.5f,
            -0.866f, 0.5f
        )

        val nearTwenty = GlobeOverlayOrientation.screenRotationFromPolygonNearestTo(
            center = center,
            polygon = polygon,
            referenceRotationDegrees = 20f
        )
        val nearFifty = GlobeOverlayOrientation.screenRotationFromPolygonNearestTo(
            center = center,
            polygon = polygon,
            referenceRotationDegrees = 50f
        )

        assertTrue("reference near 20 should snap to 0-degree vertex-aligned rotation", abs(nearTwenty - 0f) < 1e-3f)
        assertTrue("reference near 50 should snap to 60-degree vertex-aligned rotation", abs(nearFifty - 60f) < 1e-3f)
    }

    @Test
    fun localOverlayRotationVariesAcrossIcosaTiles() {
        val map = GoldbergMapBuilder.build(
            MapParameters().apply {
                shape = MapShape.icosahedron
                mapSize = MapSize.Tiny
            },
            basicRuleset()
        )
        val cache = IcosaMeshRuntimeCache.from(map)

        val expectedAngles = cache.centers.map { normal ->
            val tangent = GlobeOverlayOrientation.localNorthTangent(normal)
            kotlin.math.atan2(tangent.y.toDouble(), tangent.x.toDouble()).toFloat()
        }

        var maxExpectedDelta = 0f
        for (i in expectedAngles.indices) {
            for (j in i + 1 until expectedAngles.size) {
                val delta = angularDifferenceRadians(expectedAngles[i], expectedAngles[j])
                if (delta > maxExpectedDelta) maxExpectedDelta = delta
            }
        }

        assertTrue(
            "Expected overlay orientation should vary across tiles; got max delta=$maxExpectedDelta",
            maxExpectedDelta > 0.45f
        )
    }
}
