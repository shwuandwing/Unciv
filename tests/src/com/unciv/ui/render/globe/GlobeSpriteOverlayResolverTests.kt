package com.unciv.ui.render.globe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlobeSpriteOverlayResolverTests {
    @Test
    fun `resource icon location follows resource name`() {
        assertEquals("ResourceIcons/Oil", GlobeSpriteOverlayResolver.resourceIconLocation("Oil"))
        assertNull(GlobeSpriteOverlayResolver.resourceIconLocation(null))
    }

    @Test
    fun `improvement icon location follows improvement name`() {
        assertEquals("ImprovementIcons/Farm", GlobeSpriteOverlayResolver.improvementIconLocation("Farm"))
        assertNull(GlobeSpriteOverlayResolver.improvementIconLocation(null))
    }

    @Test
    fun `city icon location follows civilization name`() {
        assertEquals("NationIcons/Rome", GlobeSpriteOverlayResolver.cityIconLocation("Rome"))
        assertNull(GlobeSpriteOverlayResolver.cityIconLocation(null))
    }

    @Test
    fun `unit icon resolver prefers unit then base then type`() {
        val all = setOf(
            "UnitIcons/Swordsman",
            "UnitIcons/Warrior",
            "UnitTypeIcons/Melee"
        )
        val icon = GlobeSpriteOverlayResolver.unitIconLocation(
            unitName = "Swordsman",
            baseUnitName = "Warrior",
            unitTypeName = "Melee",
            imageExists = { it in all }
        )
        assertEquals("UnitIcons/Swordsman", icon)
    }

    @Test
    fun `unit icon resolver falls back to base or type`() {
        val baseOnly = setOf("UnitIcons/Warrior")
        val baseIcon = GlobeSpriteOverlayResolver.unitIconLocation(
            unitName = "Swordsman",
            baseUnitName = "Warrior",
            unitTypeName = "Melee",
            imageExists = { it in baseOnly }
        )
        assertEquals("UnitIcons/Warrior", baseIcon)

        val typeOnly = setOf("UnitTypeIcons/Melee")
        val typeIcon = GlobeSpriteOverlayResolver.unitIconLocation(
            unitName = "Swordsman",
            baseUnitName = "Warrior",
            unitTypeName = "Melee",
            imageExists = { it in typeOnly }
        )
        assertEquals("UnitTypeIcons/Melee", typeIcon)
    }

    @Test
    fun `unit icon resolver returns null when nothing exists`() {
        val icon = GlobeSpriteOverlayResolver.unitIconLocation(
            unitName = "Swordsman",
            baseUnitName = "Warrior",
            unitTypeName = "Melee",
            imageExists = { false }
        )
        assertNull(icon)
    }
}
