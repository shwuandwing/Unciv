package com.unciv.ui.render.globe

import com.badlogic.gdx.math.Vector2
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object GlobeDirectionalOverlayOrientationPolicy {
    data class DirectionSample(
        val projectedNeighborPoint: Vector2,
        val expectedNeighborRotationDegrees: Float
    )

    fun rotationFromDirectionSamples(
        center: Vector2,
        samples: Iterable<DirectionSample>
    ): Float? {
        var sampleCount = 0
        var sumX = 0.0
        var sumY = 0.0

        for (sample in samples) {
            val projectedRotation = GlobeOverlayOrientation.screenRotationDegrees(center, sample.projectedNeighborPoint)
            val baseRotation = projectedRotation - sample.expectedNeighborRotationDegrees
            val radians = Math.toRadians(baseRotation.toDouble())
            sumX += cos(radians)
            sumY += sin(radians)
            sampleCount++
        }
        if (sampleCount == 0) return null

        return Math.toDegrees(atan2(sumY, sumX)).toFloat()
    }
}
