package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.widgets.ZoomableScrollPane
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.setFontSize

class ZoomButtonPair(
    private val zoomInAction: () -> Unit,
    private val zoomOutAction: () -> Unit
) : Table(BaseScreen.skin) {
    constructor(mapHolder: ZoomableScrollPane) : this(
        zoomInAction = { mapHolder.zoomIn() },
        zoomOutAction = { mapHolder.zoomOut() }
    )

    init {
        addButton("+") {
            zoomInAction()
        }.padRight(10f)
        addButton("–") {  // figure dash U+2013, not minus, looks better
            zoomOutAction()
        }
        pack()
    }

    private fun addButton(text: String, action: () -> Unit): Cell<TextButton> {
        val button = TextButton(text, skin)
        button.label.setFontSize(30)
        button.label.setAlignment(Align.center)
        button.onClick(action)
        return add(button)
    }
}
