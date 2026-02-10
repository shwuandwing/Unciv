package com.unciv.ui.render.globe

import com.badlogic.gdx.graphics.Color

object GlobeFogOfWarVisualPolicy {
    data class Visual(
        val tintColor: Color,
        val alphaScale: Float
    )

    /**
     * Mirrors 2D fog tinting for explored-but-not-currently-visible tiles:
     * color is blended 60% toward fog-of-war color.
     */
    fun resolve(
        isCurrentlyVisible: Boolean,
        fogOfWarColor: Color
    ): Visual {
        if (isCurrentlyVisible) return Visual(Color.WHITE.cpy(), 1f)
        return Visual(
            tintColor = Color.WHITE.cpy().lerp(fogOfWarColor.cpy(), 0.6f),
            alphaScale = 1f
        )
    }
}
