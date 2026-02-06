package com.unciv.ui.components.tilegroups.layers

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.topology.GoldbergTopology
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.utils.DebugUtils
import kotlin.math.max
import kotlin.math.sqrt

class TileLayerOverlay(tileGroup: TileGroup, size: Float) : TileLayer(tileGroup, size) {

    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha) // perf

    private var highlight: Image? = null // for blue and red circles/emphasis on the tile
    private var crosshair: Image? = null // for when a unit is targeted
    private var goodCityLocationIndicator: Image? = null
    private var fog: Image? = null
    private var unexplored: Image? = null
    private val seamMarkers = HashMap<Tile, Image>()
    private val faceBoundaryMarkers = HashMap<Tile, Image>()
    private var faceLabel: Actor? = null

    private fun getHighlight() = ImageGetter.getImage(strings.highlight).setHexagonSize() // for blue and red circles/emphasis on the tile
    private fun getCrosshair() = ImageGetter.getImage(strings.crosshair).setHexagonSize() // for when a unit is targeted
    private fun getGoodCityLocationIndicator() = ImageGetter.getImage("OtherIcons/Cities").setHexagonSize(0.25f)
    private fun getFog() = ImageGetter.getImage(strings.crosshatchHexagon ).setHexagonSize().apply { 
        color = Color.WHITE.cpy().apply { a = 0.2f }
    }
    private fun getUnexplored() = ImageGetter.getImage(strings.unexploredTile ).setHexagonSize()
    
    fun orderToFront() {
        unexplored?.toFront()
        highlight?.toFront()
        fog?.toFront()
        crosshair?.toFront()
        goodCityLocationIndicator?.toFront()
        for (marker in seamMarkers.values) marker.toFront()
        for (marker in faceBoundaryMarkers.values) marker.toFront()
        faceLabel?.toFront()
    }

    fun showCrosshair(alpha: Float = 1f) {
        if (crosshair != null){
            crosshair = getCrosshair()
            addActor(crosshair)
            determineVisibility()
        }
        crosshair?.color?.a = alpha
    }

    fun hideCrosshair() {
        if (crosshair == null) return
        crosshair?.remove()
        crosshair = null
        determineVisibility()
    }

    fun showHighlight(color: Color = Color.WHITE, alpha: Float = 0.3f) {
        if (highlight == null) {
            highlight = getHighlight()
            addActor(highlight)
            determineVisibility()
        }
        highlight?.color = color.cpy().apply { a = alpha }
    }

    fun hideHighlight() {
        if (highlight == null) return
        highlight?.remove()
        highlight = null
        determineVisibility()
    }

    fun showGoodCityLocationIndicator() {
        if (goodCityLocationIndicator != null) return
        goodCityLocationIndicator = getGoodCityLocationIndicator()
        addActor(goodCityLocationIndicator)
        determineVisibility()
    }

    fun hideGoodCityLocationIndicator() {
        if (goodCityLocationIndicator == null) return
        goodCityLocationIndicator?.remove()
        goodCityLocationIndicator = null
        determineVisibility()
    }

    fun reset() {
        hideHighlight()
        hideCrosshair()
        hideGoodCityLocationIndicator()
        clearSeamMarkers()
        clearFaceOverlay()
        determineVisibility()
    }

    override fun doUpdate(viewingCiv: Civilization?, localUniqueCache: LocalUniqueCache) {
        val isViewable = viewingCiv == null || isViewable(viewingCiv)
        
        setFog(isViewable)
        updateSeamMarkers()
        updateFaceOverlay()
        
        if (viewingCiv == null) return

        setUnexplored(viewingCiv)

        if (tile.getShownImprovement(viewingCiv) == Constants.barbarianEncampment
                && tile.isExplored(viewingCiv))
            showHighlight(Color.RED)
    }

    fun setUnexplored(viewingCiv: Civilization) {
        val unexploredShouldBeVisible = !viewingCiv.hasExplored(tile)
        val unexploredIsVisible = unexplored != null
        if (unexploredIsVisible && !unexploredShouldBeVisible) {
            unexplored?.remove()
            determineVisibility()
        } else if (!unexploredIsVisible && unexploredShouldBeVisible
                && ImageGetter.imageExists(strings.unexploredTile)) {
            unexplored = getUnexplored()
            addActor(unexplored)
            determineVisibility()
        }
    }

    private fun setFog(isViewable: Boolean) {
        val fogShouldBeVisible = !isViewable && !tileGroup.isForceVisible
        val fogIsVisible = fog != null
        if (fogIsVisible && !fogShouldBeVisible) {
            fog?.remove()
            fog = null
            determineVisibility()
        } else if (!fogIsVisible && fogShouldBeVisible) {
            fog = getFog()
            addActor(fog)
            determineVisibility()
        }
    }

    override fun determineVisibility() {
        isVisible = fog != null || unexplored != null || highlight != null || crosshair != null
            || goodCityLocationIndicator != null || seamMarkers.isNotEmpty()
            || faceBoundaryMarkers.isNotEmpty() || faceLabel != null
        orderToFront()
    }

    private fun updateSeamMarkers() {
        if (!tile.hasTileMap()) {
            clearSeamMarkers()
            return
        }

        val isIcosahedron = tile.tileMap.mapParameters.shape == MapShape.icosahedron
        if (!DebugUtils.SHOW_SEAM_EDGES || !isIcosahedron) {
            clearSeamMarkers()
            return
        }

        val centerX = tileGroup.hexagonImagePosition.first + tileGroup.hexagonImageOrigin.first
        val centerY = tileGroup.hexagonImagePosition.second + tileGroup.hexagonImageOrigin.second
        val lineLength = tileGroup.hexagonImageWidth * 0.22f
        val lineThickness = max(1f, tileGroup.hexagonImageWidth * 0.03f)
        val offset = tileGroup.hexagonImageOrigin.second * 0.9f

        val stale = seamMarkers.keys.toMutableSet()
        for (neighbor in tile.neighbors) {
            if (!tile.tileMap.topology.isSeamEdge(tile, neighbor)) continue
            stale.remove(neighbor)
            if (seamMarkers.containsKey(neighbor)) continue

            val fromPos = tile.tileMap.getWorldPositionForRendering(tile)
            val toPos = tile.tileMap.getWorldPositionForRendering(neighbor)
            val dx = toPos.x - fromPos.x
            val dy = toPos.y - fromPos.y
            val length = sqrt(dx * dx + dy * dy)
            if (length == 0f) continue
            val dirX = dx / length
            val dirY = dy / length

            val startX = centerX + dirX * (offset - lineLength * 0.5f)
            val startY = centerY + dirY * (offset - lineLength * 0.5f)
            val endX = centerX + dirX * (offset + lineLength * 0.5f)
            val endY = centerY + dirY * (offset + lineLength * 0.5f)

            val marker = ImageGetter.getLine(startX, startY, endX, endY, lineThickness)
            marker.color = Color.MAGENTA.cpy().apply { a = 0.75f }
            addActor(marker)
            seamMarkers[neighbor] = marker
        }

        for (neighbor in stale) {
            seamMarkers.remove(neighbor)?.remove()
        }
    }

    private fun clearSeamMarkers() {
        if (seamMarkers.isEmpty()) return
        for (marker in seamMarkers.values) marker.remove()
        seamMarkers.clear()
    }

    private fun updateFaceOverlay() {
        if (!tile.hasTileMap()) {
            clearFaceOverlay()
            return
        }
        val isIcosahedron = tile.tileMap.mapParameters.shape == MapShape.icosahedron
        val topology = tile.tileMap.topology as? GoldbergTopology
        if (!DebugUtils.SHOW_ICOSA_FACES || !isIcosahedron || topology == null) {
            clearFaceOverlay()
            return
        }

        val centerX = tileGroup.hexagonImagePosition.first + tileGroup.hexagonImageOrigin.first
        val centerY = tileGroup.hexagonImagePosition.second + tileGroup.hexagonImageOrigin.second
        val lineLength = tileGroup.hexagonImageWidth * 0.32f
        val lineThickness = max(1f, tileGroup.hexagonImageWidth * 0.03f)
        val offset = tileGroup.hexagonImageOrigin.second * 0.72f

        val stale = faceBoundaryMarkers.keys.toMutableSet()
        for (neighbor in tile.neighbors) {
            if (!topology.isFaceBoundaryForDebug(tile, neighbor)) continue
            stale.remove(neighbor)
            if (faceBoundaryMarkers.containsKey(neighbor)) continue

            val fromPos = tile.tileMap.getWorldPositionForRendering(tile)
            val toPos = tile.tileMap.getWorldPositionForRendering(neighbor)
            val dx = toPos.x - fromPos.x
            val dy = toPos.y - fromPos.y
            val length = sqrt(dx * dx + dy * dy)
            if (length == 0f) continue
            val dirX = dx / length
            val dirY = dy / length

            val startX = centerX + dirX * (offset - lineLength * 0.5f)
            val startY = centerY + dirY * (offset - lineLength * 0.5f)
            val endX = centerX + dirX * (offset + lineLength * 0.5f)
            val endY = centerY + dirY * (offset + lineLength * 0.5f)

            val marker = ImageGetter.getLine(startX, startY, endX, endY, lineThickness)
            marker.color = Color.GOLDENROD.cpy().apply { a = 0.8f }
            addActor(marker)
            faceBoundaryMarkers[neighbor] = marker
        }
        for (neighbor in stale) {
            faceBoundaryMarkers.remove(neighbor)?.remove()
        }

        val face = topology.getPrimaryFaceForDebug(tile)
        val shouldShowLabel = face >= 0 && topology.getFaceLabelTileForDebug(face) == tile.zeroBasedIndex
        if (!shouldShowLabel) {
            faceLabel?.remove()
            faceLabel = null
            return
        }
        if (faceLabel == null) {
            faceLabel = "F$face".toLabel(Color.ORANGE, 14).apply {
                center(tileGroup)
                moveBy(0f, 8f)
            }
            addActor(faceLabel)
        }
    }

    private fun clearFaceOverlay() {
        if (faceBoundaryMarkers.isNotEmpty()) {
            for (marker in faceBoundaryMarkers.values) marker.remove()
            faceBoundaryMarkers.clear()
        }
        faceLabel?.remove()
        faceLabel = null
    }

}
