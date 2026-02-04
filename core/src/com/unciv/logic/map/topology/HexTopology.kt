package com.unciv.logic.map.topology

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.toHexCoord
import com.unciv.logic.map.tile.Tile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class HexTopology(private val tileMap: TileMap) : MapTopology {

    // Preserve the historical neighbor order from TileMap.getTilesAtDistance(distance=1)
    private val neighborDirections = listOf(
        HexCoord.of(-1, -1),
        HexCoord.of(1, 1),
        HexCoord.of(0, -1),
        HexCoord.of(0, 1),
        HexCoord.of(1, 0),
        HexCoord.of(-1, 0)
    )

    override fun getNeighbors(tile: Tile): Sequence<Tile> = sequence {
        val pos = tile.position
        for (dir in neighborDirections) {
            val neighbor = tileMap.getIfTileExistsOrNull(pos.x + dir.x, pos.y + dir.y)
            if (neighbor != null) yield(neighbor)
        }
    }

    override fun getTilesAtDistance(origin: Tile, distance: Int): Sequence<Tile> {
        if (distance <= 0) return sequenceOf(origin)
        val vectors = HexMath.getHexCoordsAtDistance(origin.position, distance, distance, tileMap.mapParameters.worldWrap)
        return vectors.asSequence().mapNotNull { tileMap.getIfTileExistsOrNull(it.x, it.y) }
    }

    override fun getTilesInDistanceRange(origin: Tile, range: IntRange): Sequence<Tile> =
        range.asSequence().flatMap { getTilesAtDistance(origin, it) }

    override fun getDistance(from: Tile, to: Tile): Int {
        val position = from.position
        val otherPosition = to.position
        val xDelta = position.x - otherPosition.x
        val yDelta = position.y - otherPosition.y
        val distance = maxOf(abs(xDelta), abs(yDelta), abs(xDelta - yDelta))

        if (!tileMap.mapParameters.worldWrap || distance <= tileMap.width / 2) return distance

        val otherUnwrapped = tileMap.getUnwrappedPosition(otherPosition)
        val xDeltaWrapped = position.x - otherUnwrapped.x
        val yDeltaWrapped = position.y - otherUnwrapped.y
        val wrappedDistance = maxOf(abs(xDeltaWrapped), abs(yDeltaWrapped), abs(xDeltaWrapped - yDeltaWrapped))

        return min(distance, wrappedDistance)
    }

    override fun getHeuristicDistance(from: Tile, to: Tile): Float = getDistance(from, to).toFloat()

    override fun getLatitude(tile: Tile): Int = HexMath.getLatitude(tile.position)

    override fun getLongitude(tile: Tile): Int = HexMath.getLongitude(tile.position)

    override fun getWorldPosition(tile: Tile): Vector2 = HexMath.hex2WorldCoords(tile.position)

    override fun getWorldBounds(): Rectangle {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (tile in tileMap.values) {
            val pos = getWorldPosition(tile)
            if (pos.x < minX) minX = pos.x
            if (pos.y < minY) minY = pos.y
            if (pos.x > maxX) maxX = pos.x
            if (pos.y > maxY) maxY = pos.y
        }
        return Rectangle(minX, minY, maxX - minX, maxY - minY)
    }

    override fun isEdge(tile: Tile): Boolean = tile.neighbors.count() < 6

    override fun edgeUniqueIndex(tile: Tile, neighbor: Tile): Int = HexMath.tilesAndNeighborUniqueIndex(tile, neighbor)

    override fun isSeamEdge(from: Tile, to: Tile): Boolean = false

    override fun getClosestTile(worldPos: Vector2): Tile? {
        val hexPos = HexMath.world2HexCoords(worldPos)
        val rounded = HexMath.roundHexCoords(hexPos).toHexCoord()

        if (!tileMap.mapParameters.worldWrap)
            return tileMap.getIfTileExistsOrNull(rounded.x, rounded.y)

        val wrapped = HexMath.getUnwrappedNearestTo(rounded, HexCoord.Zero, tileMap.maxLongitude)
        return tileMap.getIfTileExistsOrNull(wrapped.x.toInt(), wrapped.y.toInt())
            ?: tileMap.getIfTileExistsOrNull(rounded.x, rounded.y)
    }
}
