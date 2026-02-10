package com.unciv.ui.render.globe

import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import kotlin.math.floor

object GlobeYieldOverlayPolicy {
    data class MarkerOffset(
        val x: Float,
        val y: Float
    )

    data class YieldIcon(
        val stat: Stat,
        val value: Int,
        val iconLocation: String
    )

    fun resolve(
        stats: Stats,
        maxIcons: Int = 3
    ): List<YieldIcon> {
        return stats.asSequence()
            .map { it.key to floor(it.value).toInt() }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<Stat, Int>> { it.second }
                    .thenBy { it.first.ordinal }
            )
            .take(maxIcons.coerceAtLeast(0))
            .map { (stat, value) ->
                YieldIcon(
                    stat = stat,
                    value = value,
                    iconLocation = "StatIcons/${stat.name}"
                )
            }
            .toList()
    }

    /**
     * Returns per-stat icon offsets following the same compact cluster patterns used in 2D yields:
     * 1 => single icon
     * 2 => vertical pair
     * 3 => one top, two bottom
     * 4+ => 2x2 block (values above 4 are clamped for readability in 3D)
     */
    fun markerOffsets(value: Int): List<MarkerOffset> = when (value.coerceAtLeast(1).coerceAtMost(4)) {
        1 -> listOf(MarkerOffset(0f, 0f))
        2 -> listOf(
            MarkerOffset(0f, 0.5f),
            MarkerOffset(0f, -0.5f)
        )
        3 -> listOf(
            MarkerOffset(0f, 0.55f),
            MarkerOffset(-0.5f, -0.35f),
            MarkerOffset(0.5f, -0.35f)
        )
        else -> listOf(
            MarkerOffset(-0.5f, 0.5f),
            MarkerOffset(0.5f, 0.5f),
            MarkerOffset(-0.5f, -0.5f),
            MarkerOffset(0.5f, -0.5f)
        )
    }
}
