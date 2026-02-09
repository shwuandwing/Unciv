package com.unciv.ui.render.globe

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.atan2

object GlobeOverlayOrientation {

    fun localNorthTangent(normal: Vector3): Vector3 {
        val unitNormal = normal.cpy().nor()
        val projectedFromY = projectedTangent(Vector3.Y, unitNormal)
        if (projectedFromY.len2() > 1e-6f) return projectedFromY.nor()

        val projectedFromZ = projectedTangent(Vector3.Z, unitNormal)
        if (projectedFromZ.len2() > 1e-6f) return projectedFromZ.nor()

        return projectedTangent(Vector3.X, unitNormal).nor()
    }

    fun localNorthOffsetDirection(normal: Vector3, step: Float = 0.08f): Vector3 {
        val unitNormal = normal.cpy().nor()
        val tangent = localNorthTangent(unitNormal)
        return unitNormal.add(tangent.scl(step)).nor()
    }

    fun screenRotationDegrees(center: Vector2, localNorthPoint: Vector2): Float {
        val dx = localNorthPoint.x - center.x
        val dy = localNorthPoint.y - center.y
        // Overlay sprites are authored with "up" along +Y, so rotate by angle-90.
        return (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / Math.PI).toFloat() - 90f
    }

    fun screenRotationFromPolygonTopVertex(center: Vector2, polygon: FloatArray): Float {
        require(polygon.size >= 6) { "Polygon must contain at least 3 vertices" }

        var bestX = polygon[0]
        var bestY = polygon[1]
        var i = 1
        while (i < polygon.size / 2) {
            val x = polygon[i * 2]
            val y = polygon[i * 2 + 1]
            if (y > bestY + 1e-4f || (abs(y - bestY) <= 1e-4f && x < bestX)) {
                bestX = x
                bestY = y
            }
            i++
        }

        return screenRotationDegrees(center, Vector2(bestX, bestY))
    }

    fun screenRotationFromPolygonNearestTo(
        center: Vector2,
        polygon: FloatArray,
        referenceRotationDegrees: Float
    ): Float {
        require(polygon.size >= 6) { "Polygon must contain at least 3 vertices" }
        var bestRotation = screenRotationFromPolygonTopVertex(center, polygon)
        var bestDelta = angularDistanceDegrees(bestRotation, referenceRotationDegrees)

        var i = 0
        while (i < polygon.size / 2) {
            val candidate = screenRotationDegrees(
                center,
                Vector2(polygon[i * 2], polygon[i * 2 + 1])
            )
            val delta = angularDistanceDegrees(candidate, referenceRotationDegrees)
            if (delta < bestDelta) {
                bestDelta = delta
                bestRotation = candidate
            }
            i++
        }
        return bestRotation
    }

    fun angularDistanceDegrees(a: Float, b: Float): Float {
        var diff = (a - b + 180f) % 360f
        if (diff < 0f) diff += 360f
        diff -= 180f
        return abs(diff)
    }

    private fun projectedTangent(vector: Vector3, normal: Vector3): Vector3 {
        return vector.cpy().sub(normal.cpy().scl(vector.dot(normal)))
    }
}
