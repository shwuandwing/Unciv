package com.unciv.ui.screens.worldscreen.worldmap

import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class GlobeStackSelectionPolicyTests {
    private lateinit var testGame: TestGame
    private lateinit var civ: Civilization
    private lateinit var city: City
    private lateinit var cityTile: Tile
    private lateinit var military: MapUnit
    private lateinit var civilian: MapUnit
    private lateinit var air: MapUnit

    @Before
    fun setUp() {
        testGame = TestGame()
        testGame.makeHexagonalMap(newRadius = 2, baseTerrain = Constants.grassland)
        civ = testGame.addCiv(isPlayer = true)
        cityTile = testGame.getTile(HexCoord.Zero)
        city = testGame.addCity(civ, cityTile)
        military = testGame.addUnit("Warrior", civ, cityTile)
        civilian = testGame.addUnit("Settler", civ, cityTile)
        val airUnitName = testGame.ruleset.units.values.first { it.movesLikeAirUnits }.name
        air = testGame.addUnit(airUnitName, civ, cityTile)
    }

    @Test
    fun repeated_click_on_selected_city_cycles_to_first_city_unit() {
        val selection = GlobeStackSelectionPolicy.resolveForRepeatedTileClick(
            tile = cityTile,
            viewingCiv = civ,
            selectedUnit = null,
            selectedCity = city,
            tileAlreadySelected = true
        )
        assertSelectUnit(selection, military)
    }

    @Test
    fun repeated_click_cycles_city_stack_units_then_returns_to_city() {
        val fromMilitary = GlobeStackSelectionPolicy.resolveForRepeatedTileClick(
            tile = cityTile,
            viewingCiv = civ,
            selectedUnit = military,
            selectedCity = null,
            tileAlreadySelected = true
        )
        assertSelectUnit(fromMilitary, civilian)

        val fromCivilian = GlobeStackSelectionPolicy.resolveForRepeatedTileClick(
            tile = cityTile,
            viewingCiv = civ,
            selectedUnit = civilian,
            selectedCity = null,
            tileAlreadySelected = true
        )
        assertSelectUnit(fromCivilian, air)

        val fromAir = GlobeStackSelectionPolicy.resolveForRepeatedTileClick(
            tile = cityTile,
            viewingCiv = civ,
            selectedUnit = air,
            selectedCity = null,
            tileAlreadySelected = true
        )
        assertSelectCity(fromAir, city)
    }

    @Test
    fun repeated_click_with_no_selection_still_picks_first_stack_unit() {
        val selection = GlobeStackSelectionPolicy.resolveForRepeatedTileClick(
            tile = cityTile,
            viewingCiv = civ,
            selectedUnit = null,
            selectedCity = null,
            tileAlreadySelected = true
        )
        assertSelectUnit(selection, military)
    }

    @Test
    fun first_click_does_not_override_default_unit_table_selection_flow() {
        val selection = GlobeStackSelectionPolicy.resolveForRepeatedTileClick(
            tile = cityTile,
            viewingCiv = civ,
            selectedUnit = null,
            selectedCity = city,
            tileAlreadySelected = false
        )
        assertNull(selection)
    }

    private fun assertSelectUnit(selection: GlobeStackSelectionPolicy.Selection?, expected: MapUnit) {
        val picked = selection as GlobeStackSelectionPolicy.Selection.SelectUnit
        assertSame(expected, picked.unit)
    }

    private fun assertSelectCity(selection: GlobeStackSelectionPolicy.Selection?, expected: City) {
        val picked = selection as GlobeStackSelectionPolicy.Selection.SelectCity
        assertSame(expected, picked.city)
    }
}
