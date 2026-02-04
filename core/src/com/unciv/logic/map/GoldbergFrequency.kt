package com.unciv.logic.map

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.math.roundToInt

object GoldbergMath {
    @yairm210.purity.annotations.Readonly
    fun tileCount(frequency: Int): Int = 10 * frequency * frequency + 2
}

object GoldbergFrequency {
    private val defaultCandidates = 2..12
    private val predefinedFrequencyBySizeName = mapOf(
        MapSize.Predefined.Tiny.name to 5,
        MapSize.Predefined.Small.name to 8,
        MapSize.Predefined.Medium.name to 11,
        MapSize.Predefined.Large.name to 16,
        MapSize.Predefined.Huge.name to 22
    )

    fun selectForMapSize(mapSize: MapSize, candidates: IntRange = defaultCandidates): Int {
        predefinedFrequencyBySizeName[mapSize.name]?.let { return it }
        val target = 1 + 3 * mapSize.radius * (mapSize.radius - 1)
        val nearest = nearestFrequencyForTarget(target)
        val dynamicCandidates = (nearest - 1).coerceAtLeast(candidates.first)..(nearest + 1)
        return selectForTarget(target, dynamicCandidates)
    }

    fun selectForTarget(targetTileCount: Int, candidates: IntRange = defaultCandidates): Int {
        var best = candidates.first
        var bestDiff = Int.MAX_VALUE
        for (f in candidates) {
            val diff = abs(GoldbergMath.tileCount(f) - targetTileCount)
            if (diff < bestDiff) {
                bestDiff = diff
                best = f
            }
        }
        return best
    }

    fun estimateFromTileCount(tileCount: Int): Int {
        val f = sqrt((tileCount - 2).toDouble() / 10.0)
        return f.roundToInt().coerceAtLeast(1)
    }

    private fun nearestFrequencyForTarget(targetTileCount: Int): Int {
        val root = sqrt((targetTileCount - 2).coerceAtLeast(0).toDouble() / 10.0)
        val lower = floor(root).toInt().coerceAtLeast(1)
        val upper = (lower + 1).coerceAtLeast(1)
        val lowerDiff = abs(GoldbergMath.tileCount(lower) - targetTileCount)
        val upperDiff = abs(GoldbergMath.tileCount(upper) - targetTileCount)
        return if (lowerDiff <= upperDiff) lower else upper
    }
}
