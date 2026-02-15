package com.unciv.logic.map.topology

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly
import kotlin.math.roundToInt
import kotlin.math.sqrt

class GoldbergTopology(private val tileMap: TileMap, frequency: Int, layoutId: String) : MapTopology {

    private val mesh = GoldbergMeshBuilder.build(frequency)
    private val layout = GoldbergNetLayoutBuilder.buildIndexToCoord(frequency, mesh, layoutId)

    private val latitudes: IntArray
    private val longitudes: IntArray
    private val worldPositions: Array<Vector2>
    private val worldBounds: Rectangle
    private val maxEdgeChord = mesh.edgeMaxChord

    private val seamEdges: Array<MutableSet<Int>>
    private val primaryFaceByIndex: IntArray
    private val faceLabelTileByFace: IntArray

    init {
        val tileCount = tileMap.tileList.size
        require(tileCount == mesh.vertices.size) {
            "Goldberg tile count mismatch: tiles=$tileCount mesh=${mesh.vertices.size}"
        }

        latitudes = IntArray(mesh.vertices.size)
        longitudes = IntArray(mesh.vertices.size)
        worldPositions = Array(mesh.vertices.size) { Vector2() }
        seamEdges = Array(mesh.vertices.size) { LinkedHashSet() }
        primaryFaceByIndex = IntArray(mesh.vertices.size)
        faceLabelTileByFace = IntArray(mesh.faces.size) { -1 }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        val orientationBasis = GoldbergNetNorthAxis.buildBasis(mesh, layout.indexToCoord)

        for (i in mesh.vertices.indices) {
            val vec = mesh.vertices[i]
            // Align climate latitude/longitude with the unfolded net orientation
            // (north = top-center tile toward bottom-center tile).
            val lat = GoldbergNetNorthAxis.latitudeDegrees(vec, orientationBasis)
            val lon = GoldbergNetNorthAxis.longitudeDegrees(vec, orientationBasis)
            latitudes[i] = (lat * 1000.0).roundToInt()
            longitudes[i] = (lon * 1000.0).roundToInt()

            val expectedCoord = layout.indexToCoord[i]
            val tile = tileMap.tileList[i]
            if (tile.position != expectedCoord) {
                throw IllegalStateException("Goldberg layout mismatch at index $i: expected $expectedCoord got ${tile.position}")
            }

            val worldPos = HexMath.hex2WorldCoords(expectedCoord)
            worldPositions[i] = worldPos
            if (worldPos.x < minX) minX = worldPos.x
            if (worldPos.y < minY) minY = worldPos.y
            if (worldPos.x > maxX) maxX = worldPos.x
            if (worldPos.y > maxY) maxY = worldPos.y
        }

        worldBounds = Rectangle(minX, minY, maxX - minX, maxY - minY)

        for (i in mesh.neighbors.indices) {
            val pos = layout.indexToCoord[i]
            for (n in mesh.neighbors[i]) {
                val dist = HexMath.getDistance(pos, layout.indexToCoord[n])
                if (dist > 1) {
                    seamEdges[i].add(n)
                }
            }
        }

        for (i in mesh.vertexFaces.indices) {
            val faces = mesh.vertexFaces[i]
            primaryFaceByIndex[i] = faces.minOrNull() ?: -1
        }
        computeFaceLabelTiles()
    }

    override fun getNeighbors(tile: Tile): Sequence<Tile> = sequence {
        val idx = tile.zeroBasedIndex
        @LocalState val neighborIndices = mesh.neighbors[idx]
        for (i in neighborIndices.indices) {
            val n = neighborIndices[i]
            yield(tileMap.tileList[n])
        }
    }

    override fun getTilesAtDistance(origin: Tile, distance: Int): Sequence<Tile> {
        if (distance <= 0) return sequenceOf(origin)
        val ring = bfsRing(origin.zeroBasedIndex, distance)
        return ring.asSequence().map { tileMap.tileList[it] }
    }

    override fun getTilesInDistanceRange(origin: Tile, range: IntRange): Sequence<Tile> =
        range.asSequence().flatMap { getTilesAtDistance(origin, it) }

    @Readonly
    override fun getDistance(from: Tile, to: Tile): Int = bfsDistance(from.zeroBasedIndex, to.zeroBasedIndex)

    override fun getHeuristicDistance(from: Tile, to: Tile): Float {
        val a = mesh.vertices[from.zeroBasedIndex]
        val b = mesh.vertices[to.zeroBasedIndex]
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        val chord = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        return if (maxEdgeChord <= 0f) 0f else chord / maxEdgeChord
    }

    override fun getLatitude(tile: Tile): Int {
        @LocalState val local = latitudes
        return local[tile.zeroBasedIndex]
    }

    override fun getLongitude(tile: Tile): Int {
        @LocalState val local = longitudes
        return local[tile.zeroBasedIndex]
    }

    override fun getWorldPosition(tile: Tile): Vector2 = worldPositions[tile.zeroBasedIndex]

    override fun getWorldBounds(): Rectangle = worldBounds

    override fun isEdge(tile: Tile): Boolean = false

    override fun edgeUniqueIndex(tile: Tile, neighbor: Tile): Int {
        val idx = tile.zeroBasedIndex
        val neighborIdx = neighbor.zeroBasedIndex
        @LocalState val list = mesh.neighbors[idx]
        var localIndex = -1
        for (i in list.indices) {
            if (list[i] == neighborIdx) { localIndex = i; break }
        }
        if (localIndex < 0) return idx * 6
        return idx * 6 + localIndex
    }

    override fun isSeamEdge(from: Tile, to: Tile): Boolean {
        val idx = from.zeroBasedIndex
        return seamEdges[idx].contains(to.zeroBasedIndex)
    }

    override fun getClosestTile(worldPos: Vector2): Tile? {
        var bestIndex = -1
        var bestDist = Float.MAX_VALUE
        for (i in worldPositions.indices) {
            val pos = worldPositions[i]
            val dx = pos.x - worldPos.x
            val dy = pos.y - worldPos.y
            val dist = dx * dx + dy * dy
            if (dist < bestDist) {
                bestDist = dist
                bestIndex = i
            }
        }
        return if (bestIndex >= 0) tileMap.tileList[bestIndex] else null
    }

    @Readonly
    fun getFaceMembershipForDebug(tile: Tile): IntArray = mesh.vertexFaces[tile.zeroBasedIndex]

    @Readonly
    fun getPrimaryFaceForDebug(tile: Tile): Int {
        @LocalState val local = primaryFaceByIndex
        return local[tile.zeroBasedIndex]
    }

    @Readonly
    fun isFaceBoundaryForDebug(from: Tile, to: Tile): Boolean =
        getPrimaryFaceForDebug(from) != getPrimaryFaceForDebug(to)

    @Readonly
    fun getFaceLabelTileForDebug(face: Int): Int {
        @LocalState val local = faceLabelTileByFace
        return if (face in local.indices) local[face] else -1
    }

    @Readonly
    private fun bfsDistance(start: Int, target: Int): Int {
        if (start == target) return 0
        val size = mesh.neighbors.size
        @LocalState val visited = BooleanArray(size)
        @LocalState val distances = IntArray(size)
        @LocalState val queue = IntArray(size)
        var head = 0
        var tail = 0
        queue[tail++] = start
        visited[start] = true
        while (head < tail) {
            val idx = queue[head++]
            val dist = distances[idx]
            @LocalState val neighbors = mesh.neighbors[idx]
            for (i in neighbors.indices) {
                val n = neighbors[i]
                if (visited[n]) continue
                if (n == target) return dist + 1
                visited[n] = true
                distances[n] = dist + 1
                queue[tail++] = n
            }
        }
        return Int.MAX_VALUE
    }

    @Readonly
    private fun bfsRing(start: Int, distance: Int): List<Int> {
        val size = mesh.neighbors.size
        @LocalState val visited = BooleanArray(size)
        @LocalState val distances = IntArray(size)
        @LocalState val queue = IntArray(size)
        var head = 0
        var tail = 0
        queue[tail++] = start
        visited[start] = true
        val ring = ArrayList<Int>()
        while (head < tail) {
            val idx = queue[head++]
            val dist = distances[idx]
            if (dist == distance) {
                ring.add(idx)
                continue
            }
            if (dist > distance) continue
            @LocalState val neighbors = mesh.neighbors[idx]
            for (i in neighbors.indices) {
                val n = neighbors[i]
                if (visited[n]) continue
                visited[n] = true
                distances[n] = dist + 1
                queue[tail++] = n
            }
        }
        return ring
    }

    private fun computeFaceLabelTiles() {
        for (face in mesh.faces.indices) {
            val candidates = ArrayList<Int>()
            for (index in primaryFaceByIndex.indices) {
                if (primaryFaceByIndex[index] == face) candidates.add(index)
            }
            if (candidates.isEmpty()) continue

            var centerX = 0f
            var centerY = 0f
            for (index in candidates) {
                val pos = worldPositions[index]
                centerX += pos.x
                centerY += pos.y
            }
            centerX /= candidates.size
            centerY /= candidates.size

            var bestIndex = candidates.first()
            var bestSameFaceNeighbors = -1
            var bestCenterDist = Float.MAX_VALUE
            for (index in candidates) {
                var sameFaceNeighbors = 0
                for (neighbor in mesh.neighbors[index]) {
                    if (primaryFaceByIndex[neighbor] == face) sameFaceNeighbors++
                }
                val pos = worldPositions[index]
                val dx = pos.x - centerX
                val dy = pos.y - centerY
                val centerDist = dx * dx + dy * dy
                if (sameFaceNeighbors > bestSameFaceNeighbors ||
                    (sameFaceNeighbors == bestSameFaceNeighbors && centerDist < bestCenterDist) ||
                    (sameFaceNeighbors == bestSameFaceNeighbors && centerDist == bestCenterDist && index < bestIndex)
                ) {
                    bestIndex = index
                    bestSameFaceNeighbors = sameFaceNeighbors
                    bestCenterDist = centerDist
                }
            }
            faceLabelTileByFace[face] = bestIndex
        }
    }
}
