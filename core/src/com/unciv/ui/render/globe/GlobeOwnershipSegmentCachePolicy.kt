package com.unciv.ui.render.globe

import com.unciv.logic.map.tile.Tile

object GlobeOwnershipSegmentCachePolicy {
    fun signature(tile: Tile): Int {
        var hash = 17
        hash = 31 * hash + ownerHash(tile)

        val tileNeighbors = tile.neighbors.toList()
        val tileNeighborSet = tileNeighbors.toHashSet()
        for (neighbor in tileNeighbors.sortedBy { it.zeroBasedIndex }) {
            hash = 31 * hash + neighbor.zeroBasedIndex
            hash = 31 * hash + ownerHash(neighbor)

            val sharedNeighbors = neighbor.neighbors
                .filter { candidate ->
                    candidate != neighbor && candidate != tile && candidate in tileNeighborSet
                }
                .sortedBy { it.zeroBasedIndex }
                .toList()

            for (sharedNeighbor in sharedNeighbors) {
                hash = 31 * hash + sharedNeighbor.zeroBasedIndex
                hash = 31 * hash + ownerHash(sharedNeighbor)
            }

            // Preserve edge boundaries inside the rolling hash.
            hash = 31 * hash + 0x9E3779B9.toInt()
        }
        return hash
    }

    private fun ownerHash(tile: Tile): Int = tile.getOwner()?.civID?.hashCode() ?: 0
}
