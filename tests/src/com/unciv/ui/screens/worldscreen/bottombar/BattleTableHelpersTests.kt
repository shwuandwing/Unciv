package com.unciv.ui.screens.worldscreen.bottombar

import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.mapgenerator.GoldbergMapBuilder
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import org.junit.Assert
import org.junit.Test
import kotlin.math.absoluteValue
import kotlin.math.atan2
import kotlin.math.sqrt

class BattleTableHelpersTests {

    private fun basicRuleset(): Ruleset {
        val ruleset = Ruleset()
        ruleset.terrains["Plains"] = Terrain().apply {
            name = "Plains"
            type = TerrainType.Land
        }
        return ruleset
    }

    @Test
    fun icosaBattleAttackVectorUsesRenderRotation() {
        val map = GoldbergMapBuilder.build(
            MapParameters().apply {
                shape = MapShape.icosahedron
                mapSize = MapSize.Tiny
            },
            basicRuleset()
        )

        val from = map.tileList.first()
        val to = from.neighbors.first { !map.topology.isSeamEdge(from, it) }

        val vector = BattleTableHelpers.computeAttackVectorForAnimation(from, to, 10f)
        val length = sqrt(vector.x * vector.x + vector.y * vector.y)
        Assert.assertEquals(10.0, length.toDouble(), 1e-3)

        val fromWorld = map.topology.getWorldPosition(from)
        val toWorld = map.topology.getWorldPosition(to)
        val worldAngle = atan2(toWorld.y - fromWorld.y, toWorld.x - fromWorld.x).toFloat()
        val battleAngle = atan2(vector.y, vector.x).toFloat()
        val delta = normalizeAngle(battleAngle - worldAngle)

        Assert.assertEquals(Math.PI / 6.0, delta.toDouble(), 0.03)
    }

    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle
        val pi = Math.PI.toFloat()
        val tau = (2 * Math.PI).toFloat()
        while (normalized <= -pi) normalized += tau
        while (normalized > pi) normalized -= tau
        return normalized.absoluteValue
    }
}
