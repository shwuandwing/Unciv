package com.unciv.ui.render.globe

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object GlobeBorderStripPlacementPolicy {
    data class Edge(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val midX: Float,
        val midY: Float,
        val length: Float
    )

    data class Placement(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val originX: Float,
        val originY: Float,
        val rotation: Float
    )

    /**
     * Mirrors 2D border sprite placement semantics:
     * - Border strip is scaled to hex width.
     * - Strip y-anchor starts at bottom of full hex frame.
     * - Rotation origin uses full-hex center, not strip center.
     */
    fun resolve(
        frame: GlobeOverlayFramePolicy.Frame,
        regionWidth: Int,
        regionHeight: Int,
        rotationDegrees: Float
    ): Placement {
        val width = frame.width
        val height = width * (regionHeight.toFloat() / regionWidth.toFloat())
        val x = frame.centerX - width / 2f
        val y = frame.centerY - frame.height / 2f
        val originX = width / 2f
        val originY = frame.height / 2f
        return Placement(
            x = x,
            y = y,
            width = width,
            height = height,
            originX = originX,
            originY = originY,
            rotation = rotationDegrees
        )
    }

    fun resolveEdgeFacingNeighbor(
        polygon: FloatArray,
        centerX: Float,
        centerY: Float,
        neighborX: Float,
        neighborY: Float
    ): Edge? {
        val vertexCount = polygon.size / 2
        if (vertexCount < 3) return null

        val neighborDx = neighborX - centerX
        val neighborDy = neighborY - centerY
        val neighborLen = hypot(neighborDx, neighborDy)
        if (neighborLen <= 1e-6f) return null

        var bestEdge: Edge? = null
        var bestScore = -Float.MAX_VALUE

        for (i in 0 until vertexCount) {
            val next = (i + 1) % vertexCount
            val startX = polygon[i * 2]
            val startY = polygon[i * 2 + 1]
            val endX = polygon[next * 2]
            val endY = polygon[next * 2 + 1]
            val midX = (startX + endX) * 0.5f
            val midY = (startY + endY) * 0.5f

            val edgeDx = endX - startX
            val edgeDy = endY - startY
            val edgeLen = hypot(edgeDx, edgeDy)
            if (edgeLen <= 1e-6f) continue

            val radialDx = midX - centerX
            val radialDy = midY - centerY
            val radialLen = hypot(radialDx, radialDy)
            if (radialLen <= 1e-6f) continue

            val radialScore = (radialDx * neighborDx + radialDy * neighborDy) / (radialLen * neighborLen)
            val tangentOrtho = abs((edgeDx * neighborDx + edgeDy * neighborDy) / (edgeLen * neighborLen))
            val score = radialScore - tangentOrtho * 0.08f

            if (score > bestScore) {
                bestScore = score
                bestEdge = Edge(
                    startX = startX,
                    startY = startY,
                    endX = endX,
                    endY = endY,
                    midX = midX,
                    midY = midY,
                    length = edgeLen
                )
            }
        }

        return bestEdge
    }

    /**
     * Edge-local strip placement:
     * - Chooses the projected edge that faces the neighbor.
     * - Keeps strip art and tinting identical to 2D.
     * - Anchors strip at the edge midpoint so placement stays stable near the globe limb.
     */
    fun resolve(
        edge: Edge,
        tileCenterX: Float,
        tileCenterY: Float,
        regionWidth: Int,
        regionHeight: Int,
        preferredRotationDegrees: Float
    ): Placement {
        val width = max(1f, edge.length * sqrt(3f))
        val height = width * (regionHeight.toFloat() / regionWidth.toFloat())
        val rotation = resolveEdgeAlignedRotation(
            edge = edge,
            tileCenterX = tileCenterX,
            tileCenterY = tileCenterY,
            preferredRotationDegrees = preferredRotationDegrees
        )
        return Placement(
            x = edge.midX - width / 2f,
            y = edge.midY,
            width = width,
            height = height,
            originX = width / 2f,
            originY = 0f,
            rotation = rotation
        )
    }

    private fun resolveEdgeAlignedRotation(
        edge: Edge,
        tileCenterX: Float,
        tileCenterY: Float,
        preferredRotationDegrees: Float
    ): Float {
        val edgeAngle = (atan2((edge.endY - edge.startY).toDouble(), (edge.endX - edge.startX).toDouble()) * 180.0 / PI).toFloat()
        val candidateA = normalizeDegrees(edgeAngle)
        val candidateB = normalizeDegrees(edgeAngle + 180f)
        val centerDx = tileCenterX - edge.midX
        val centerDy = tileCenterY - edge.midY

        val scoreA = candidateScore(candidateA, preferredRotationDegrees, centerDx, centerDy)
        val scoreB = candidateScore(candidateB, preferredRotationDegrees, centerDx, centerDy)
        return if (scoreA <= scoreB) candidateA else candidateB
    }

    private fun candidateScore(
        candidateRotation: Float,
        preferredRotationDegrees: Float,
        centerDx: Float,
        centerDy: Float
    ): Float {
        val radians = Math.toRadians(candidateRotation.toDouble())
        val inwardNormalX = (-sin(radians)).toFloat()
        val inwardNormalY = cos(radians).toFloat()
        val inwardPenalty = if (inwardNormalX * centerDx + inwardNormalY * centerDy >= 0f) 0f else 1000f
        return angleDeltaDegrees(candidateRotation, preferredRotationDegrees) + inwardPenalty
    }

    private fun normalizeDegrees(rotation: Float): Float {
        var normalized = rotation % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }

    private fun angleDeltaDegrees(a: Float, b: Float): Float {
        val delta = ((a - b + 540f) % 360f) - 180f
        return abs(delta)
    }
}
