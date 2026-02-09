package com.unciv.ui.render.globe

import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import kotlin.math.floor

object GlobeYieldOverlayPolicy {
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
}
