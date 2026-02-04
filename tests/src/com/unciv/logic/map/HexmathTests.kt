package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.unciv.json.json
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.tile.Terrain
import com.unciv.models.ruleset.tile.TerrainType
import org.junit.Assert
import org.junit.Test
import kotlin.random.Random
import kotlin.random.nextInt


class HexmathTests {
    // Looks like our current movement is actually unoptimized, since it fails this test :)
    @Test
    fun zeroIndexed() {
        Assert.assertEquals(0, HexMath.getZeroBasedIndex(0,0))
    }

    @Test
    fun testMappingIsOneToOne() {
        val seenCoordsMapping = hashSetOf<Int>()
        for (ring in 1..100) {
            val coords = HexMath.getHexCoordsAtDistance(HexCoord.Zero, ring, 100, false)
            val ringStartCoordinate = 1 + 6 * ring * (ring - 1) / 2
            for (coord in coords) {
                val mapping = HexMath.getZeroBasedIndex(coord.x.toInt(), coord.y.toInt())
                Assert.assertFalse("Duplicate coords found: $coord", seenCoordsMapping.contains(mapping))
                Assert.assertTrue("Coords $coord should be in ring $ring, actual mapping $mapping", mapping in ringStartCoordinate .. (ringStartCoordinate + 6 * ring - 1))

                seenCoordsMapping.add(mapping)
            }
        }
    }

    @Test
    fun testTileNeighborMappingIsNonConflicting() {
        val ruleset = Ruleset()
        ruleset.terrains["Plains"] = Terrain().apply {
            name = "Plains"
            type = TerrainType.Land
        }
        val tileMap = TileMap(100, ruleset, false)
        val seenCoordsMapping = hashSetOf<Int>()

        for (tile in tileMap.values){
            for (neighbor in tile.neighbors){
                val index = tileMap.edgeUniqueIndex(tile, neighbor)
                Assert.assertFalse("Duplicate coords found: $neighbor", seenCoordsMapping.contains(index))

                seenCoordsMapping.add(index)
            }
        }
    }

    @Test
    fun testHexCoordConversions() {
        for (x in -2..2)
            for (y in -2..2){
                val hexCoord = HexCoord.of(x, y)
                Assert.assertEquals(hexCoord.x, x)
                Assert.assertEquals(hexCoord.y, y)
            }
    }

    @Test
    fun testHexCoordVector2SerDeser() {
        val json = json()

        for (x in -2..2)
            for (y in -2..2) {
                val hexCoord = HexCoord(x, y)
                val vector2 = Vector2(x.toFloat(), y.toFloat())

                // hexcoord -> json -> vector2
                val hexCoordSerialized = json.toJson(hexCoord)
                val vector2Deserialized = json.fromJson(Vector2::class.java, hexCoordSerialized)
                Assert.assertEquals(vector2, vector2Deserialized)

                // vector2 -> json -> hexcoord
                val vector2Serialized = json.toJson(vector2)
                val hexCoordDeserialized = json.fromJson(HexCoord::class.java, vector2Serialized)
                Assert.assertEquals(hexCoord, hexCoordDeserialized)
            }
    }

    @Test
    fun testCanDeserializeVerboseVector2Format() {
        val json = json()
        val input = """{"x": {"class": "java.lang.Float", "value": 23}, "y": {"class": "java.lang.Float", "value": -23}}"""
        val actual = json.fromJson(HexCoord::class.java, input)
        val expected = HexCoord.of(23, -23)
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun testSerializedHexCoordIsCompact() {
        val json = json()
        val input = HexCoord.of(-42, 42)
        val actual = json.toJson(input)
        val expected = """{"x":-42,"y":42}"""
        Assert.assertEquals(expected, actual)
    }

    @Test
    fun testHexCoordFormatAndParseBack() {
        val rnd = Random(42)
        fun rndCoord() = rnd.nextInt(-20..20)

        repeat(42) {
            val expected = HexCoord.of(rndCoord(), rndCoord())
            val prettyString = expected.toPrettyString()
            val actual = HexCoord.fromString(prettyString)
            Assert.assertEquals(expected, actual)
        }
    }

    @Test
    fun testHexagonAreaAndRadius() {
        Assert.assertEquals(1, HexMath.getNumberOfTilesInHexagon(0))
        Assert.assertEquals(7, HexMath.getNumberOfTilesInHexagon(1))
        Assert.assertEquals(19, HexMath.getNumberOfTilesInHexagon(2))
        Assert.assertEquals(37, HexMath.getNumberOfTilesInHexagon(3))
        Assert.assertEquals(0, HexMath.getNumberOfTilesInHexagon(-1))

        Assert.assertEquals(0f, HexMath.getHexagonalRadiusForArea(1), 0.001f)
        Assert.assertEquals(1f, HexMath.getHexagonalRadiusForArea(7), 0.001f)
        Assert.assertEquals(2f, HexMath.getHexagonalRadiusForArea(19), 0.001f)
        Assert.assertEquals(3f, HexMath.getHexagonalRadiusForArea(37), 0.001f)
        Assert.assertEquals(0f, HexMath.getHexagonalRadiusForArea(0), 0.001f)
    }

    @Test
    fun testLatLongConversions() {
        val coord = HexCoord.of(1, 2)
        Assert.assertEquals(3, HexMath.getLatitude(coord))
        Assert.assertEquals(-1, HexMath.getLongitude(coord))

        val vector = HexMath.hexFromLatLong(3, -1)
        Assert.assertEquals(1f, vector.x, 0.001f)
        Assert.assertEquals(2f, vector.y, 0.001f)
    }

    @Test
    fun testEquivalentSizes() {
        val radius = 20
        val rectSize = HexMath.getEquivalentRectangularSize(radius)
        Assert.assertEquals(radius, HexMath.getEquivalentHexagonalRadius(rectSize.x, rectSize.y))
    }

    @Test
    fun testDistance() {
        val origin = HexCoord.of(0, 0)
        Assert.assertEquals(0, HexMath.getDistance(origin, HexCoord.of(0, 0)))
        Assert.assertEquals(1, HexMath.getDistance(origin, HexCoord.of(1, 1)))
        Assert.assertEquals(1, HexMath.getDistance(origin, HexCoord.of(1, 0)))
        Assert.assertEquals(1, HexMath.getDistance(origin, HexCoord.of(0, 1)))
        Assert.assertEquals(1, HexMath.getDistance(origin, HexCoord.of(-1, -1)))
        Assert.assertEquals(1, HexMath.getDistance(origin, HexCoord.of(-1, 0)))
        Assert.assertEquals(1, HexMath.getDistance(origin, HexCoord.of(0, -1)))

        Assert.assertEquals(2, HexMath.getDistance(origin, HexCoord.of(1, -1)))
        Assert.assertEquals(2, HexMath.getDistance(origin, HexCoord.of(-1, 1)))

        Assert.assertEquals(7, HexMath.getDistance(HexCoord.of(1, 2), HexCoord.of(4, -2)))
    }

    @Test
    fun testRowColumn() {
        val origin = HexCoord.of(0, 0)
        Assert.assertEquals(0, HexMath.getRow(origin))
        Assert.assertEquals(0, HexMath.getColumn(origin))

        val coord = HexCoord.of(1, 2)
        Assert.assertEquals(1, HexMath.getRow(coord))
        Assert.assertEquals(1, HexMath.getColumn(coord))

        val coord2 = HexCoord.of(2, 2)
        Assert.assertEquals(2, HexMath.getRow(coord2))
        Assert.assertEquals(0, HexMath.getColumn(coord2))

        for (row in 0..10) { // Only test positive rows where getRow is a clean inverse
            for (col in -10..10) {
                val c = HexMath.getTileCoordsFromColumnRow(col, row)
                Assert.assertEquals("Column mismatch for col $col row $row", col, HexMath.getColumn(c))
                Assert.assertEquals("Row mismatch for col $col row $row", row, HexMath.getRow(c))
            }
        }
    }

    @Test
    fun testInlineHexCoord() {
        val c = InlineHexCoord.of(10, -5)
        Assert.assertEquals(10, c.x)
        Assert.assertEquals(-5, c.y)

        val c2 = InlineHexCoord.of(-32768, 32767)
        Assert.assertEquals(-32768, c2.x)
        Assert.assertEquals(32767, c2.y)
    }

    @Test
    fun testClockPositions() {
        Assert.assertEquals(HexCoord.of(1, 1), HexMath.getClockPositionToHexcoord(12))
        Assert.assertEquals(HexCoord.of(1, 1), HexMath.getClockPositionToHexcoord(0))
        Assert.assertEquals(HexCoord.of(0, 1), HexMath.getClockPositionToHexcoord(2))
        Assert.assertEquals(HexCoord.of(-1, 0), HexMath.getClockPositionToHexcoord(4))
        Assert.assertEquals(HexCoord.of(-1, -1), HexMath.getClockPositionToHexcoord(6))
        Assert.assertEquals(HexCoord.of(0, -1), HexMath.getClockPositionToHexcoord(8))
        Assert.assertEquals(HexCoord.of(1, 0), HexMath.getClockPositionToHexcoord(10))
        Assert.assertEquals(HexCoord.Zero, HexMath.getClockPositionToHexcoord(1))
    }
}
