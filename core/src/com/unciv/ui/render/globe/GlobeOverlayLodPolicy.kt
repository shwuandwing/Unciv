package com.unciv.ui.render.globe

import kotlin.math.max
import kotlin.math.min

object GlobeOverlayLodPolicy {
    fun overlayAlpha(
        frameWidth: Float,
        frameHeight: Float,
        facingDotCamera: Float
    ): Float {
        val minSpan = min(frameWidth, frameHeight)
        val sizeFactor = smoothstep(5f, 12f, minSpan)
        val facingFactor = smoothstep(0.1f, 0.26f, facingDotCamera)
        return sizeFactor * facingFactor
    }

    fun baseTerrainAlpha(
        frameWidth: Float,
        frameHeight: Float,
        facingDotCamera: Float
    ): Float {
        val minSpan = min(frameWidth, frameHeight)
        val sizeFactor = smoothstep(7f, 16f, minSpan)
        val facingFactor = smoothstep(0.16f, 0.34f, facingDotCamera)
        return sizeFactor * facingFactor
    }

    fun gridLineAlphaScale(facingDotCamera: Float): Float =
        smoothstep(0.08f, 0.22f, facingDotCamera)

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
