package com.unciv.ui.render.globe

import com.badlogic.gdx.math.Vector2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object GlobeOverlayPolygonMapping {

    fun triangleFan(vertexCount: Int): ShortArray {
        require(vertexCount >= 3) { "Need at least 3 vertices to triangulate polygon" }
        val triangles = ShortArray((vertexCount - 2) * 3)
        var t = 0
        for (i in 1 until vertexCount - 1) {
            triangles[t++] = 0
            triangles[t++] = i.toShort()
            triangles[t++] = (i + 1).toShort()
        }
        return triangles
    }

    fun uvForPoint(
        pointX: Float,
        pointY: Float,
        frameCenterX: Float,
        frameCenterY: Float,
        frameWidth: Float,
        frameHeight: Float,
        rotationDegrees: Float
    ): Vector2 {
        val angle = (-rotationDegrees * PI / 180.0).toFloat()
        val c = cos(angle)
        val s = sin(angle)
        val dx = pointX - frameCenterX
        val dy = pointY - frameCenterY
        val localX = dx * c - dy * s
        val localY = dx * s + dy * c
        val u = (localX / frameWidth + 0.5f).coerceIn(0f, 1f)
        val v = (localY / frameHeight + 0.5f).coerceIn(0f, 1f)
        return Vector2(u, v)
    }
}

