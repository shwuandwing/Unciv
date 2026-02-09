package com.unciv.ui.render.globe

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.NeighborDirection
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.images.ImageGetter
import com.unciv.logic.map.tile.Tile
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class IcosaGlobeActor(
    private val tileMapProvider: () -> com.unciv.logic.map.TileMap,
    private val visibilityContextProvider: () -> GlobeVisibilityPolicy.Context = { GlobeVisibilityPolicy.Context() },
    private val selectedUnitProvider: () -> MapUnit? = { null },
    private val onTileClick: (Tile) -> Unit = {}
) : Actor(), Disposable {
    private data class UnitVisual(
        val unit: MapUnit,
        val yOffsetFactor: Float
    )

    private val shapeRenderer = ShapeRenderer()
    private val polygonBatch = PolygonSpriteBatch()
    private var tileMap = tileMapProvider()
    private var cache = IcosaMeshRuntimeCache.from(tileMap)
    private var tileSetStrings = TileSetStrings(
        requireNotNull(tileMap.ruleset) { "3D globe renderer requires tileMap.ruleset transients" },
        com.unciv.UncivGame.Current.settings
    )

    private val camera = PerspectiveCamera(42f, 1f, 1f)
    private val cameraController = GlobeCameraController(distance = 4.15f)

    private var selectedTileIndex = -1
    private var dragPointer = -1
    private var wasDragging = false
    private var lastX = 0f
    private var lastY = 0f
    private var hoveredTileIndex = -1

    private var projectedCenters = Array(tileMap.tileList.size) { Vector2() }
    private var projectedPolygons = arrayOfNulls<FloatArray>(tileMap.tileList.size)
    private var projectedDistances = FloatArray(tileMap.tileList.size)
    private var projectedVisible = BooleanArray(tileMap.tileList.size)
    private var projectedFacing = FloatArray(tileMap.tileList.size)
    private var projectedOverlayRotations = FloatArray(tileMap.tileList.size)
    private var projectedDirectionalOverlayRotations = FloatArray(tileMap.tileList.size)
    private var projectedTileExplored = BooleanArray(tileMap.tileList.size)
    private var projectedTileVisible = BooleanArray(tileMap.tileList.size)
    private val drawOrder = ArrayList<Int>(tileMap.tileList.size)

    private val tempWorld = Vector3()
    private val tempScreen = Vector3()
    private val tempStage = Vector2()
    private val cameraDirectionFromOrigin = Vector3()
    private val overlayTrianglesByVertexCount = HashMap<Int, ShortArray>()
    private val unexploredTileColor = Color(0.03f, 0.05f, 0.11f, 1f)
    private val overlayRegionCache = HashMap<String, TextureRegion?>()

    init {
        touchable = Touchable.enabled
        addListener(object : InputListener() {
            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
                dragPointer = pointer
                wasDragging = false
                lastX = x
                lastY = y
                return true
            }

            override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
                if (pointer != dragPointer) return
                val deltaX = x - lastX
                val deltaY = y - lastY
                if (kotlin.math.abs(deltaX) > 0.4f || kotlin.math.abs(deltaY) > 0.4f) {
                    wasDragging = true
                }
                cameraController.rotateBy(deltaX, deltaY)
                lastX = x
                lastY = y
                hoveredTileIndex = -1
            }

            override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int) {
                if (pointer != dragPointer) return
                dragPointer = -1
                if (wasDragging) return

                val stageCoords = localToStageCoordinates(Vector2(x, y))
                val tile = pickTile(stageCoords.x, stageCoords.y) ?: return
                selectedTileIndex = tile.zeroBasedIndex
                hoveredTileIndex = tile.zeroBasedIndex
                onTileClick(tile)
            }

            override fun scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
                cameraController.zoomBy(amountY)
                return true
            }

            override fun mouseMoved(event: InputEvent, x: Float, y: Float): Boolean {
                val stageCoords = localToStageCoordinates(Vector2(x, y))
                hoveredTileIndex = pickTile(stageCoords.x, stageCoords.y)?.zeroBasedIndex ?: -1
                return false
            }
        })
    }

    fun refreshTileMap() {
        val latestMap = tileMapProvider()
        if (latestMap === tileMap) return
        tileMap = latestMap
        cache = IcosaMeshRuntimeCache.from(tileMap)
        tileSetStrings = TileSetStrings(
            requireNotNull(tileMap.ruleset) { "3D globe renderer requires tileMap.ruleset transients" },
            com.unciv.UncivGame.Current.settings
        )
        overlayRegionCache.clear()
        selectedTileIndex = -1
        hoveredTileIndex = -1

        projectedCenters = Array(tileMap.tileList.size) { Vector2() }
        projectedPolygons = arrayOfNulls<FloatArray>(tileMap.tileList.size)
        projectedDistances = FloatArray(tileMap.tileList.size)
        projectedVisible = BooleanArray(tileMap.tileList.size)
        projectedFacing = FloatArray(tileMap.tileList.size)
        projectedOverlayRotations = FloatArray(tileMap.tileList.size)
        projectedDirectionalOverlayRotations = FloatArray(tileMap.tileList.size)
        projectedTileExplored = BooleanArray(tileMap.tileList.size)
        projectedTileVisible = BooleanArray(tileMap.tileList.size)
        drawOrder.clear()
    }

    fun resetToNorth() {
        cameraController.resetToNorth()
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        refreshTileMap()
        projectTiles()
        val selectedUnit = selectedUnitProvider()

        batch.end()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.projectionMatrix = stage.camera.combined
        drawTileSurface()
        drawSelectedUnitReachableOverlay(selectedUnit)
        drawBorders()
        drawSelectedUnitPathPreview(selectedUnit)

        drawPolygonTileOverlays(parentAlpha)

        batch.begin()
        drawRoadOverlaySprites(batch, parentAlpha)
        drawOwnershipBorderSprites(batch, parentAlpha)
        drawSpriteMarkers(batch, parentAlpha)
        batch.end()

        batch.begin()
    }

    private fun projectTiles() {
        drawOrder.clear()

        camera.viewportWidth = stage.viewport.screenWidth.toFloat()
        camera.viewportHeight = stage.viewport.screenHeight.toFloat()
        cameraController.applyTo(camera)
        cameraDirectionFromOrigin.set(camera.position).nor()
        val visibilityContext = visibilityContextProvider()

        for (tile in tileMap.tileList) {
            val index = tile.zeroBasedIndex
            projectedVisible[index] = false
            projectedPolygons[index] = null
            projectedFacing[index] = 0f
            projectedOverlayRotations[index] = 0f
            projectedDirectionalOverlayRotations[index] = 0f
            val tileVisibility = GlobeVisibilityPolicy.resolve(tile, visibilityContext)
            projectedTileExplored[index] = tileVisibility.isExplored
            projectedTileVisible[index] = tileVisibility.isVisible

            val centerDirection = cache.centers[index]
            val facing = centerDirection.dot(cameraDirectionFromOrigin)
            if (facing <= 0.05f) continue

            val centerWorld = IcosaProjection.projectToSphere(centerDirection, 1f)
            projectToStage(centerWorld, projectedCenters[index])
            projectedDistances[index] = camera.position.dst2(centerWorld)
            projectedFacing[index] = facing
            val northDirection = GlobeOverlayOrientation.localNorthOffsetDirection(centerDirection)
            val northWorld = IcosaProjection.projectToSphere(northDirection, 1f)
            projectToStage(northWorld, tempStage)
            val northDx = tempStage.x - projectedCenters[index].x
            val northDy = tempStage.y - projectedCenters[index].y
            val localNorthRotation = GlobeOverlayOrientation.screenRotationDegrees(projectedCenters[index], tempStage)

            val corners = cache.cornerRings[index]
            val polygon = FloatArray(corners.size * 2)
            for (cornerIndex in corners.indices) {
                val cornerWorld = IcosaProjection.projectToSphere(corners[cornerIndex], 1f)
                projectToStage(cornerWorld, tempStage)
                polygon[cornerIndex * 2] = tempStage.x
                polygon[cornerIndex * 2 + 1] = tempStage.y
            }

            projectedPolygons[index] = polygon
            val regularBaseRotation = if (northDx * northDx + northDy * northDy > 1e-6f) {
                GlobeOverlayOrientation.screenRotationFromPolygonNearestTo(
                    center = projectedCenters[index],
                    polygon = polygon,
                    referenceRotationDegrees = localNorthRotation
                )
            } else {
                GlobeOverlayOrientation.screenRotationFromPolygonTopVertex(projectedCenters[index], polygon)
            }
            projectedOverlayRotations[index] = regularBaseRotation
            projectedDirectionalOverlayRotations[index] = resolveDirectionalBaseRotation(index, regularBaseRotation, polygon)
            projectedVisible[index] = true
            drawOrder.add(index)
        }

        drawOrder.sortByDescending { projectedDistances[it] }
    }

    private fun drawTileSurface() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        for (index in drawOrder) {
            val polygon = projectedPolygons[index] ?: continue
            val tile = tileMap.tileList[index]
            val center = projectedCenters[index]
            val color = if (!projectedTileExplored[index]) {
                unexploredTileColor.cpy()
            } else {
                GlobeRenderStateAdapter.tileFillColor(tile, tileSetStrings.tileSetConfig.useColorAsBaseTerrain)
            }
            if (projectedTileExplored[index] && !projectedTileVisible[index]) {
                color.lerp(tileSetStrings.tileSetConfig.fogOfWarColor.cpy(), 0.6f)
            }
            if (index == selectedTileIndex && projectedTileExplored[index]) {
                color.lerp(Color.WHITE, 0.26f)
            }
            shapeRenderer.color = color

            val vertexCount = polygon.size / 2
            for (i in 0 until vertexCount) {
                val next = (i + 1) % vertexCount
                val ax = polygon[i * 2]
                val ay = polygon[i * 2 + 1]
                val bx = polygon[next * 2]
                val by = polygon[next * 2 + 1]
                shapeRenderer.triangle(center.x, center.y, ax, ay, bx, by)
            }
        }

        shapeRenderer.end()
    }

    private fun drawBorders() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

        val gridColor = Color(0.07f, 0.09f, 0.12f, 0.1f)

        for (index in drawOrder) {
            if (!projectedTileExplored[index]) continue
            val polygon = projectedPolygons[index] ?: continue
            val vertexCount = polygon.size / 2
            val alphaScale = GlobeOverlayLodPolicy.gridLineAlphaScale(projectedFacing[index])
            if (alphaScale <= 0.01f) continue

            shapeRenderer.color.set(
                gridColor.r,
                gridColor.g,
                gridColor.b,
                gridColor.a * alphaScale
            )
            for (i in 0 until vertexCount) {
                val next = (i + 1) % vertexCount
                shapeRenderer.line(
                    polygon[i * 2],
                    polygon[i * 2 + 1],
                    polygon[next * 2],
                    polygon[next * 2 + 1]
                )
            }
        }

        shapeRenderer.end()
    }

    private fun drawSelectedUnitReachableOverlay(selectedUnit: MapUnit?) {
        if (selectedUnit == null || !selectedUnit.hasTile()) return
        val movementScope = selectedUnit.movement.getDistanceToTiles()
        if (movementScope.isEmpty()) return

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (index in drawOrder) {
            if (!projectedTileVisible[index]) continue
            val tile = tileMap.tileList[index]
            if (tile == selectedUnit.currentTile) continue
            if (!movementScope.containsKey(tile)) continue
            val polygon = projectedPolygons[index] ?: continue
            val center = projectedCenters[index]
            val alpha = GlobeOverlayLodPolicy.overlayAlpha(
                frameWidth = 18f,
                frameHeight = 18f,
                facingDotCamera = projectedFacing[index]
            ) * 0.22f
            if (alpha <= 0.01f) continue
            shapeRenderer.color.set(0.92f, 0.98f, 1f, alpha)

            val vertexCount = polygon.size / 2
            for (i in 0 until vertexCount) {
                val next = (i + 1) % vertexCount
                shapeRenderer.triangle(
                    center.x,
                    center.y,
                    polygon[i * 2],
                    polygon[i * 2 + 1],
                    polygon[next * 2],
                    polygon[next * 2 + 1]
                )
            }
        }
        shapeRenderer.end()
    }

    private fun drawSelectedUnitPathPreview(selectedUnit: MapUnit?) {
        if (selectedUnit == null || !selectedUnit.hasTile()) return
        if (hoveredTileIndex !in tileMap.tileList.indices) return
        val targetTile = tileMap.tileList[hoveredTileIndex]
        val movementScope = selectedUnit.movement.getDistanceToTiles()
        if (!movementScope.containsKey(targetTile)) return
        val path = movementScope.getPathToTile(targetTile)
        if (path.isEmpty()) return

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.98f, 1f, 1f, 0.9f)
        var previousCenter = projectedCenters[selectedUnit.currentTile.zeroBasedIndex]
        for (tile in path) {
            val currentCenter = projectedCenters[tile.zeroBasedIndex]
            shapeRenderer.line(previousCenter.x, previousCenter.y, currentCenter.x, currentCenter.y)
            previousCenter = currentCenter
        }
        shapeRenderer.end()
    }

    private fun drawOwnershipBorderSprites(batch: Batch, parentAlpha: Float) {
        val whiteDot = getRegion(ImageGetter.whiteDotLocation) ?: return
        val previousColor = Color(batch.color)

        for (index in drawOrder) {
            if (!projectedTileExplored[index]) continue
            val tile = tileMap.tileList[index]
            val owner = tile.getOwner() ?: continue
            val ownerColor = GlobeRenderStateAdapter.borderColor(tile) ?: owner.nation.getInnerColor()
            val polygon = projectedPolygons[index] ?: continue
            val alphaScale = GlobeOverlayLodPolicy.gridLineAlphaScale(projectedFacing[index])
            if (alphaScale <= 0.01f) continue
            val vertexCount = polygon.size / 2
            val ring = cache.neighborRings[index]

            for (neighborRingIndex in ring.indices) {
                val neighborIndex = ring[neighborRingIndex]
                if (!projectedVisible[neighborIndex]) continue
                val neighborOwner = tileMap.tileList[neighborIndex].getOwner()
                if (neighborOwner == owner) continue

                val startCorner = (neighborRingIndex - 1 + vertexCount) % vertexCount
                val endCorner = neighborRingIndex
                val startX = polygon[startCorner * 2]
                val startY = polygon[startCorner * 2 + 1]
                val endX = polygon[endCorner * 2]
                val endY = polygon[endCorner * 2 + 1]
                val dx = endX - startX
                val dy = endY - startY
                val length = hypot(dx, dy)
                if (length <= 0.001f) continue
                val thickness = max(0.95f, min(2.8f, length * 0.11f))
                val angle = (atan2(dy, dx) * 180f / Math.PI).toFloat()
                batch.color.set(
                    ownerColor.r,
                    ownerColor.g,
                    ownerColor.b,
                    parentAlpha * alphaScale * 0.92f
                )
                drawRotatedRegion(
                    batch = batch,
                    region = whiteDot,
                    x = startX,
                    y = startY - thickness / 2f,
                    width = length,
                    height = thickness,
                    rotation = angle
                )
            }
        }

        batch.color = previousColor
    }

    private fun drawSpriteMarkers(batch: Batch, parentAlpha: Float) {
        val previousColor = Color(batch.color)
        val viewingCiv = visibilityContextProvider().viewingCiv
        val showResourceAndImprovementIcons = viewingCiv == null || UncivGame.Current.settings.showResourcesAndImprovements
        val showTileYields = viewingCiv != null && UncivGame.Current.settings.showTileYields

        for (index in drawOrder) {
            if (!projectedTileVisible[index]) continue
            val tile = tileMap.tileList[index]
            val polygon = projectedPolygons[index] ?: continue
            val center = projectedCenters[index]
            val rotation = GlobeOverlaySpritePolicy.overlayRotationDegrees(projectedOverlayRotations[index])
            val frame = GlobeOverlayFramePolicy.fromPolygon(center, polygon, rotation)
            val detailSize = min(frame.width, frame.height)
            val lodAlpha = GlobeOverlayLodPolicy.overlayAlpha(
                frameWidth = frame.width,
                frameHeight = frame.height,
                facingDotCamera = projectedFacing[index]
            )
            if (lodAlpha <= 0.01f) continue

            val markerAlpha = parentAlpha * lodAlpha
            if (showTileYields) {
                drawYieldMarkers(batch, tile, center, detailSize, markerAlpha, viewingCiv)
            }
            if (showResourceAndImprovementIcons) {
                drawResourceMarker(batch, tile, center, detailSize, markerAlpha, viewingCiv)
                drawImprovementMarker(batch, tile, center, detailSize, markerAlpha, viewingCiv)
            }
            drawCityMarker(batch, tile, center, detailSize, markerAlpha)
            drawUnitMarkers(batch, tile, center, detailSize, markerAlpha, viewingCiv)
        }

        batch.color = previousColor
    }

    private fun drawYieldMarkers(
        batch: Batch,
        tile: Tile,
        center: Vector2,
        detailSize: Float,
        alpha: Float,
        viewingCiv: Civilization
    ) {
        if (detailSize < 18f) return
        val yieldIcons = GlobeYieldOverlayPolicy.resolve(tile.stats.getTileStats(viewingCiv), maxIcons = 3)
        if (yieldIcons.isEmpty()) return
        val iconSize = detailSize * 0.15f
        val spacing = detailSize * 0.17f
        val baseY = center.y - detailSize * 0.30f
        val startX = center.x - spacing * (yieldIcons.size - 1) / 2f

        for ((index, icon) in yieldIcons.withIndex()) {
            val region = getRegion(icon.iconLocation) ?: continue
            drawCenteredRegion(
                batch = batch,
                region = region,
                centerX = startX + index * spacing,
                centerY = baseY,
                width = iconSize,
                height = iconSize,
                color = Color.WHITE,
                alpha = alpha
            )
        }
    }

    private fun drawResourceMarker(
        batch: Batch,
        tile: Tile,
        center: Vector2,
        detailSize: Float,
        alpha: Float,
        viewingCiv: Civilization?
    ) {
        val resourceName = tile.resource ?: return
        if (viewingCiv != null && !viewingCiv.canSeeResource(tile.tileResource)) return
        val iconLocation = GlobeSpriteOverlayResolver.resourceIconLocation(resourceName) ?: return
        val iconRegion = getRegion(iconLocation) ?: return
        val circleRegion = getRegion("ResourceIcons/Circle") ?: getRegion(ImageGetter.circleLocation)
        val bgSize = detailSize * 0.36f
        val iconSize = detailSize * 0.22f
        val x = center.x - detailSize * 0.25f
        val y = center.y + detailSize * 0.20f
        val bgColor = tile.tileResource?.resourceType?.getColor() ?: Color(0.14f, 0.14f, 0.14f, 1f)

        if (circleRegion != null) {
            drawCenteredRegion(batch, circleRegion, x, y, bgSize, bgSize, bgColor, alpha)
        }
        drawCenteredRegion(batch, iconRegion, x, y, iconSize, iconSize, ImageGetter.CHARCOAL, alpha)
    }

    private fun drawImprovementMarker(
        batch: Batch,
        tile: Tile,
        center: Vector2,
        detailSize: Float,
        alpha: Float,
        viewingCiv: Civilization?
    ) {
        val shownImprovement = tile.getShownImprovement(viewingCiv) ?: return
        val iconLocation = GlobeSpriteOverlayResolver.improvementIconLocation(shownImprovement) ?: return
        val iconRegion = getRegion(iconLocation) ?: return
        val circleRegion = getRegion("ImprovementIcons/Circle") ?: getRegion(ImageGetter.circleLocation) ?: return
        val bgSize = detailSize * 0.36f
        val iconSize = detailSize * 0.22f
        val x = center.x + detailSize * 0.25f
        val y = center.y + detailSize * 0.20f
        val bgColor = if (tile.improvementIsPillaged) Color(0.55f, 0.18f, 0.14f, 1f)
        else Color(0.20f, 0.22f, 0.24f, 1f)

        drawCenteredRegion(batch, circleRegion, x, y, bgSize, bgSize, bgColor, alpha)
        drawCenteredRegion(batch, iconRegion, x, y, iconSize, iconSize, ImageGetter.CHARCOAL, alpha)
    }

    private fun drawCityMarker(batch: Batch, tile: Tile, center: Vector2, detailSize: Float, alpha: Float) {
        if (!tile.isCityCenter()) return
        val owner = tile.getOwner()
        val outer = owner?.nation?.getOuterColor() ?: Color.BLACK
        val inner = owner?.nation?.getInnerColor() ?: Color.WHITE
        val circleRegion = getRegion(ImageGetter.circleLocation) ?: return
        val cityIconLocation = GlobeSpriteOverlayResolver.cityIconLocation(owner?.civName)
        val cityIcon = getRegion(cityIconLocation) ?: getRegion("OtherIcons/Star")

        drawCenteredRegion(batch, circleRegion, center.x, center.y, detailSize * 0.44f, detailSize * 0.44f, outer, alpha)
        drawCenteredRegion(batch, circleRegion, center.x, center.y, detailSize * 0.30f, detailSize * 0.30f, inner, alpha)
        if (cityIcon != null) {
            drawCenteredRegion(batch, cityIcon, center.x, center.y, detailSize * 0.18f, detailSize * 0.18f, ImageGetter.CHARCOAL, alpha)
        }
    }

    private fun drawUnitMarkers(
        batch: Batch,
        tile: Tile,
        center: Vector2,
        detailSize: Float,
        alpha: Float,
        viewingCiv: Civilization?
    ) {
        val visuals = mutableListOf<UnitVisual>()
        tile.militaryUnit?.let {
            if (viewingCiv == null || viewingCiv.viewableInvisibleUnitsTiles.contains(tile) || !tile.hasEnemyInvisibleUnit(viewingCiv))
                visuals += UnitVisual(it, 0.18f)
        }
        tile.civilianUnit?.let { visuals += UnitVisual(it, -0.18f) }
        if (visuals.isEmpty()) return

        val circleRegion = getRegion(ImageGetter.circleLocation) ?: return
        for (visual in visuals) {
            val unit = visual.unit
            val unitLocation = GlobeSpriteOverlayResolver.unitIconLocation(
                unitName = unit.name,
                baseUnitName = unit.baseUnit.name,
                unitTypeName = unit.type.name,
                imageExists = { ImageGetter.imageExists(it) }
            )
            val iconRegion = getRegion(unitLocation) ?: continue

            val markerAlpha = if (viewingCiv != null && unit.civ == viewingCiv && !unit.hasMovement()) alpha * 0.65f else alpha
            val outer = unit.civ.nation.getOuterColor()
            val inner = unit.civ.nation.getInnerColor()
            val markerX = center.x
            val markerY = center.y + detailSize * visual.yOffsetFactor
            drawCenteredRegion(batch, circleRegion, markerX, markerY, detailSize * 0.36f, detailSize * 0.36f, outer, markerAlpha)
            drawCenteredRegion(batch, circleRegion, markerX, markerY, detailSize * 0.26f, detailSize * 0.26f, inner, markerAlpha)
            drawCenteredRegion(batch, iconRegion, markerX, markerY, detailSize * 0.17f, detailSize * 0.17f, ImageGetter.CHARCOAL, markerAlpha)
        }
    }

    private fun drawCenteredRegion(
        batch: Batch,
        region: TextureRegion,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        color: Color,
        alpha: Float
    ) {
        batch.color.set(color.r, color.g, color.b, color.a * alpha)
        batch.draw(
            region,
            centerX - width / 2f,
            centerY - height / 2f,
            width,
            height
        )
    }

    private fun getRegion(location: String?): TextureRegion? {
        if (location == null) return null
        return overlayRegionCache.getOrPut(location) { ImageGetter.getDrawableOrNull(location)?.region }
    }

    private fun drawPolygonTileOverlays(parentAlpha: Float) {
        polygonBatch.projectionMatrix = stage.camera.combined
        polygonBatch.begin()
        val visibilityContext = visibilityContextProvider()
        val viewingCiv = visibilityContext.viewingCiv
        for (index in drawOrder) {
            val polygon = projectedPolygons[index] ?: continue
            val tile = tileMap.tileList[index]
            val center = projectedCenters[index]
            val rotation = GlobeOverlaySpritePolicy.overlayRotationDegrees(projectedOverlayRotations[index])
            val directionalRotation = GlobeOverlaySpritePolicy.overlayRotationDegrees(projectedDirectionalOverlayRotations[index])
            val frame = GlobeOverlayFramePolicy.fromPolygon(center, polygon, rotation)
            val detailWidth = frame.width
            val detailHeight = frame.height
            val detailLodAlpha = GlobeOverlayLodPolicy.overlayAlpha(
                frameWidth = detailWidth,
                frameHeight = detailHeight,
                facingDotCamera = projectedFacing[index]
            )
            val baseTerrainLodAlpha = GlobeOverlayLodPolicy.baseTerrainAlpha(
                frameWidth = detailWidth,
                frameHeight = detailHeight,
                facingDotCamera = projectedFacing[index]
            )
            if (max(detailLodAlpha, baseTerrainLodAlpha) <= 0.01f) continue
            if (!projectedTileExplored[index]) {
                drawUnexploredOverlay(
                    tile = tile,
                    polygon = polygon,
                    center = center,
                    rotation = rotation,
                    parentAlpha = parentAlpha
                )
                continue
            }

            val detailLayers = getTerrainDetailLocations(tile)
            for (location in detailLayers) {
                drawPolygonLocationIfExists(
                    GlobeCenterOverlayPolicy.Overlay(location = location),
                    polygon,
                    frame.centerX,
                    frame.centerY,
                    detailWidth,
                    detailHeight,
                    tile,
                    rotation,
                    parentAlpha * detailLodAlpha
                )
            }

            val centerOverlays = getCenterOverlayLocations(tile, viewingCiv)
            for (overlay in centerOverlays) {
                val overlayLodAlpha = if (overlay.isBaseTerrain) baseTerrainLodAlpha else detailLodAlpha
                val overlayRotation = if (overlay.isDirectional) directionalRotation else rotation
                val overlayFrame = GlobeOverlayFramePolicy.frameForOverlay(
                    center = center,
                    polygon = polygon,
                    regularRotationDegrees = rotation,
                    directionalRotationDegrees = directionalRotation,
                    overlay = overlay
                )
                val overlayWidth = overlayFrame.width * overlay.scale
                val overlayHeight = overlayFrame.height * overlay.scale
                drawPolygonLocationIfExists(
                    overlay,
                    polygon,
                    overlayFrame.centerX,
                    overlayFrame.centerY,
                    overlayWidth,
                    overlayHeight,
                    tile,
                    overlayRotation,
                    parentAlpha * overlayLodAlpha
                )
            }

            if (!projectedTileVisible[index]) {
                drawFogOverlay(
                    tile = tile,
                    polygon = polygon,
                    center = center,
                    rotation = rotation,
                    parentAlpha = parentAlpha
                )
            }
        }
        polygonBatch.end()
    }

    private fun drawRoadOverlaySprites(batch: Batch, parentAlpha: Float) {
        val previousColor = Color(batch.color)
        batch.color = Color.WHITE.cpy().apply { a = parentAlpha }
        for (index in drawOrder) {
            val polygon = projectedPolygons[index] ?: continue
            val center = projectedCenters[index]
            val rotation = GlobeOverlaySpritePolicy.overlayRotationDegrees(projectedOverlayRotations[index])
            val frame = GlobeOverlayFramePolicy.fromPolygon(center, polygon, rotation)
            val lodAlpha = GlobeOverlayLodPolicy.overlayAlpha(
                frameWidth = frame.width,
                frameHeight = frame.height,
                facingDotCamera = projectedFacing[index]
            )
            if (lodAlpha <= 0.01f) continue
            batch.color.a = parentAlpha * lodAlpha
            drawRoadOverlays(batch, index, center, min(frame.width, frame.height))
        }
        batch.color = previousColor
    }

    private fun drawRoadOverlays(batch: Batch, index: Int, center: Vector2, detailSize: Float) {
        val tile = tileMap.tileList[index]
        if (!projectedTileVisible[index]) return
        if (tile.roadStatus == RoadStatus.None) return
        val ring = cache.neighborRings[index]
        val roadThickness = max(1.6f, detailSize * 0.12f)

        for (neighborIndex in ring) {
            if (!projectedVisible[neighborIndex]) continue
            val neighbor = tileMap.tileList[neighborIndex]
            val roadStatus = GlobeTileOverlayResolver.resolveRoadStatus(tile.roadStatus, neighbor.roadStatus)
            if (roadStatus == RoadStatus.None) continue

            val location = tileSetStrings.orFallback { roadsMap[roadStatus]!! }
            val drawable = ImageGetter.getDrawableOrNull(location) ?: continue
            val neighborCenter = projectedCenters[neighborIndex]

            val dx = neighborCenter.x - center.x
            val dy = neighborCenter.y - center.y
            val length = hypot(dx, dy)
            if (length <= 0.001f) continue

            val roadLength = min(detailSize * 0.58f, length * 0.52f)
            val angle = (atan2(dy, dx) * 180f / Math.PI).toFloat()
            drawRotatedRegion(
                batch = batch,
                region = drawable.region,
                x = center.x,
                y = center.y - roadThickness / 2f,
                width = roadLength,
                height = roadThickness,
                rotation = angle
            )
        }
    }

    private fun drawUnexploredOverlay(
        tile: Tile,
        polygon: FloatArray,
        center: Vector2,
        rotation: Float,
        parentAlpha: Float
    ) {
        if (!ImageGetter.imageExists(tileSetStrings.unexploredTile)) return
        val frame = GlobeOverlayFramePolicy.fromPolygon(center, polygon, rotation)
        drawPolygonLocationIfExists(
            overlay = GlobeCenterOverlayPolicy.Overlay(
                location = tileSetStrings.unexploredTile,
                isBaseTerrain = true
            ),
            polygon = polygon,
            frameCenterX = frame.centerX,
            frameCenterY = frame.centerY,
            frameWidth = frame.width,
            frameHeight = frame.height,
            tile = tile,
            rotation = rotation,
            parentAlpha = parentAlpha
        )
    }

    private fun drawFogOverlay(
        tile: Tile,
        polygon: FloatArray,
        center: Vector2,
        rotation: Float,
        parentAlpha: Float
    ) {
        if (!ImageGetter.imageExists(tileSetStrings.crosshatchHexagon)) return
        val frame = GlobeOverlayFramePolicy.fromPolygon(center, polygon, rotation)
        drawPolygonLocationIfExists(
            overlay = GlobeCenterOverlayPolicy.Overlay(
                location = tileSetStrings.crosshatchHexagon,
                alpha = 0.2f
            ),
            polygon = polygon,
            frameCenterX = frame.centerX,
            frameCenterY = frame.centerY,
            frameWidth = frame.width,
            frameHeight = frame.height,
            tile = tile,
            rotation = rotation,
            parentAlpha = parentAlpha
        )
    }

    private fun drawRotatedRegion(
        batch: Batch,
        region: TextureRegion,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rotation: Float
    ) {
        batch.draw(
            region,
            x,
            y,
            0f,
            height / 2f,
            width,
            height,
            1f,
            1f,
            rotation
        )
    }

    private fun drawPolygonLocationIfExists(
        overlay: GlobeCenterOverlayPolicy.Overlay,
        polygon: FloatArray,
        frameCenterX: Float,
        frameCenterY: Float,
        frameWidth: Float,
        frameHeight: Float,
        tile: Tile,
        rotation: Float,
        parentAlpha: Float
    ) {
        val location = chooseVariant(overlay.location, tile) ?: return
        val region = ImageGetter.getDrawable(location).region
        val vertexCount = polygon.size / 2
        val triangles = overlayTrianglesByVertexCount.getOrPut(vertexCount) {
            GlobeOverlayPolygonMapping.triangleFan(vertexCount)
        }
        val packedColor = Color.toFloatBits(1f, 1f, 1f, parentAlpha * overlay.alpha)

        val texture = region.texture
        // Atlas tiles have anti-aliased transparent edges; inset only where needed.
        // Directional overlays (rivers/edge strips) must keep border texels so they reach tile edges.
        val insetTexels = GlobeOverlaySpritePolicy.textureInsetTexels(overlay)
        val uInset = insetTexels / texture.width.toFloat()
        val vInset = insetTexels / texture.height.toFloat()
        val uWindow = GlobeOverlaySpritePolicy.horizontalUvWindow(region.u, region.u2, uInset)
        val vWindow = GlobeOverlaySpritePolicy.verticalUvWindow(region.v, region.v2, vInset)
        val uStart = uWindow.start
        val uEnd = uWindow.end
        val vStart = vWindow.start
        val vEnd = vWindow.end

        val mappedVertices = FloatArray(vertexCount * 5)
        var out = 0
        for (i in 0 until vertexCount) {
            val x = polygon[i * 2]
            val y = polygon[i * 2 + 1]
            val uv = GlobeOverlayPolygonMapping.uvForPoint(
                pointX = x,
                pointY = y,
                frameCenterX = frameCenterX,
                frameCenterY = frameCenterY,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                rotationDegrees = rotation
            )
            mappedVertices[out++] = x
            mappedVertices[out++] = y
            mappedVertices[out++] = packedColor
            mappedVertices[out++] = uStart + (uEnd - uStart) * uv.x
            mappedVertices[out++] = vStart + (vEnd - vStart) * uv.y
        }

        polygonBatch.draw(
            texture,
            mappedVertices,
            0,
            mappedVertices.size,
            triangles,
            0,
            triangles.size
        )
    }

    private fun chooseVariant(baseLocation: String, tile: Tile): String? {
        if (!ImageGetter.imageExists(baseLocation)) return null
        val available = ArrayList<String>()
        available += baseLocation
        var i = 2
        while (ImageGetter.imageExists("$baseLocation$i")) {
            available += "$baseLocation$i"
            i++
        }
        return available.random(Random(tile.position.hashCode() + baseLocation.hashCode()))
    }

    private fun getCenterOverlayLocations(
        tile: Tile,
        viewingCiv: com.unciv.logic.civilization.Civilization?
    ): List<GlobeCenterOverlayPolicy.Overlay> {
        val shownImprovement = tile.getShownImprovement(viewingCiv)
        val borderOverlays = getBorderOverlayLocations(tile)
        val directionalOverlays = borderOverlays.toHashSet()
        val canSeeResource = viewingCiv == null || viewingCiv.canSeeResource(tile.tileResource)
        val fullLayers = GlobeTileOverlayResolver.resolveTerrainLayerLocations(
            baseTerrain = tile.baseTerrain,
            terrainFeatures = tile.terrainFeatures,
            naturalWonder = tile.naturalWonder,
            shownImprovement = shownImprovement,
            improvementIsPillaged = tile.improvementIsPillaged,
            resource = tile.resource,
            showPixelImprovements = UncivGame.Current.settings.showPixelImprovements,
            canSeeResource = canSeeResource,
            useColorAsBaseTerrain = tileSetStrings.tileSetConfig.useColorAsBaseTerrain,
            useSummaryImages = tileSetStrings.tileSetConfig.useSummaryImages,
            hexagonLocation = tileSetStrings.hexagon,
            naturalWonderSummaryLocation = tileSetStrings.naturalWonder,
            edgeLocations = borderOverlays.asSequence(),
            getTile = { key -> tileSetStrings.getTile(key) },
            orFallback = { key -> tileSetStrings.orFallback { getTile(key) } },
            imageExists = { path -> ImageGetter.imageExists(path) },
            ruleVariants = tileSetStrings.tileSetConfig.ruleVariants
        )

        val hexagon = tileSetStrings.hexagon
        val baseTerrainTiles = setOf(
            tileSetStrings.getTile(tile.baseTerrain),
            tileSetStrings.orFallback { getTile(tile.baseTerrain) }
        )

        return GlobeCenterOverlayPolicy.classify(fullLayers, hexagon, baseTerrainTiles).map { overlay ->
            if (overlay.location in directionalOverlays) overlay.copy(isDirectional = true)
            else overlay
        }
    }

    private fun getBorderOverlayLocations(tile: Tile): List<String> {
        if (!tile.hasTileMap()) return emptyList()
        val neighborContexts = tile.neighbors.asSequence().map { neighbor ->
            val clockPosition = tile.tileMap.getNeighborTileClockPosition(tile, neighbor)
            GlobeTileOverlayResolver.NeighborEdgeContext(
                direction = NeighborDirection.byClockPosition[clockPosition],
                terrainNames = neighbor.cachedTerrainData.terrainNameSet,
                baseTerrainTypeName = neighbor.getBaseTerrain().type.name
            )
        }

        return GlobeTileOverlayResolver.resolveBorderOverlayLocations(
            originTerrainNames = tile.cachedTerrainData.terrainNameSet,
            originBaseTerrainTypeName = tile.getBaseTerrain().type.name,
            neighbors = neighborContexts,
            edgeImagesByPosition = tileSetStrings.edgeImagesByPosition,
            hasBottomRightRiver = tile.hasBottomRightRiver,
            hasBottomRiver = tile.hasBottomRiver,
            hasBottomLeftRiver = tile.hasBottomLeftRiver,
            bottomRightRiverLocation = tileSetStrings.bottomRightRiver,
            bottomRiverLocation = tileSetStrings.bottomRiver,
            bottomLeftRiverLocation = tileSetStrings.bottomLeftRiver
        )
    }

    private fun resolveDirectionalBaseRotation(
        index: Int,
        fallbackRotation: Float,
        polygon: FloatArray
    ): Float {
        val tile = tileMap.tileList[index]
        if (!tile.hasTileMap()) return fallbackRotation
        val center = projectedCenters[index]

        val samples = ArrayList<GlobeDirectionalOverlayOrientationPolicy.DirectionSample>(6)
        for (direction in NeighborDirection.entries) {
            val neighbor = tile.tileMap.getClockPositionNeighborTile(tile, direction.clockPosition) ?: continue
            val neighborPoint = if (projectedVisible[neighbor.zeroBasedIndex]) {
                projectedCenters[neighbor.zeroBasedIndex].cpy()
            } else {
                val neighborWorld = IcosaProjection.projectToSphere(cache.centers[neighbor.zeroBasedIndex], 1f)
                projectToStage(neighborWorld, Vector2())
            }

            val expectedDirection = tile.tileMap.getNeighborTilePositionAsWorldCoords(tile, neighbor)
            if (expectedDirection.isZero(1e-6f)) continue
            val expectedRotation = GlobeOverlayOrientation.screenRotationDegrees(Vector2.Zero, expectedDirection)

            samples += GlobeDirectionalOverlayOrientationPolicy.DirectionSample(
                projectedNeighborPoint = neighborPoint,
                expectedNeighborRotationDegrees = expectedRotation
            )
        }

        val directionalReference = GlobeDirectionalOverlayOrientationPolicy.rotationFromDirectionSamples(
            center = center,
            samples = samples
        ) ?: fallbackRotation

        return GlobeOverlayOrientation.screenRotationFromPolygonNearestTo(
            center = center,
            polygon = polygon,
            referenceRotationDegrees = directionalReference
        )
    }

    private fun getTerrainDetailLocations(tile: Tile): List<String> {
        return GlobeTerrainDetailResolver.resolveLocations(
            baseTerrain = tile.baseTerrain,
            terrainFeatures = tile.terrainFeatures,
            naturalWonder = tile.naturalWonder,
            getTile = { key -> tileSetStrings.getTile(key) },
            orFallback = { key -> tileSetStrings.orFallback { getTile(key) } },
            imageExists = { path -> ImageGetter.imageExists(path) }
        )
    }

    private fun projectToStage(world: Vector3, out: Vector2): Vector2 {
        tempWorld.set(world)
        tempScreen.set(tempWorld)
        camera.project(tempScreen)
        out.set(tempScreen.x, tempScreen.y)
        return stage.screenToStageCoordinates(out)
    }

    private fun pickTile(stageX: Float, stageY: Float): Tile? {
        for (i in drawOrder.indices.reversed()) {
            val index = drawOrder[i]
            val polygon = projectedPolygons[index] ?: continue
            if (pointInPolygon(stageX, stageY, polygon)) {
                if (!projectedTileExplored[index]) return null
                return tileMap.tileList[index]
            }
        }

        val threshold = max(stage.viewport.worldHeight / 24f, 18f)
        val thresholdSq = threshold * threshold
        var bestIndex = -1
        var bestDistance = Float.MAX_VALUE
        for (index in drawOrder) {
            val center = projectedCenters[index]
            val dx = center.x - stageX
            val dy = center.y - stageY
            val distSq = dx * dx + dy * dy
            if (distSq < bestDistance) {
                bestDistance = distSq
                bestIndex = index
            }
        }
        if (bestIndex >= 0 && bestDistance <= thresholdSq) {
            if (!projectedTileExplored[bestIndex]) return null
            return tileMap.tileList[bestIndex]
        }
        return null
    }

    private fun pointInPolygon(x: Float, y: Float, polygon: FloatArray): Boolean {
        var inside = false
        val count = polygon.size / 2
        var j = count - 1
        for (i in 0 until count) {
            val xi = polygon[i * 2]
            val yi = polygon[i * 2 + 1]
            val xj = polygon[j * 2]
            val yj = polygon[j * 2 + 1]

            val intersects = (yi > y) != (yj > y) &&
                x < (xj - xi) * (y - yi) / ((yj - yi) + 0.00001f) + xi
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }

    override fun dispose() {
        shapeRenderer.dispose()
        polygonBatch.dispose()
    }
}
