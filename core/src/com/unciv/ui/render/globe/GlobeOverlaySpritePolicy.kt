package com.unciv.ui.render.globe

object GlobeOverlaySpritePolicy {
    private const val icosaTextureRotationOffsetDegrees = 30f
    private const val defaultInsetTexels = 1.25f
    private const val directionalInsetTexels = 0f

    data class UvAxisWindow(
        val start: Float,
        val end: Float
    )

    fun overlayRotationDegrees(baseRotationDegrees: Float): Float =
        baseRotationDegrees + icosaTextureRotationOffsetDegrees

    fun textureInsetTexels(overlay: GlobeCenterOverlayPolicy.Overlay): Float {
        return if (overlay.isDirectional) directionalInsetTexels else defaultInsetTexels
    }

    fun horizontalUvWindow(rawStart: Float, rawEnd: Float, inset: Float): UvAxisWindow {
        val low = minOf(rawStart, rawEnd) + inset
        val high = maxOf(rawStart, rawEnd) - inset
        return if (rawEnd >= rawStart) UvAxisWindow(low, high) else UvAxisWindow(high, low)
    }

    /**
     * In libGDX, unflipped [TextureRegion] uses a larger V at the visual bottom.
     * For screen-space Y-up mapping we therefore reverse V when rawEnd >= rawStart.
     */
    fun verticalUvWindow(rawStart: Float, rawEnd: Float, inset: Float): UvAxisWindow {
        val low = minOf(rawStart, rawEnd) + inset
        val high = maxOf(rawStart, rawEnd) - inset
        return if (rawEnd >= rawStart) UvAxisWindow(high, low) else UvAxisWindow(low, high)
    }
}
