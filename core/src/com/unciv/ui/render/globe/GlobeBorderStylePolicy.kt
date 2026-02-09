package com.unciv.ui.render.globe

import com.badlogic.gdx.graphics.Color

object GlobeBorderStylePolicy {
    data class BorderPassColors(
        val outerPass: Color,
        val innerPass: Color
    )

    /**
     * Mirror 2D TileLayerBorders coloring:
     * - Outer border sprite is tinted with civ inner color.
     * - Inner border sprite is tinted with civ outer color.
     */
    fun resolvePassColors(civOuterColor: Color, civInnerColor: Color): BorderPassColors =
        BorderPassColors(
            outerPass = civInnerColor.cpy(),
            innerPass = civOuterColor.cpy()
        )
}

