package com.unciv.logic.map.topology

import com.badlogic.gdx.math.Vector3
import com.unciv.logic.map.HexCoord
import org.junit.Assert
import org.junit.Test
import kotlin.math.abs
import kotlin.math.acos

class GoldbergNetNorthAxisTests {

    @Test
    fun selectsTopAndBottomCenterDeterministically() {
        val frequencies = listOf(5, 8, 11, 16, 22)
        for (frequency in frequencies) {
            val mesh = GoldbergMeshBuilder.build(frequency)
            val layout = GoldbergNetLayoutBuilder.buildIndexToCoord(
                frequency,
                mesh,
                GoldbergNetLayoutBuilder.DEFAULT_LAYOUT
            )

            val first = GoldbergNetNorthAxis.selectPoleTileIndices(layout.indexToCoord)
            val second = GoldbergNetNorthAxis.selectPoleTileIndices(layout.indexToCoord)
            Assert.assertEquals("Top-center mismatch for f=$frequency", first.topCenterIndex, second.topCenterIndex)
            Assert.assertEquals("Bottom-center mismatch for f=$frequency", first.bottomCenterIndex, second.bottomCenterIndex)

            assertIsTopCenter(layout.indexToCoord, first.topCenterIndex)
            assertIsBottomCenter(layout.indexToCoord, first.bottomCenterIndex)
        }
    }

    @Test
    fun basisIsUnitAndOrientedBySelectedPoleTiles() {
        val frequencies = listOf(5, 8, 11, 16, 22)
        val basisByFrequency = ArrayList<Vector3>()

        for (frequency in frequencies) {
            val mesh = GoldbergMeshBuilder.build(frequency)
            val layout = GoldbergNetLayoutBuilder.buildIndexToCoord(
                frequency,
                mesh,
                GoldbergNetLayoutBuilder.DEFAULT_LAYOUT
            )

            val basis = GoldbergNetNorthAxis.buildBasis(mesh, layout)
            basisByFrequency += basis.northAxis.cpy()

            Assert.assertEquals(1f, basis.northAxis.len(), 1e-5f)
            Assert.assertEquals(1f, basis.meridianAxis.len(), 1e-5f)
            Assert.assertEquals(1f, basis.eastAxis.len(), 1e-5f)
            Assert.assertEquals(-1f, basis.northAxis.dot(basis.southAxis), 1e-5f)
            Assert.assertEquals(0f, basis.northAxis.dot(basis.meridianAxis), 1e-5f)
            Assert.assertEquals(0f, basis.northAxis.dot(basis.eastAxis), 1e-5f)
            Assert.assertEquals(0f, basis.meridianAxis.dot(basis.eastAxis), 1e-5f)

            val topVec = mesh.vertices[basis.topCenterIndex].cpy().nor()
            val bottomVec = mesh.vertices[basis.bottomCenterIndex].cpy().nor()
            Assert.assertTrue(
                "North axis should point toward top-center tile for f=$frequency",
                basis.northAxis.dot(topVec) > 0f
            )
            Assert.assertTrue(
                "North axis should point away from bottom-center tile for f=$frequency",
                basis.northAxis.dot(bottomVec) < 0f
            )
        }

        // The net-derived north axis should be stable across frequencies for IcosaNetV2.
        val baseline = basisByFrequency.first()
        for ((i, axis) in basisByFrequency.withIndex()) {
            val angle = angleDegrees(baseline, axis)
            Assert.assertEquals("Axis drifted unexpectedly at index $i", 0.0, angle, 5.0)
        }
    }

    @Test
    fun frequencyEightDefaultLayoutPolesStayOnRowCenterNotCorners() {
        val frequency = 8
        val mesh = GoldbergMeshBuilder.build(frequency)
        val layout = GoldbergNetLayoutBuilder.buildIndexToCoord(
            frequency,
            mesh,
            GoldbergNetLayoutBuilder.DEFAULT_LAYOUT
        )
        val poles = GoldbergNetNorthAxis.selectPoleTileIndices(layout.indexToCoord)

        val topRow = layout.indexToCoord.indices.filter {
            layout.indexToCoord[it].y == layout.indexToCoord.minOf { coord -> coord.y }
        }
        val bottomRow = layout.indexToCoord.indices.filter {
            layout.indexToCoord[it].y == layout.indexToCoord.maxOf { coord -> coord.y }
        }
        if (topRow.size >= 3) {
            val minX = topRow.minOf { layout.indexToCoord[it].x }
            val maxX = topRow.maxOf { layout.indexToCoord[it].x }
            val selectedX = layout.indexToCoord[poles.topCenterIndex].x
            Assert.assertTrue(
                "Top pole must not be a corner tile for default f=8 layout",
                selectedX != minX && selectedX != maxX
            )
        }
        if (bottomRow.size >= 3) {
            val minX = bottomRow.minOf { layout.indexToCoord[it].x }
            val maxX = bottomRow.maxOf { layout.indexToCoord[it].x }
            val selectedX = layout.indexToCoord[poles.bottomCenterIndex].x
            Assert.assertTrue(
                "Bottom pole must not be a corner tile for default f=8 layout",
                selectedX != minX && selectedX != maxX
            )
        }
    }

    private fun assertIsTopCenter(coords: List<HexCoord>, selectedIndex: Int) {
        val minY = coords.minOf { it.y }
        val candidates = coords.indices.filter { coords[it].y == minY }
        val centerX = candidates.let {
            val minX = it.minOf { index -> coords[index].x }
            val maxX = it.maxOf { index -> coords[index].x }
            (minX + maxX) / 2.0
        }
        val selectedDistance = abs(coords[selectedIndex].x - centerX)
        Assert.assertTrue(selectedIndex in candidates)

        for (index in candidates) {
            if (index == selectedIndex) continue
            val candidateDistance = abs(coords[index].x - centerX)
            Assert.assertTrue(
                "Found top-row candidate closer to center than selected index $selectedIndex",
                candidateDistance >= selectedDistance - 1e-9
            )
        }
    }

    private fun assertIsBottomCenter(coords: List<HexCoord>, selectedIndex: Int) {
        val maxY = coords.maxOf { it.y }
        val candidates = coords.indices.filter { coords[it].y == maxY }
        val centerX = candidates.let {
            val minX = it.minOf { index -> coords[index].x }
            val maxX = it.maxOf { index -> coords[index].x }
            (minX + maxX) / 2.0
        }
        val selectedDistance = abs(coords[selectedIndex].x - centerX)
        Assert.assertTrue(selectedIndex in candidates)

        for (index in candidates) {
            if (index == selectedIndex) continue
            val candidateDistance = abs(coords[index].x - centerX)
            Assert.assertTrue(
                "Found bottom-row candidate closer to center than selected index $selectedIndex",
                candidateDistance >= selectedDistance - 1e-9
            )
        }
    }

    private fun angleDegrees(a: Vector3, b: Vector3): Double {
        val dot = a.cpy().nor().dot(b.cpy().nor()).toDouble().coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(dot))
    }
}
