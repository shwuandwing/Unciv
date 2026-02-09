package com.unciv.ui.render.globe

import com.badlogic.gdx.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobeBorderStylePolicyTests {
    @Test
    fun `border pass colors mirror 2d ordering`() {
        val civOuter = Color(0.1f, 0.7f, 0.3f, 1f)
        val civInner = Color(1f, 1f, 1f, 1f)
        val style = GlobeBorderStylePolicy.resolvePassColors(civOuterColor = civOuter, civInnerColor = civInner)

        assertEquals(civInner, style.outerPass)
        assertEquals(civOuter, style.innerPass)
    }
}

