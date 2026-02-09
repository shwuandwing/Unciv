package com.unciv.ui.render.globe

import com.badlogic.gdx.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobeCityBannerStylePolicyTests {
    @Test
    fun `selected city border color matches 2d city table`() {
        val style = GlobeCityBannerStylePolicy.resolve(
            GlobeCityBannerStylePolicy.Context(
                sameAsSelectedCiv = true,
                atWarWithSelectedCiv = false,
                ownerOuterColor = Color(0.1f, 0.7f, 0.3f, 1f),
                ownerInnerColor = Color.WHITE
            )
        )
        assertEquals(Color.valueOf("#E9E9AC"), style.borderColor)
    }

    @Test
    fun `war city border color matches 2d city table`() {
        val style = GlobeCityBannerStylePolicy.resolve(
            GlobeCityBannerStylePolicy.Context(
                sameAsSelectedCiv = false,
                atWarWithSelectedCiv = true,
                ownerOuterColor = Color(0.1f, 0.7f, 0.3f, 1f),
                ownerInnerColor = Color.WHITE
            )
        )
        assertEquals(Color.valueOf("#E63200"), style.borderColor)
    }

    @Test
    fun `neutral city border uses charcoal`() {
        val style = GlobeCityBannerStylePolicy.resolve(
            GlobeCityBannerStylePolicy.Context(
                sameAsSelectedCiv = false,
                atWarWithSelectedCiv = false,
                ownerOuterColor = Color(0.1f, 0.7f, 0.3f, 1f),
                ownerInnerColor = Color.WHITE
            )
        )
        assertEquals(Color(0.12f, 0.12f, 0.14f, 1f), style.borderColor)
    }
}

