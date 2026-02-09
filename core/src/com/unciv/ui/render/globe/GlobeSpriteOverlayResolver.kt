package com.unciv.ui.render.globe

object GlobeSpriteOverlayResolver {
    fun resourceIconLocation(resourceName: String?): String? =
        resourceName?.let { "ResourceIcons/$it" }

    fun improvementIconLocation(improvementName: String?): String? =
        improvementName?.let { "ImprovementIcons/$it" }

    fun cityIconLocation(civName: String?): String? =
        civName?.let { "NationIcons/$it" }

    fun unitIconLocation(
        unitName: String,
        baseUnitName: String,
        unitTypeName: String,
        imageExists: (String) -> Boolean
    ): String? {
        val candidates = listOf(
            "UnitIcons/$unitName",
            "UnitIcons/$baseUnitName",
            "UnitTypeIcons/$unitTypeName"
        )
        return candidates.firstOrNull(imageExists)
    }
}
