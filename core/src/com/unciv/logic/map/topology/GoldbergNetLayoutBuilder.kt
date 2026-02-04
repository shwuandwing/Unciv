package com.unciv.logic.map.topology

import com.unciv.logic.map.HexCoord

object GoldbergNetLayoutBuilder {

    const val DEFAULT_LAYOUT = "IcosaNetV2"

    data class LayoutResult(
        val indexToCoord: List<HexCoord>,
        val faceOrder: List<Int>
    )

    private data class FaceAdjacency(val otherFace: Int, val shared: IntArray)

    fun buildIndexToCoord(
        frequency: Int,
        mesh: GoldbergMeshBuilder.GoldbergMesh,
        layoutId: String = DEFAULT_LAYOUT
    ): LayoutResult {
        require(layoutId == DEFAULT_LAYOUT) { "Unsupported Goldberg layout '$layoutId'" }
        val faces = mesh.faces

        val northVertex = 0
        val southVertex = 3

        val northFaces = faces.indices.filter { faces[it].contains(northVertex) }
        val southFaces = faces.indices.filter { faces[it].contains(southVertex) }
        val bandFaces = faces.indices.filter { it !in northFaces && it !in southFaces }

        val adjacency = buildAdjacency(faces)

        val bandCycle = buildBandCycle(bandFaces, adjacency)
        val bandChainEdges = bandCycle.zipWithNext().mapNotNull { (a, b) ->
            adjacency[a].firstOrNull { it.otherFace == b }?.let { edge -> Triple(a, b, edge.shared) }
        }

        val faceCornerCoords = Array(faces.size) { mutableMapOf<Int, HexCoord>() }
        val placed = BooleanArray(faces.size)

        val baseDir1 = HexCoord.of(1, 0)
        val baseDir2 = HexCoord.of(0, 1)

        fun placeBaseFace(faceIndex: Int, origin: HexCoord) {
            val face = faces[faceIndex]
            faceCornerCoords[faceIndex][face[0]] = origin
            faceCornerCoords[faceIndex][face[1]] = origin.plus(baseDir1)
            faceCornerCoords[faceIndex][face[2]] = origin.plus(baseDir2)
            placed[faceIndex] = true
        }

        fun getOppositeVertex(face: IntArray, shared: IntArray): Int {
            for (v in face) if (v != shared[0] && v != shared[1]) return v
            throw IllegalStateException("Shared vertices do not match face")
        }

        fun placeNeighbor(fromFace: Int, toFace: Int, shared: IntArray) {
            val fromCoords = faceCornerCoords[fromFace]
            val toCoords = faceCornerCoords[toFace]
            val fromOpp = getOppositeVertex(faces[fromFace], shared)
            val toOpp = getOppositeVertex(faces[toFace], shared)
            val coordSharedA = fromCoords[shared[0]]!!
            val coordSharedB = fromCoords[shared[1]]!!
            val coordOppFrom = fromCoords[fromOpp]!!
            val coordOppTo = coordSharedA.plus(coordSharedB).minus(coordOppFrom)

            if (toCoords.isEmpty()) {
                toCoords[shared[0]] = coordSharedA
                toCoords[shared[1]] = coordSharedB
                toCoords[toOpp] = coordOppTo
                placed[toFace] = true
                return
            }

            val existingA = toCoords[shared[0]]
            val existingB = toCoords[shared[1]]
            if (existingA != null && existingA != coordSharedA) throw IllegalStateException("Inconsistent face placement")
            if (existingB != null && existingB != coordSharedB) throw IllegalStateException("Inconsistent face placement")
        }

        fun placeComponent(
            facesInComponent: Set<Int>,
            edges: List<Triple<Int, Int, IntArray>>,
            origin: HexCoord
        ) {
            val baseFace = facesInComponent.minOrNull() ?: return
            placeBaseFace(baseFace, origin)
            var progress = true
            while (progress) {
                progress = false
                for ((a, b, shared) in edges) {
                    if (!facesInComponent.contains(a) || !facesInComponent.contains(b)) continue
                    if (placed[a] && !placed[b]) {
                        placeNeighbor(a, b, shared)
                        progress = true
                    } else if (placed[b] && !placed[a]) {
                        placeNeighbor(b, a, shared)
                        progress = true
                    } else if (placed[a] && placed[b]) {
                        // verify shared vertices match
                        val coordsA = faceCornerCoords[a]
                        val coordsB = faceCornerCoords[b]
                        val sharedA = coordsA[shared[0]]
                        val sharedB = coordsA[shared[1]]
                        val sharedA2 = coordsB[shared[0]]
                        val sharedB2 = coordsB[shared[1]]
                        if (sharedA != null && sharedA2 != null && sharedA != sharedA2) throw IllegalStateException("Inconsistent shared vertex")
                        if (sharedB != null && sharedB2 != null && sharedB != sharedB2) throw IllegalStateException("Inconsistent shared vertex")
                    }
                }
            }
        }

        val bandSet = bandFaces.toSet()
        placeComponent(bandSet, bandChainEdges, HexCoord.Zero)

        for (bandFace in bandCycle) {
            for (adj in adjacency[bandFace]) {
                val other = adj.otherFace
                if (other !in northFaces && other !in southFaces) continue
                if (!placed[other]) {
                    placeNeighbor(bandFace, other, adj.shared)
                } else {
                    placeNeighbor(bandFace, other, adj.shared)
                }
            }
        }

        val remaining = faces.indices.filter { !placed[it] }
        if (remaining.isNotEmpty()) {
            for (face in faces.indices) {
                if (!placed[face]) continue
                for (adj in adjacency[face]) {
                    val other = adj.otherFace
                    if (!placed[other]) {
                        placeNeighbor(face, other, adj.shared)
                    }
                }
            }
        }

        val faceOrder = bandCycle + northFaces.sorted() + southFaces.sorted()

        val indexToCoord = Array<HexCoord?>(mesh.vertices.size) { null }
        val coordToIndex = HashMap<HexCoord, Int>()

        for (faceIndex in faceOrder) {
            val face = faces[faceIndex]
            val faceCoords = faceCornerCoords[faceIndex]
            if (faceCoords.size != 3) throw IllegalStateException("Face coordinates missing")
            val base0 = faceCoords[face[0]]!!
            val base1 = faceCoords[face[1]]!!
            val base2 = faceCoords[face[2]]!!
            val origin = base0.times(frequency)
            val step1 = base1.minus(base0)
            val step2 = base2.minus(base0)

            for (i in 0..frequency) {
                for (j in 0..(frequency - i)) {
                    val k = frequency - i - j
                    val key = keyForVertex(faceIndex, i, j, k, face[0], face[1], face[2], frequency)
                    val idx = mesh.vertexKeyToIndex[key] ?: continue
                    if (indexToCoord[idx] != null) continue
                    val coord = origin.plus(step1.times(j)).plus(step2.times(k))
                    val existing = coordToIndex[coord]
                    if (existing != null && existing != idx) {
                        throw IllegalStateException("Goldberg layout collision at $coord between $existing and $idx")
                    }
                    coordToIndex[coord] = idx
                    indexToCoord[idx] = coord
                }
            }
        }

        val finalCoords = indexToCoord.map { it ?: throw IllegalStateException("Unassigned Goldberg coord") }
        val rotated = finalCoords.map { rotate60CounterClockwise(it) }
        val normalized = normalizeToPositive(rotated)
        return LayoutResult(normalized, faceOrder)
    }

    private fun rotate60CounterClockwise(coord: HexCoord): HexCoord {
        val x = coord.x
        val y = coord.y
        return HexCoord.of(x + y, -x)
    }

    private fun normalizeToPositive(coords: List<HexCoord>): List<HexCoord> {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        for (coord in coords) {
            if (coord.x < minX) minX = coord.x
            if (coord.y < minY) minY = coord.y
        }
        if (minX == 0 && minY == 0) return coords
        val offset = HexCoord.of(-minX, -minY)
        return coords.map { it.plus(offset) }
    }

    private fun buildAdjacency(faces: List<IntArray>): Array<MutableList<FaceAdjacency>> {
        val result = Array(faces.size) { mutableListOf<FaceAdjacency>() }
        for (i in faces.indices) {
            for (j in i + 1 until faces.size) {
                val shared = faces[i].filter { v -> faces[j].contains(v) }
                if (shared.size == 2) {
                    val sharedArray = intArrayOf(shared[0], shared[1])
                    result[i].add(FaceAdjacency(j, sharedArray))
                    result[j].add(FaceAdjacency(i, sharedArray))
                }
            }
        }
        return result
    }

    private fun buildBandCycle(bandFaces: List<Int>, adjacency: Array<out List<FaceAdjacency>>): List<Int> {
        val bandSet = bandFaces.toSet()
        val start = bandFaces.minOrNull() ?: return emptyList()
        val neighbors = adjacency[start].map { it.otherFace }.filter { it in bandSet }.sorted()
        if (neighbors.isEmpty()) return listOf(start)
        var prev = start
        var current = neighbors.first()
        val cycle = mutableListOf(start, current)
        while (true) {
            val nextOptions = adjacency[current].map { it.otherFace }
                .filter { it in bandSet && it != prev }
                .sorted()
            if (nextOptions.isEmpty()) break
            val next = nextOptions.first()
            if (next == start) break
            cycle.add(next)
            prev = current
            current = next
        }
        return cycle
    }

    private fun buildComponentTreeEdges(facesInComponent: Set<Int>, adjacency: Array<out List<FaceAdjacency>>): List<Triple<Int, Int, IntArray>> {
        val edges = ArrayList<Triple<Int, Int, IntArray>>()
        val start = facesInComponent.minOrNull() ?: return edges
        val visited = HashSet<Int>()
        val queue = ArrayDeque<Int>()
        visited.add(start)
        queue.add(start)
        while (queue.isNotEmpty()) {
            val face = queue.removeFirst()
            for (adj in adjacency[face]) {
                val other = adj.otherFace
                if (other !in facesInComponent || visited.contains(other)) continue
                visited.add(other)
                edges.add(Triple(face, other, adj.shared))
                queue.add(other)
            }
        }
        return edges
    }

    private fun keyForVertex(faceIndex: Int, i: Int, j: Int, k: Int, v0: Int, v1: Int, v2: Int, frequency: Int): String {
        fun edgeKey(a: Int, b: Int, weightA: Int, weightB: Int): String {
            val min = minOf(a, b)
            val max = maxOf(a, b)
            val t = if (a == min) weightB else weightA
            return "E${min}_${max}_$t"
        }
        if (i == frequency && j == 0 && k == 0) return "V$v0"
        if (j == frequency && i == 0 && k == 0) return "V$v1"
        if (k == frequency && i == 0 && j == 0) return "V$v2"
        if (k == 0) return edgeKey(v0, v1, i, j)
        if (i == 0) return edgeKey(v1, v2, j, k)
        if (j == 0) return edgeKey(v2, v0, k, i)
        return "F${faceIndex}_${i}_${j}_${k}"
    }
}
