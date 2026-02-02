package com.unciv.logic.map

import com.unciv.testing.GdxTestRunner
import com.unciv.testing.TestGame
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GdxTestRunner::class)
class AlgorithmTests {

    val testGame = TestGame()

    @Before
    fun init() {
        testGame.makeHexagonalMap(5)
    }

    @Test
    fun testBFS() {
        val origin = testGame.tileMap[0, 0]
        val bfs = BFS(origin) { true }
        bfs.stepToEnd()
        Assert.assertEquals(91, bfs.size())
        Assert.assertTrue(bfs.hasReachedTile(testGame.tileMap[5, 0]))
        
        val path = bfs.getPathTo(testGame.tileMap[1, 1]).toList()
        Assert.assertEquals(2, path.size)
        Assert.assertEquals(testGame.tileMap[1, 1], path[0])
        Assert.assertEquals(origin, path[1])
    }

    @Test
    fun testBFSPredicate() {
        val origin = testGame.tileMap[0, 0]
        // Only allow movement to tiles with x >= 0
        val bfs = BFS(origin) { it.position.x >= 0 }
        bfs.stepToEnd()
        for (tile in bfs.getReachedTiles()) {
            Assert.assertTrue(tile.position.x >= 0)
        }
    }

    @Test
    fun testAStarDijkstra() {
        val origin = testGame.tileMap[0, 0]
        val goal = testGame.tileMap[2, 2]
        // Dijkstra: heuristic is always 0
        val astar = AStar(origin, { true }, { _, _ -> 1f }, { _, _ -> 0f })
        astar.stepUntilDestination(goal)
        
        Assert.assertTrue(astar.hasReachedTile(goal))
        val path = astar.getPathTo(goal).toList()
        Assert.assertEquals(3, path.size) // (2,2), (1,1), (0,0)
    }

    @Test
    fun testAStarWithHeuristic() {
        val origin = testGame.tileMap[0, 0]
        val goal = testGame.tileMap[2, 2]
        // Heuristic: distance to goal
        // Wait, AStar implementation in Unciv takes (current, neighbor)
        // so it must be a closure.
        val astar = AStar(origin, 
            { true }, 
            { _, _ -> 1f }, 
            { _, neighbor -> neighbor.aerialDistanceTo(goal).toFloat() }
        )
        astar.stepUntilDestination(goal)
        
        Assert.assertTrue(astar.hasReachedTile(goal))
        val path = astar.getPathTo(goal).toList()
        Assert.assertEquals(3, path.size)
    }
}
