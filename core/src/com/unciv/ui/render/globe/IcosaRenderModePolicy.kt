package com.unciv.ui.render.globe

import com.unciv.logic.map.MapShape

enum class IcosaRenderMode {
    TwoD,
    ThreeD
}

data class IcosaRenderModeState(
    val effectiveMode: IcosaRenderMode,
    val showToggle: Boolean,
    val isReadOnly: Boolean
)

object IcosaRenderModePolicy {
    fun resolve(
        shape: String,
        requestedMode: IcosaRenderMode,
        threeDReadOnly: Boolean = true
    ): IcosaRenderModeState {
        val isIcosa = shape == MapShape.icosahedron
        if (!isIcosa) {
            return IcosaRenderModeState(
                effectiveMode = IcosaRenderMode.TwoD,
                showToggle = false,
                isReadOnly = false
            )
        }
        return IcosaRenderModeState(
            effectiveMode = requestedMode,
            showToggle = true,
            isReadOnly = requestedMode == IcosaRenderMode.ThreeD && threeDReadOnly
        )
    }
}
