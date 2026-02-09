package com.unciv.ui.render.globe

import com.badlogic.gdx.graphics.Color
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization

object GlobeCityBannerStylePolicy {
    data class Context(
        val sameAsSelectedCiv: Boolean,
        val atWarWithSelectedCiv: Boolean,
        val ownerOuterColor: Color,
        val ownerInnerColor: Color
    )

    data class Style(
        val borderColor: Color,
        val backgroundColor: Color,
        val textColor: Color
    )

    private val selectedCityBorderColor = Color.valueOf("#E9E9AC")
    private val atWarBorderColor = Color.valueOf("#E63200")
    private val neutralBorderColor = Color(0.12f, 0.12f, 0.14f, 1f)

    fun resolve(city: City, selectedCiv: Civilization?): Style {
        val owner = city.civ
        return resolve(
            Context(
                sameAsSelectedCiv = selectedCiv != null && owner == selectedCiv,
                atWarWithSelectedCiv = selectedCiv != null && owner.isAtWarWith(selectedCiv),
                ownerOuterColor = owner.nation.getOuterColor().cpy(),
                ownerInnerColor = owner.nation.getInnerColor().cpy()
            )
        )
    }

    fun resolve(context: Context): Style {
        val borderColor = when {
            context.sameAsSelectedCiv -> selectedCityBorderColor.cpy()
            context.atWarWithSelectedCiv -> atWarBorderColor.cpy()
            else -> neutralBorderColor.cpy()
        }
        val backgroundColor = context.ownerOuterColor.cpy().apply { a = 0.9f }
        val textColor = context.ownerInnerColor.cpy()
        return Style(borderColor = borderColor, backgroundColor = backgroundColor, textColor = textColor)
    }
}
