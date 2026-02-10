package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.logic.civilization.Civilization
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
        val resolvedSegmentsByNeighbor = OwnershipBorderSegmentResolver.resolve(tile).associateBy { it.neighbor }
        for (neighbor in tile.neighbors) {
            var shouldRemoveBorderSegment = false
            var shouldAddBorderSegment = false

            val resolvedSegment = resolvedSegmentsByNeighbor[neighbor]
            if (resolvedSegment == null && borderSegments.containsKey(neighbor)) { // the neighbor used to not belong to us, but now it's ours
                shouldRemoveBorderSegment = true
            }
            else if (resolvedSegment != null) {
                if (!borderSegments.containsKey(neighbor)) { // there should be a border here but there isn't
                    shouldAddBorderSegment = true
                }
                else if (
                        resolvedSegment.isLeftConcave != borderSegments[neighbor]!!.isLeftConcave ||
                        resolvedSegment.isRightConcave != borderSegments[neighbor]!!.isRightConcave
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
                val segmentInfo = resolvedSegment ?: continue
                val images = mutableListOf<Image>()
                val borderSegment = BorderSegment(
                    images,
                    segmentInfo.isLeftConcave,
                    segmentInfo.isRightConcave
                )
                borderSegments[neighbor] = borderSegment

                val angle = BorderEdgeGeometry.borderAngleDegrees(segmentInfo.angleDirection)

                val innerBorderImage = ImageGetter.getImage(
                    strings.orFallback { getBorder(segmentInfo.borderShapeString,"Inner") }
                ).setHexagonSize()

                addActor(innerBorderImage)
                images.add(innerBorderImage)
                innerBorderImage.rotateBy(angle)
                innerBorderImage.color = civOuterColor

                val outerBorderImage = ImageGetter.getImage(
                    strings.orFallback { getBorder(segmentInfo.borderShapeString, "Outer") }
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
