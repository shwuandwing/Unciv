package com.unciv.ui.screens.worldscreen.worldmap

import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile

/**
 * Resolves repeated-click selection behavior for 3D globe tiles where 2D stack overlays are hidden.
 * This mirrors the set of unit-pick overlays that [WorldMapHolder.addTileOverlays] provides in 2D:
 * - city center tiles owned by viewing civ (or spectator)
 * - tiles containing selectable air stacks
 */
object GlobeStackSelectionPolicy {
    sealed class Selection {
        data class SelectUnit(val unit: MapUnit) : Selection()
        data class SelectCity(val city: City) : Selection()
    }

    fun resolveForRepeatedTileClick(
        tile: Tile,
        viewingCiv: Civilization,
        selectedUnit: MapUnit?,
        selectedCity: City?,
        tileAlreadySelected: Boolean
    ): Selection? {
        if (!tileAlreadySelected) return null

        val pickableUnits = getPickableUnits(tile, viewingCiv)
        if (pickableUnits.isEmpty()) return null

        if (selectedCity != null && tile.isCityCenter() && selectedCity == tile.getCity()) {
            return Selection.SelectUnit(pickableUnits.first())
        }

        if (selectedUnit != null && selectedUnit.getTile() == tile) {
            val currentIndex = pickableUnits.indexOf(selectedUnit)
            if (currentIndex in 0 until pickableUnits.lastIndex) {
                return Selection.SelectUnit(pickableUnits[currentIndex + 1])
            }
            if (tile.isCityCenter() && isOwnedCityTile(tile, viewingCiv)) {
                val city = tile.getCity() ?: return null
                return Selection.SelectCity(city)
            }
            return Selection.SelectUnit(pickableUnits.first())
        }

        if (selectedUnit == null && selectedCity == null) {
            return Selection.SelectUnit(pickableUnits.first())
        }

        return null
    }

    private fun getPickableUnits(tile: Tile, viewingCiv: Civilization): List<MapUnit> {
        if (isOwnedCityTile(tile, viewingCiv)) return tile.getUnits().toList()
        if (hasSelectableAirStack(tile, viewingCiv)) return tile.getUnits().toList()
        return emptyList()
    }

    private fun hasSelectableAirStack(tile: Tile, viewingCiv: Civilization): Boolean {
        if (tile.airUnits.isEmpty()) return false
        if (viewingCiv.isSpectator()) return true
        return tile.airUnits.first().civ == viewingCiv
    }

    private fun isOwnedCityTile(tile: Tile, viewingCiv: Civilization): Boolean {
        if (!tile.isCityCenter()) return false
        if (viewingCiv.isSpectator()) return true
        return tile.getOwner() == viewingCiv
    }
}
