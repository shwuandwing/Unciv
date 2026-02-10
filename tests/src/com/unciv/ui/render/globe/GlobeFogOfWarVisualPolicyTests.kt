package com.unciv.ui.render.globe

import com.badlogic.gdx.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobeFogOfWarVisualPolicyTests {
    @Test
    fun `visible tiles keep neutral tint`() {
        val fog = Color(0.12f, 0.15f, 0.2f, 1f)
        val visual = GlobeFogOfWarVisualPolicy.resolve(
            isCurrentlyVisible = true,
            fogOfWarColor = fog
        )

        assertEquals(1f, visual.alphaScale, 0.0001f)
        assertEquals(1f, visual.tintColor.r, 0.0001f)
        assertEquals(1f, visual.tintColor.g, 0.0001f)
        assertEquals(1f, visual.tintColor.b, 0.0001f)
    }

    @Test
    fun `fogged tiles use same 60 percent fog blend as 2d`() {
        val fog = Color(0.2f, 0.3f, 0.4f, 1f)
        val visual = GlobeFogOfWarVisualPolicy.resolve(
            isCurrentlyVisible = false,
            fogOfWarColor = fog
        )

        assertEquals(1f, visual.alphaScale, 0.0001f)
        assertEquals(0.52f, visual.tintColor.r, 0.0001f)
        assertEquals(0.58f, visual.tintColor.g, 0.0001f)
        assertEquals(0.64f, visual.tintColor.b, 0.0001f)
    }
}
