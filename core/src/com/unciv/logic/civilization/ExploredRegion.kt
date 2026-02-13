package com.unciv.logic.civilization

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.HexCoord
import com.unciv.logic.map.HexMath.getLatitude
import com.unciv.logic.map.HexMath.getLongitude
import com.unciv.logic.map.HexMath.worldFromLatLong
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.TileMap
import com.unciv.ui.components.tilegroups.TileGroupMap
import yairm210.purity.annotations.Readonly
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ExploredRegion : IsPartOfGameInfoSerialization {
    @Transient
    private var worldWrap = false

    @Transient
    private var evenMapWidth = false

    @Transient
    private var rectangularMap = false

    @Transient
    private var mapRadius = 0f

    @Transient
    private val tileRadius = TileGroupMap.groupSize * 0.8f

    @Transient
    private var shouldRecalculateCoords = true

    @Transient
    private var shouldUpdateMinimap = true

    @Transient
    private var tileMap: TileMap? = null

    @Transient
    private var useWorldCoords = false

    @Transient
    private var worldPeriodX = 0f

    @Transient
    private var lastStageMapMaxX = Float.NaN

    @Transient
    private var lastStageMapMaxY = Float.NaN

    // Rectangle for positioning the camera viewport on the minimap
    @Transient
    private val exploredRectangle = Rectangle()

    @Transient
    private var shouldRestrictX = false

    // Top left point of the explored region in stage (x;y) starting from the top left corner
    @Transient
    private var topLeftStage = Vector2()

    // Bottom right point of the explored region in stage (x;y) starting from the top left corner
    @Transient
    private var bottomRightStage = Vector2()

    // Top left point of the explored region in hex (long;lat) from the center of the map
    private var topLeft = Vector2()

    // Bottom right point of the explored region in hex (long;lat) from the center of the map
    private var bottomRight = Vector2()

    // Getters
    @Readonly fun shouldRecalculateCoords(): Boolean = shouldRecalculateCoords
    @Readonly fun shouldUpdateMinimap(): Boolean = shouldUpdateMinimap
    @Readonly fun getRectangle(): Rectangle = exploredRectangle
    @Readonly fun shouldRestrictX(): Boolean = shouldRestrictX
    @Readonly fun getLeftX(): Float = topLeftStage.x
    @Readonly fun getRightX(): Float = bottomRightStage.x
    @Readonly fun getTopY(): Float = topLeftStage.y
    @Readonly fun getBottomY(): Float = bottomRightStage.y

    fun clone(): ExploredRegion {
        val toReturn = ExploredRegion()
        toReturn.topLeft = topLeft
        toReturn.bottomRight = bottomRight
        return toReturn
    }

    fun setMapParameters(mapParameters: MapParameters, tileMap: TileMap? = null, civ: Civilization? = null) {
        this.tileMap = tileMap
        useWorldCoords = mapParameters.shape == MapShape.icosahedron && tileMap != null
        worldPeriodX = if (useWorldCoords && tileMap != null) tileMap.topology.getWorldBounds().width else 0f
        shouldRecalculateCoords = true
        shouldUpdateMinimap = true
        lastStageMapMaxX = Float.NaN
        lastStageMapMaxY = Float.NaN
        this.worldWrap = mapParameters.worldWrap
        evenMapWidth = worldWrap
        rectangularMap = false

        if (mapParameters.shape == MapShape.rectangular) {
            mapRadius = (mapParameters.mapSize.width / 2).toFloat()
            evenMapWidth = mapParameters.mapSize.width % 2 == 0 || evenMapWidth
            rectangularMap = true
        }
        else
            mapRadius = mapParameters.mapSize.radius.toFloat()

        if (useWorldCoords && civ != null) {
            recalculateWorldBoundsFromExploredTiles(civ)
        }
    }

    fun ensureStageCoords(mapMaxX: Float, mapMaxY: Float) {
        val viewportChanged = mapMaxX != lastStageMapMaxX || mapMaxY != lastStageMapMaxY
        if (shouldRecalculateCoords || viewportChanged) {
            calculateStageCoords(mapMaxX, mapMaxY)
        }
    }

    @Readonly
    private fun getExploredCoord(tilePosition: HexCoord): Vector2 {
        val localMap = tileMap
        if (useWorldCoords && localMap != null) {
            val worldPos = localMap.topology.getWorldPosition(localMap[tilePosition])
            return Vector2(worldPos.x, worldPos.y)
        }
        return Vector2(getLongitude(tilePosition).toFloat(), getLatitude(tilePosition).toFloat())
    }

    @Readonly
    fun getWorldBounds(): Rectangle? {
        if (!useWorldCoords) return null
        val minX = min(topLeft.x, bottomRight.x)
        val maxX = max(topLeft.x, bottomRight.x)
        val minY = min(topLeft.y, bottomRight.y)
        val maxY = max(topLeft.y, bottomRight.y)
        return Rectangle(minX, minY, maxX - minX, maxY - minY)
    }

    @Readonly
    fun getWorldCenterX(): Float? {
        if (!useWorldCoords) return null
        if (topLeft == Vector2.Zero && bottomRight == Vector2.Zero) return null
        return (topLeft.x + bottomRight.x) * 0.5f
    }

    @Readonly
    fun unwrapWorldLongitudeForRegion(longitude: Float): Float {
        val anchor = getWorldCenterX() ?: longitude
        return unwrapLongitudeForContinuity(longitude, anchor)
    }

    // Check if tilePosition is beyond explored region
    fun checkTilePosition(tilePosition: HexCoord, explorerPosition: HexCoord?, civ: Civilization? = null) {
        var mapExplored = false
        val coord = getExploredCoord(tilePosition)
        var longitude = coord.x
        val latitude = coord.y

        if (useWorldCoords) {
            val centerX = when {
                explorerPosition != null -> getExploredCoord(explorerPosition).x
                topLeft == Vector2.Zero && bottomRight == Vector2.Zero -> longitude
                else -> (topLeft.x + bottomRight.x) * 0.5f
            }
            longitude = unwrapLongitudeForContinuity(longitude, centerX)
        }

        // First time call
        if (topLeft == Vector2.Zero && bottomRight == Vector2.Zero) {
            topLeft = Vector2(longitude, latitude)
            bottomRight = Vector2(longitude, latitude)
            return
        }

        if (useWorldCoords) {
            if (civ == null) {
                if (longitude > topLeft.x) {
                    topLeft.x = longitude
                    mapExplored = true
                } else if (longitude < bottomRight.x) {
                    bottomRight.x = longitude
                    mapExplored = true
                }

                if (latitude > topLeft.y) {
                    topLeft.y = latitude
                    mapExplored = true
                } else if (latitude < bottomRight.y) {
                    bottomRight.y = latitude
                    mapExplored = true
                }
            } else {
                val prevTopLeft = topLeft.cpy()
                val prevBottomRight = bottomRight.cpy()
                recalculateWorldBoundsFromExploredTiles(civ)
                mapExplored = prevTopLeft != topLeft || prevBottomRight != bottomRight
            }

            if(mapExplored) {
                shouldRecalculateCoords = true
                shouldUpdateMinimap = true
            }
            return
        }

        // Check X coord
        if (topLeft.x >= bottomRight.x) {
            if (longitude > topLeft.x) {
                // For world wrap maps when the maximumX is reached, we move to a minimumX - 1f
                if (worldWrap && longitude == mapRadius) longitude = mapRadius * -1f
                topLeft.x = longitude
                mapExplored = true
            } else if (longitude < bottomRight.x) {
                // For world wrap maps when the minimumX is reached, we move to a maximumX + 1f
                if (worldWrap && longitude == (mapRadius * -1f + 1f)) longitude = mapRadius + 1f
                bottomRight.x = longitude
                mapExplored = true
            }
        } else {
            // When we cross the map edge with world wrap, the vectors are swapped along the x-axis
            if (longitude < bottomRight.x && longitude > topLeft.x) {
                val rightSideDistance: Float
                val leftSideDistance: Float

                // If we have explorerPosition, get distance to explorer
                // This solves situations when a newly explored cell is in the middle of an unexplored area
                if(explorerPosition != null) {
                    val explorerLongitude = getExploredCoord(explorerPosition).x

                    rightSideDistance = if(explorerLongitude < 0 && bottomRight.x > 0)
                            // The explorer is still on the right edge of the map, but has explored over the edge
                            mapRadius * 2f + explorerLongitude - bottomRight.x
                        else
                            abs(explorerLongitude - bottomRight.x)

                    leftSideDistance = if(explorerLongitude > 0 && topLeft.x < 0)
                            // The explorer is still on the left edge of the map, but has explored over the edge
                            mapRadius * 2f - explorerLongitude + topLeft.x
                        else
                            abs(topLeft.x - explorerLongitude)
                } else {
                    // If we don't have explorerPosition, we calculate the distance to the edges of the explored region
                    // e.g. when capitals are revealed
                    rightSideDistance = bottomRight.x - longitude
                    leftSideDistance = longitude - topLeft.x
                }

                // Expand region from the nearest edge
                if (rightSideDistance > leftSideDistance)
                    topLeft.x = longitude
                else
                    bottomRight.x = longitude

                mapExplored = true
            }
        }

        // Check Y coord
        if (latitude > topLeft.y) {
            topLeft.y = latitude
            mapExplored = true
        } else if (latitude < bottomRight.y) {
            bottomRight.y = latitude
            mapExplored = true
        }

        if(mapExplored) {
            shouldRecalculateCoords = true
            shouldUpdateMinimap = true
        }
    }

    fun calculateStageCoords(mapMaxX: Float, mapMaxY: Float) {
        shouldRecalculateCoords = false
        lastStageMapMaxX = mapMaxX
        lastStageMapMaxY = mapMaxY

        // Check if we explored the whole world wrap map horizontally
        if (useWorldCoords && worldPeriodX > 0f) {
            val exploredWidth = topLeft.x - bottomRight.x
            shouldRestrictX = exploredWidth < worldPeriodX - 0.001f
        } else {
            shouldRestrictX = bottomRight.x - topLeft.x != 1f
        }

        // Get world (x;y)
        val topLeftWorld: Vector2
        val bottomRightWorld: Vector2
        if (useWorldCoords) {
            val localMap = tileMap
            val top = Vector2(topLeft).scl(tileRadius)
            val bottom = Vector2(bottomRight).scl(tileRadius)
            if (localMap != null && localMap.mapParameters.shape == MapShape.icosahedron) {
                topLeftWorld = localMap.worldToRenderCoords(top)
                bottomRightWorld = localMap.worldToRenderCoords(bottom)
            } else {
                topLeftWorld = top
                bottomRightWorld = bottom
            }
        } else {
            topLeftWorld = worldFromLatLong(topLeft, tileRadius)
            bottomRightWorld = worldFromLatLong(bottomRight, tileRadius)
        }

        // Convert X to the stage coords
        val mapCenterX = if (evenMapWidth) (mapMaxX + TileGroupMap.groupSize + 4f) * 0.5f else mapMaxX * 0.5f
        var left: Float
        var right: Float
        if (useWorldCoords) {
            val minX = min(topLeftWorld.x, bottomRightWorld.x)
            val maxX = max(topLeftWorld.x, bottomRightWorld.x)
            left = mapCenterX + minX
            right = mapCenterX + maxX
        } else {
            left = mapCenterX + topLeftWorld.x
            right = mapCenterX + bottomRightWorld.x
        }

        // World wrap over edge check
        if (left > mapMaxX) left = 10f
        if (right < 0f) right = mapMaxX - 10f

        // Convert Y to the stage coords
        val mapCenterY = if (rectangularMap) mapMaxY * 0.5f + TileGroupMap.groupSize * 0.25f else mapMaxY * 0.5f
        val top: Float
        val bottom: Float
        if (useWorldCoords) {
            val maxY = max(topLeftWorld.y, bottomRightWorld.y)
            val minY = min(topLeftWorld.y, bottomRightWorld.y)
            top = mapCenterY - maxY
            bottom = mapCenterY - minY
        } else {
            top = mapCenterY - topLeftWorld.y
            bottom = mapCenterY - bottomRightWorld.y
        }

        topLeftStage = Vector2(left, top)
        bottomRightStage = Vector2(right, bottom)

        // Calculate rectangle for positioning the camera viewport on the minimap
        val yOffset = tileRadius * sqrt(3f) * 0.5f
        exploredRectangle.x = left - tileRadius
        exploredRectangle.y = mapMaxY - bottom - yOffset * 0.5f
        if (useWorldCoords) {
            exploredRectangle.width = (right - left) + tileRadius * 2f
            exploredRectangle.height = (bottom - top) + yOffset
        } else {
            exploredRectangle.width = getWidth() * tileRadius * 1.5f
            exploredRectangle.height = getHeight() * yOffset
        }
    }

    @Readonly
    fun isPositionInRegion(postition: HexCoord): Boolean {
        val coord = getExploredCoord(postition)
        val long = if (useWorldCoords)
            unwrapLongitudeForContinuity(coord.x, (topLeft.x + bottomRight.x) * 0.5f)
        else coord.x
        val lat = coord.y
        return if (topLeft.x > bottomRight.x)
                (long <= topLeft.x && long >= bottomRight.x && lat <= topLeft.y && lat >= bottomRight.y)
            else
                (((long >= topLeft.x && long >= bottomRight.x) || (long <= topLeft.x && long <= bottomRight.x)) && lat <= topLeft.y && lat >= bottomRight.y)
    }

    @Readonly
    fun getWidth(): Int {
        if (useWorldCoords) return (topLeft.x - bottomRight.x).toInt() + 1
        val result: Float
        if (topLeft.x > bottomRight.x) result = topLeft.x - bottomRight.x
        else result = mapRadius * 2f - (bottomRight.x - topLeft.x)
        return result.toInt() + 1
    }

    @Readonly fun getHeight(): Int = (topLeft.y - bottomRight.y).toInt() + 1

    fun getMinimapLeft(tileSize: Float): Float {
        shouldUpdateMinimap = false
        return (topLeft.x + 1f) * tileSize * -0.75f
    }

    private fun recalculateWorldBoundsFromExploredTiles(civ: Civilization) {
        val localMap = tileMap ?: return
        val exploredTiles = localMap.values.filter { it.isExplored(civ) }
        if (exploredTiles.isEmpty()) {
            topLeft = Vector2.Zero.cpy()
            bottomRight = Vector2.Zero.cpy()
            shouldRecalculateCoords = true
            shouldUpdateMinimap = true
            return
        }

        val worldCoords = exploredTiles.map { getExploredCoord(it.position) }
        val minY = worldCoords.minOf { it.y }
        val maxY = worldCoords.maxOf { it.y }
        val (minX, maxX) = getMinimalWorldXInterval(worldCoords.map { it.x })

        topLeft = Vector2(maxX, maxY)
        bottomRight = Vector2(minX, minY)
        shouldRecalculateCoords = true
        shouldUpdateMinimap = true
    }

    private fun getMinimalWorldXInterval(values: List<Float>): Pair<Float, Float> {
        if (values.isEmpty()) return 0f to 0f
        if (!useWorldCoords || worldPeriodX <= 0f || values.size == 1) {
            val minX = values.minOrNull() ?: 0f
            val maxX = values.maxOrNull() ?: 0f
            return minX to maxX
        }

        val period = worldPeriodX
        val normalizedSorted = values
            .map { value ->
                var normalized = value % period
                if (normalized < 0f) normalized += period
                normalized
            }
            .sorted()

        val n = normalizedSorted.size
        val doubled = FloatArray(n * 2)
        for (i in 0 until n) {
            doubled[i] = normalizedSorted[i]
            doubled[i + n] = normalizedSorted[i] + period
        }

        var bestStart = doubled[0]
        var bestEnd = doubled[n - 1]
        var bestWidth = bestEnd - bestStart

        for (startIdx in 1 until n) {
            val start = doubled[startIdx]
            val end = doubled[startIdx + n - 1]
            val width = end - start
            if (width < bestWidth) {
                bestWidth = width
                bestStart = start
                bestEnd = end
            }
        }

        return bestStart to bestEnd
    }

    @Readonly
    private fun unwrapLongitudeForContinuity(longitude: Float, anchor: Float): Float {
        if (!useWorldCoords || worldPeriodX <= 0f) return longitude
        var adjusted = longitude
        val halfPeriod = worldPeriodX * 0.5f
        while (adjusted - anchor > halfPeriod) adjusted -= worldPeriodX
        while (adjusted - anchor < -halfPeriod) adjusted += worldPeriodX
        return adjusted
    }
}
