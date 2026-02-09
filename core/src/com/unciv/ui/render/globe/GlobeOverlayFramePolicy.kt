package com.unciv.ui.render.globe

import com.badlogic.gdx.math.Vector2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

object GlobeOverlayFramePolicy {
    data class Frame(
        val width: Float,
        val height: Float,
        val centerX: Float,
        val centerY: Float
    )

    fun frameForOverlay(
        center: Vector2,
        polygon: FloatArray,
        regularRotationDegrees: Float,
        directionalRotationDegrees: Float,
        overlay: GlobeCenterOverlayPolicy.Overlay,
        minimumSize: Float = 2f
    ): Frame {
        val rotationDegrees = if (overlay.isDirectional) directionalRotationDegrees else regularRotationDegrees
        return fromPolygon(
            center = center,
            polygon = polygon,
            rotationDegrees = rotationDegrees,
            minimumSize = minimumSize
        )
    }

    fun fromPolygon(
        center: Vector2,
        polygon: FloatArray,
        rotationDegrees: Float,
        minimumSize: Float = 2f
    ): Frame {
        require(polygon.size >= 6) { "Polygon must contain at least 3 vertices" }

        val angle = (-rotationDegrees * PI / 180.0).toFloat()
        val c = cos(angle)
        val s = sin(angle)

        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        var i = 0
        while (i < polygon.size / 2) {
            val dx = polygon[i * 2] - center.x
            val dy = polygon[i * 2 + 1] - center.y
            val localX = dx * c - dy * s
            val localY = dx * s + dy * c

            if (localX < minX) minX = localX
            if (localX > maxX) maxX = localX
            if (localY < minY) minY = localY
            if (localY > maxY) maxY = localY
            i++
        }

        val width = max(minimumSize, maxX - minX)
        val height = max(minimumSize, maxY - minY)
        val localCenterX = (minX + maxX) * 0.5f
        val localCenterY = (minY + maxY) * 0.5f
        val centerOffsetX = localCenterX * c + localCenterY * s
        val centerOffsetY = -localCenterX * s + localCenterY * c
        return Frame(
            width = width,
            height = height,
            centerX = center.x + centerOffsetX,
            centerY = center.y + centerOffsetY
        )
    }
}
