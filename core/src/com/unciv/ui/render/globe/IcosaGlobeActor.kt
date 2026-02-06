package com.unciv.ui.render.globe

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Disposable
import com.unciv.logic.map.tile.Tile
import kotlin.math.max

class IcosaGlobeActor(
    private val tileMapProvider: () -> com.unciv.logic.map.TileMap,
    private val onTileClick: (Tile) -> Unit = {}
) : Actor(), Disposable {

    private val shapeRenderer = ShapeRenderer()
    private var tileMap = tileMapProvider()
    private var cache = IcosaMeshRuntimeCache.from(tileMap)

    private val camera = PerspectiveCamera(42f, 1f, 1f)
    private val cameraController = GlobeCameraController(distance = 4.15f)

    private var selectedTileIndex = -1
    private var dragPointer = -1
    private var wasDragging = false
    private var lastX = 0f
    private var lastY = 0f

    private var projectedCenters = Array(tileMap.tileList.size) { Vector2() }
    private var projectedPolygons = arrayOfNulls<FloatArray>(tileMap.tileList.size)
    private var projectedDistances = FloatArray(tileMap.tileList.size)
    private var projectedVisible = BooleanArray(tileMap.tileList.size)
    private val drawOrder = ArrayList<Int>(tileMap.tileList.size)

    private val tempWorld = Vector3()
    private val tempScreen = Vector3()
    private val tempStage = Vector2()
    private val cameraDirectionFromOrigin = Vector3()

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
            }

            override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int) {
                if (pointer != dragPointer) return
                dragPointer = -1
                if (wasDragging) return

                val stageCoords = localToStageCoordinates(Vector2(x, y))
                val tile = pickTile(stageCoords.x, stageCoords.y) ?: return
                selectedTileIndex = tile.zeroBasedIndex
                onTileClick(tile)
            }

            override fun scrolled(event: InputEvent, x: Float, y: Float, amountX: Float, amountY: Float): Boolean {
                cameraController.zoomBy(amountY)
                return true
            }
        })
    }

    fun refreshTileMap() {
        val latestMap = tileMapProvider()
        if (latestMap === tileMap) return
        tileMap = latestMap
        cache = IcosaMeshRuntimeCache.from(tileMap)
        selectedTileIndex = -1

        projectedCenters = Array(tileMap.tileList.size) { Vector2() }
        projectedPolygons = arrayOfNulls<FloatArray>(tileMap.tileList.size)
        projectedDistances = FloatArray(tileMap.tileList.size)
        projectedVisible = BooleanArray(tileMap.tileList.size)
        drawOrder.clear()
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        refreshTileMap()
        projectTiles()

        batch.end()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.projectionMatrix = stage.camera.combined
        drawTileSurface()
        drawBorders()
        drawMarkers()

        batch.begin()
    }

    private fun projectTiles() {
        drawOrder.clear()

        camera.viewportWidth = stage.viewport.screenWidth.toFloat()
        camera.viewportHeight = stage.viewport.screenHeight.toFloat()
        cameraController.applyTo(camera)
        cameraDirectionFromOrigin.set(camera.position).nor()

        for (tile in tileMap.tileList) {
            val index = tile.zeroBasedIndex
            projectedVisible[index] = false
            projectedPolygons[index] = null

            val centerDirection = cache.centers[index]
            if (centerDirection.dot(cameraDirectionFromOrigin) <= 0.05f) continue

            val centerWorld = IcosaProjection.projectToSphere(centerDirection, 1f)
            projectToStage(centerWorld, projectedCenters[index])
            projectedDistances[index] = camera.position.dst2(centerWorld)

            val corners = cache.cornerRings[index]
            val polygon = FloatArray(corners.size * 2)
            for (cornerIndex in corners.indices) {
                val cornerWorld = IcosaProjection.projectToSphere(corners[cornerIndex], 1f)
                projectToStage(cornerWorld, tempStage)
                polygon[cornerIndex * 2] = tempStage.x
                polygon[cornerIndex * 2 + 1] = tempStage.y
            }

            projectedPolygons[index] = polygon
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
            val color = GlobeRenderStateAdapter.tileFillColor(tile)
            if (index == selectedTileIndex) {
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

        val gridColor = Color(0.07f, 0.09f, 0.12f, 0.33f)

        for (index in drawOrder) {
            val polygon = projectedPolygons[index] ?: continue
            val vertexCount = polygon.size / 2

            shapeRenderer.color = gridColor
            for (i in 0 until vertexCount) {
                val next = (i + 1) % vertexCount
                shapeRenderer.line(
                    polygon[i * 2],
                    polygon[i * 2 + 1],
                    polygon[next * 2],
                    polygon[next * 2 + 1]
                )
            }

            val tile = tileMap.tileList[index]
            val owner = tile.getOwner() ?: continue
            val ownerColor = GlobeRenderStateAdapter.borderColor(tile) ?: owner.nation.getInnerColor()
            val ring = cache.neighborRings[index]

            for (neighborRingIndex in ring.indices) {
                val neighborIndex = ring[neighborRingIndex]
                if (!projectedVisible[neighborIndex]) continue

                val neighborOwner = tileMap.tileList[neighborIndex].getOwner()
                if (neighborOwner == owner) continue

                val startCorner = (neighborRingIndex - 1 + vertexCount) % vertexCount
                val endCorner = neighborRingIndex

                shapeRenderer.color = ownerColor
                shapeRenderer.line(
                    polygon[startCorner * 2],
                    polygon[startCorner * 2 + 1],
                    polygon[endCorner * 2],
                    polygon[endCorner * 2 + 1]
                )
            }
        }

        shapeRenderer.end()
    }

    private fun drawMarkers() {
        val markerRadius = max(stage.viewport.worldHeight / 260f, 2.8f)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (index in drawOrder) {
            val tile = tileMap.tileList[index]
            val center = projectedCenters[index]

            if (GlobeRenderStateAdapter.hasResourceMarker(tile)) {
                shapeRenderer.color = Color(0.98f, 0.88f, 0.24f, 0.9f)
                shapeRenderer.circle(center.x, center.y, markerRadius * 0.48f, 10)
            }

            if (GlobeRenderStateAdapter.hasCityMarker(tile)) {
                val owner = tile.getOwner()
                val outer = owner?.nation?.getOuterColor() ?: Color.BLACK
                val inner = owner?.nation?.getInnerColor() ?: Color.WHITE
                shapeRenderer.color = outer
                shapeRenderer.circle(center.x, center.y, markerRadius * 0.95f, 14)
                shapeRenderer.color = inner
                shapeRenderer.circle(center.x, center.y, markerRadius * 0.56f, 14)
            }

            if (GlobeRenderStateAdapter.hasUnitMarker(tile)) {
                shapeRenderer.color = Color.BLACK
                shapeRenderer.circle(center.x + markerRadius * 0.85f, center.y - markerRadius * 0.65f, markerRadius * 0.35f, 8)
            }
        }
        shapeRenderer.end()
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
    }
}
