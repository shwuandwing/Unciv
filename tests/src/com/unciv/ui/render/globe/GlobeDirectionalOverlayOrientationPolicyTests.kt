package com.unciv.ui.render.globe

import com.badlogic.gdx.math.Vector2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlobeDirectionalOverlayOrientationPolicyTests {

    @Test
    fun returnsNullWhenNoSamplesAvailable() {
        val rotation = GlobeDirectionalOverlayOrientationPolicy.rotationFromDirectionSamples(
            center = Vector2.Zero,
            samples = emptyList()
        )
        assertNull(rotation)
    }

    @Test
    fun singleSampleDefinesBaseRotation() {
        val rotation = GlobeDirectionalOverlayOrientationPolicy.rotationFromDirectionSamples(
            center = Vector2(0f, 0f),
            samples = listOf(
                GlobeDirectionalOverlayOrientationPolicy.DirectionSample(
                    projectedNeighborPoint = Vector2(0f, 2f),
                    expectedNeighborRotationDegrees = 0f
                )
            )
        )
        assertEquals(0f, rotation!!, 1e-4f)
    }

    @Test
    fun canonicalBottomRightSampleProducesZeroRotation() {
        val rotation = GlobeDirectionalOverlayOrientationPolicy.rotationFromDirectionSamples(
            center = Vector2(0f, 0f),
            samples = listOf(
                GlobeDirectionalOverlayOrientationPolicy.DirectionSample(
                    projectedNeighborPoint = Vector2(0.866f, -0.5f),
                    expectedNeighborRotationDegrees = -120f
                )
            )
        )
        assertEquals(0f, rotation!!, 1e-2f)
    }

    @Test
    fun multipleSamplesAverageToSharedOffset() {
        val rotation = GlobeDirectionalOverlayOrientationPolicy.rotationFromDirectionSamples(
            center = Vector2(0f, 0f),
            samples = listOf(
                GlobeDirectionalOverlayOrientationPolicy.DirectionSample(
                    projectedNeighborPoint = Vector2(0.5f, 0.866f),
                    expectedNeighborRotationDegrees = -60f
                ),
                GlobeDirectionalOverlayOrientationPolicy.DirectionSample(
                    projectedNeighborPoint = Vector2(1f, 0f),
                    expectedNeighborRotationDegrees = -120f
                )
            )
        )
        assertEquals(30f, rotation!!, 1e-1f)
    }

    @Test
    fun circularMeanHandlesAngleWraparound() {
        val rotation = GlobeDirectionalOverlayOrientationPolicy.rotationFromDirectionSamples(
            center = Vector2(0f, 0f),
            samples = listOf(
                GlobeDirectionalOverlayOrientationPolicy.DirectionSample(
                    projectedNeighborPoint = Vector2(-0.17364818f, -0.98480775f), // screenRotation ~= 170
                    expectedNeighborRotationDegrees = 0f
                ),
                GlobeDirectionalOverlayOrientationPolicy.DirectionSample(
                    projectedNeighborPoint = Vector2(-0.20791169f, -0.9781476f), // screenRotation ~= 168
                    expectedNeighborRotationDegrees = 0f
                )
            )
        )
        assertEquals(169f, rotation!!, 1f)
    }
}
