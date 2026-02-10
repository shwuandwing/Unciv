package com.unciv.ui.render.globe

object GlobeBorderStripPlacementPolicy {
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
}
