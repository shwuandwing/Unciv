package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.images.ImageGetter

class TileLayerBorders(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    data class BorderSegment(
        var images: List<Image>,
        var isLeftConcave: Boolean = false,
        var isRightConcave: Boolean = false,
    )

    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)

    private var previousTileOwner: Civilization? = null
    private val borderSegments = HashMap<Tile, BorderSegment>()

    fun reset() {
        if (borderSegments.isNotEmpty()) {
            for (borderSegment in borderSegments.values)
                for (image in borderSegment.images)
                    image.remove()
            borderSegments.clear()
        }
    }


    /** Returns the left shared neighbor of `this` and [neighbor] (relative to the view direction `this`->[neighbor]), or null if there is no such tile. */
    private fun Tile.getLeftSharedNeighbor(neighbor: Tile): Tile? {
        return tileMap.getClockPositionNeighborTile(this,(tileMap.getNeighborTileClockPosition(this, neighbor) - 2) % 12)
    }

    /** Returns the right shared neighbor of `this` and [neighbor] (relative to the view direction `this`->[neighbor]), or null if there is no such tile. */
    private fun Tile.getRightSharedNeighbor(neighbor: Tile): Tile? {
        return tileMap.getClockPositionNeighborTile(this,(tileMap.getNeighborTileClockPosition(this, neighbor) + 2) % 12)
    }

    /** Returns left/right shared neighbors by geometry, robust for icosa seam/nonlocal adjacencies. */
    private fun Tile.getSharedNeighborsByGeometry(neighbor: Tile): Pair<Tile?, Tile?> {
        val commonNeighbors = neighbors.filter { candidate ->
            candidate != neighbor && neighbor.neighbors.any { it == candidate }
        }.toList()
        if (commonNeighbors.isEmpty()) return null to null
        if (commonNeighbors.size == 1) return commonNeighbors[0] to commonNeighbors[0]

        val origin = tileMap.topology.getWorldPosition(this)
        val target = tileMap.topology.getWorldPosition(neighbor)
        val dirX = target.x - origin.x
        val dirY = target.y - origin.y

        var leftNeighbor: Tile? = null
        var rightNeighbor: Tile? = null
        var bestLeftCross = -Float.MAX_VALUE
        var bestRightCross = Float.MAX_VALUE
        for (candidate in commonNeighbors) {
            val candidatePos = tileMap.topology.getWorldPosition(candidate)
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

    private fun updateBorders() {

        // This is longer than it could be, because of performance -
        // before fixing, about half (!) the time of update() was wasted on
        // removing all the border images and putting them back again!

        val tile = tileGroup.tile
        val tileOwner = tile.getOwner()

        // If owner changed - clear previous borders
        if (previousTileOwner != tileOwner)
            reset()

        previousTileOwner = tileOwner

        // No owner - no borders
        if (tileOwner == null)
            return

        // Setup new borders
        val civOuterColor = tile.getOwner()!!.nation.getOuterColor()
        val civInnerColor = tile.getOwner()!!.nation.getInnerColor()
        for (neighbor in tile.neighbors) {
            var shouldRemoveBorderSegment = false
            var shouldAddBorderSegment = false

            var borderSegmentShouldBeLeftConcave = false
            var borderSegmentShouldBeRightConcave = false

            val neighborOwner = neighbor.getOwner()
            if (neighborOwner == tileOwner && borderSegments.containsKey(neighbor)) { // the neighbor used to not belong to us, but now it's ours
                shouldRemoveBorderSegment = true
            }
            else if (neighborOwner != tileOwner) {
                val (leftSharedNeighbor, rightSharedNeighbor) =
                    if (tile.tileMap.mapParameters.shape == MapShape.icosahedron)
                        tile.getSharedNeighborsByGeometry(neighbor)
                    else
                        tile.getLeftSharedNeighbor(neighbor) to tile.getRightSharedNeighbor(neighbor)

                // If a shared neighbor doesn't exist (because it's past a map edge), we act as if it's our tile for border concave/convex-ity purposes.
                // This is because we do not draw borders against non-existing tiles either.
                borderSegmentShouldBeLeftConcave = leftSharedNeighbor == null || leftSharedNeighbor.getOwner() == tileOwner
                borderSegmentShouldBeRightConcave = rightSharedNeighbor == null || rightSharedNeighbor.getOwner() == tileOwner

                if (!borderSegments.containsKey(neighbor)) { // there should be a border here but there isn't
                    shouldAddBorderSegment = true
                }
                else if (
                        borderSegmentShouldBeLeftConcave != borderSegments[neighbor]!!.isLeftConcave ||
                        borderSegmentShouldBeRightConcave != borderSegments[neighbor]!!.isRightConcave
                ) { // the concave/convex-ity of the border here is wrong
                    shouldRemoveBorderSegment = true
                    shouldAddBorderSegment = true
                }
            }

            if (shouldRemoveBorderSegment) {
                for (image in borderSegments[neighbor]!!.images)
                    image.remove()
                borderSegments.remove(neighbor)
            }
            if (shouldAddBorderSegment) {
                val images = mutableListOf<Image>()
                val borderSegment = BorderSegment(
                    images,
                    borderSegmentShouldBeLeftConcave,
                    borderSegmentShouldBeRightConcave
                )
                borderSegments[neighbor] = borderSegment

                val borderShapeString = when {
                    borderSegment.isLeftConcave && borderSegment.isRightConcave -> "Concave"
                    !borderSegment.isLeftConcave && !borderSegment.isRightConcave -> "Convex"
                    !borderSegment.isLeftConcave && borderSegment.isRightConcave -> "ConvexConcave"
                    borderSegment.isLeftConcave && !borderSegment.isRightConcave -> "ConcaveConvex"
                    else -> error("This shouldn't happen?")
                }

                val relativeWorldPosition = if (tile.tileMap.mapParameters.shape == MapShape.icosahedron) {
                    // Border images are already rotated with the icosahedron tile sprite.
                    // Use topology world-space direction here to avoid applying the 30deg render rotation twice.
                    val fromPos = tile.tileMap.topology.getWorldPosition(tile)
                    val toPos = tile.tileMap.topology.getWorldPosition(neighbor)
                    Vector2(toPos.x - fromPos.x, toPos.y - fromPos.y)
                } else {
                    tile.tileMap.getNeighborTilePositionAsWorldCoords(tile, neighbor)
                }
                val angle = BorderEdgeGeometry.borderAngleDegrees(relativeWorldPosition)

                val innerBorderImage = ImageGetter.getImage(
                    strings.orFallback { getBorder(borderShapeString,"Inner") }
                ).setHexagonSize()

                addActor(innerBorderImage)
                images.add(innerBorderImage)
                innerBorderImage.rotateBy(angle)
                innerBorderImage.color = civOuterColor

                val outerBorderImage = ImageGetter.getImage(
                    strings.orFallback { getBorder(borderShapeString, "Outer") }
                ).setHexagonSize()

                addActor(outerBorderImage)
                images.add(outerBorderImage)
                outerBorderImage.rotateBy(angle)
                outerBorderImage.color = civInnerColor
            }
        }

    }

    override fun doUpdate(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {
        updateBorders()
    }

}
