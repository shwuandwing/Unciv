package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.tile.Tile

object OwnershipBorderSegmentResolver {
    data class Segment(
        val neighbor: Tile,
        val isLeftConcave: Boolean,
        val isRightConcave: Boolean,
        val borderShapeString: String,
        val angleDirection: Vector2
    )

    fun resolve(tile: Tile): List<Segment> {
        val tileOwner = tile.getOwner() ?: return emptyList()
        val segments = ArrayList<Segment>(6)

        for (neighbor in tile.neighbors) {
            if (neighbor.getOwner() == tileOwner) continue

            val (leftSharedNeighbor, rightSharedNeighbor) =
                if (tile.tileMap.mapParameters.shape == MapShape.icosahedron)
                    getSharedNeighborsByGeometry(tile, neighbor)
                else
                    getLeftSharedNeighbor(tile, neighbor) to getRightSharedNeighbor(tile, neighbor)

            // If a shared neighbor doesn't exist (because it's past a map edge),
            // we act as if it's owned by this tile owner for concavity purposes.
            val isLeftConcave = leftSharedNeighbor == null || leftSharedNeighbor.getOwner() == tileOwner
            val isRightConcave = rightSharedNeighbor == null || rightSharedNeighbor.getOwner() == tileOwner

            val angleDirection = if (tile.tileMap.mapParameters.shape == MapShape.icosahedron) {
                val tileWorldPosition = tile.tileMap.topology.getWorldPosition(tile)
                val neighborWorldPosition = tile.tileMap.topology.getWorldPosition(neighbor)
                BorderEdgeGeometry.mainMapIcosaAngleDirection(tileWorldPosition, neighborWorldPosition)
            } else {
                tile.tileMap.getNeighborTilePositionAsWorldCoords(tile, neighbor)
            }

            segments += Segment(
                neighbor = neighbor,
                isLeftConcave = isLeftConcave,
                isRightConcave = isRightConcave,
                borderShapeString = borderShapeString(isLeftConcave, isRightConcave),
                angleDirection = angleDirection
            )
        }

        return segments
    }

    fun borderShapeString(isLeftConcave: Boolean, isRightConcave: Boolean): String = when {
        isLeftConcave && isRightConcave -> "Concave"
        !isLeftConcave && !isRightConcave -> "Convex"
        !isLeftConcave && isRightConcave -> "ConvexConcave"
        else -> "ConcaveConvex"
    }

    /** Returns the left shared neighbor of `tile` and `neighbor` (relative to `tile` -> `neighbor`). */
    private fun getLeftSharedNeighbor(tile: Tile, neighbor: Tile): Tile? {
        return tile.tileMap.getClockPositionNeighborTile(
            tile,
            (tile.tileMap.getNeighborTileClockPosition(tile, neighbor) - 2) % 12
        )
    }

    /** Returns the right shared neighbor of `tile` and `neighbor` (relative to `tile` -> `neighbor`). */
    private fun getRightSharedNeighbor(tile: Tile, neighbor: Tile): Tile? {
        return tile.tileMap.getClockPositionNeighborTile(
            tile,
            (tile.tileMap.getNeighborTileClockPosition(tile, neighbor) + 2) % 12
        )
    }

    /** Returns left/right shared neighbors by geometry, robust for icosa seam/nonlocal adjacencies. */
    private fun getSharedNeighborsByGeometry(tile: Tile, neighbor: Tile): Pair<Tile?, Tile?> {
        val commonNeighbors = tile.neighbors.filter { candidate ->
            candidate != neighbor && neighbor.neighbors.any { it == candidate }
        }.toList()
        if (commonNeighbors.isEmpty()) return null to null
        if (commonNeighbors.size == 1) return commonNeighbors[0] to commonNeighbors[0]

        val origin = tile.tileMap.topology.getWorldPosition(tile)
        val target = tile.tileMap.topology.getWorldPosition(neighbor)
        val dirX = target.x - origin.x
        val dirY = target.y - origin.y

        var leftNeighbor: Tile? = null
        var rightNeighbor: Tile? = null
        var bestLeftCross = -Float.MAX_VALUE
        var bestRightCross = Float.MAX_VALUE
        for (candidate in commonNeighbors) {
            val candidatePos = tile.tileMap.topology.getWorldPosition(candidate)
            val vx = candidatePos.x - origin.x
            val vy = candidatePos.y - origin.y
            val cross = dirX * vy - dirY * vx
            if (cross > bestLeftCross) {
                bestLeftCross = cross
                leftNeighbor = candidate
            }
            if (cross < bestRightCross) {
                bestRightCross = cross
                rightNeighbor = candidate
            }
        }
        return (leftNeighbor ?: commonNeighbors.first()) to (rightNeighbor ?: commonNeighbors.last())
    }
}

