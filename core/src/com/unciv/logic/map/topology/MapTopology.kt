package com.unciv.logic.map.topology

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.Readonly

interface MapTopology {
    @Readonly fun getNeighbors(tile: Tile): Sequence<Tile>
    @Readonly fun getTilesAtDistance(origin: Tile, distance: Int): Sequence<Tile>
    @Readonly fun getTilesInDistanceRange(origin: Tile, range: IntRange): Sequence<Tile>
    @Readonly fun getDistance(from: Tile, to: Tile): Int
    @Readonly fun getHeuristicDistance(from: Tile, to: Tile): Float
    @Readonly fun getLatitude(tile: Tile): Int
    @Readonly fun getLongitude(tile: Tile): Int
    @Readonly fun getWorldPosition(tile: Tile): Vector2
    @Readonly fun getWorldBounds(): Rectangle
    @Readonly fun isEdge(tile: Tile): Boolean
    @Readonly fun edgeUniqueIndex(tile: Tile, neighbor: Tile): Int
    @Readonly fun isSeamEdge(from: Tile, to: Tile): Boolean
    @Readonly fun getClosestTile(worldPos: Vector2): Tile?
}
