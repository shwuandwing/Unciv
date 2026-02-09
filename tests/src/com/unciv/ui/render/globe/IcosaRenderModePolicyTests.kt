package com.unciv.ui.render.globe

import com.unciv.logic.map.MapShape
import org.junit.Assert
import org.junit.Test

class IcosaRenderModePolicyTests {

    @Test
    fun nonIcosaMapsAlwaysResolveToTwoDWithoutToggle() {
        val state = IcosaRenderModePolicy.resolve(MapShape.hexagonal, IcosaRenderMode.ThreeD)

        Assert.assertEquals(IcosaRenderMode.TwoD, state.effectiveMode)
        Assert.assertFalse(state.showToggle)
        Assert.assertFalse(state.isReadOnly)
    }

    @Test
    fun icosaThreeDModeIsReadOnlyAndKeepsToggleVisible() {
        val state = IcosaRenderModePolicy.resolve(MapShape.icosahedron, IcosaRenderMode.ThreeD)

        Assert.assertEquals(IcosaRenderMode.ThreeD, state.effectiveMode)
        Assert.assertTrue(state.showToggle)
        Assert.assertTrue(state.isReadOnly)
    }

    @Test
    fun icosaThreeDModeCanBeConfiguredAsInteractive() {
        val state = IcosaRenderModePolicy.resolve(
            MapShape.icosahedron,
            IcosaRenderMode.ThreeD,
            threeDReadOnly = false
        )

        Assert.assertEquals(IcosaRenderMode.ThreeD, state.effectiveMode)
        Assert.assertTrue(state.showToggle)
        Assert.assertFalse(state.isReadOnly)
    }

    @Test
    fun icosaTwoDModeIsEditableAndKeepsToggleVisible() {
        val state = IcosaRenderModePolicy.resolve(MapShape.icosahedron, IcosaRenderMode.TwoD)

        Assert.assertEquals(IcosaRenderMode.TwoD, state.effectiveMode)
        Assert.assertTrue(state.showToggle)
        Assert.assertFalse(state.isReadOnly)
    }
}
