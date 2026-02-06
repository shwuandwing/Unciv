package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.math.Vector2
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object BorderEdgeGeometry {
    fun borderAngleDegrees(direction: Vector2): Float {
        return (atan2(direction.y.toDouble(), direction.x.toDouble()) * 180 / PI - 90.0).toFloat()
    }

    fun borderNormalForImageRotation(imageRotationDegrees: Float): Vector2 {
        val radians = Math.toRadians((imageRotationDegrees + 90f).toDouble())
        return Vector2(cos(radians).toFloat(), sin(radians).toFloat())
    }

    fun getOffsetTowardsNeighbor(direction: Vector2, distance: Float): Vector2 {
        if (direction.isZero) return Vector2.Zero
        return direction.cpy().nor().scl(distance)
    }

    /**
     * Main-map border assets follow the legacy clock-vector orientation convention.
     * For icosahedron we therefore use tile<-neighbor as angle direction.
     */
    fun mainMapIcosaAngleDirection(tileWorldPosition: Vector2, neighborWorldPosition: Vector2): Vector2 {
        return Vector2(
            tileWorldPosition.x - neighborWorldPosition.x,
            tileWorldPosition.y - neighborWorldPosition.y
        )
    }

    fun isSegmentFacingNeighbor(
        angleDirection: Vector2,
        neighborDirectionInRenderSpace: Vector2,
        baseImageRotationDegrees: Float
    ): Boolean {
        if (angleDirection.isZero || neighborDirectionInRenderSpace.isZero) return false
        val imageRotation = baseImageRotationDegrees + borderAngleDegrees(angleDirection)
        val normal = borderNormalForImageRotation(imageRotation)
        val neighborDirection = neighborDirectionInRenderSpace.cpy().nor()
        return normal.dot(neighborDirection) > 0f
    }
}
