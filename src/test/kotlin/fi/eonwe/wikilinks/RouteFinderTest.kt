package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import fi.eonwe.wikilinks.leanpages.OrderedPage
import io.kotest.matchers.shouldBe
import org.jgrapht.EdgeFactory
import org.jgrapht.VertexFactory
import org.jgrapht.alg.DijkstraShortestPath
import org.jgrapht.generate.RandomGraphGenerator
import org.jgrapht.graph.SimpleDirectedGraph
import org.junit.jupiter.api.Test
import java.util.Random
import java.util.function.IntConsumer
import java.util.stream.Stream
import kotlin.math.min

/**
 */
class RouteFinderTest {
    private class IntEdge(val start: Int, val end: Int) {
        override fun toString(): String {
            return String.format("%d->%d", start, end)
        }
    }

    private class VF : VertexFactory<Int?> {
        private var vertexCount = 0

        override fun createVertex(): Int {
            return vertexCount++
        }

    }

    private class EF : EdgeFactory<Int, IntEdge> {
        override fun createEdge(sourceVertex: Int, targetVertex: Int): IntEdge {
            return IntEdge(sourceVertex, targetVertex)
        }
    }

    @Test
    fun findsTheSameSizedRoutes() {
        val rng = Random(-0x35014542)
        val vertexCount = 1000
        val edgeCount = vertexCount * min(vertexCount / 2, 100)
        val generator: RandomGraphGenerator<Int?, IntEdge?> =
            RandomGraphGenerator<Int?, IntEdge?>(vertexCount, edgeCount)
        val repeats = 10
        val innerRepeats = 10
        repeat(repeats) {
            val graph = SimpleDirectedGraph(EF())
            val resultMap = mutableMapOf<String?, Int?>()
            generator.generateGraph(graph, VF(), resultMap)
            val orderedPageMap: MutableMap<Int, OrderedPage> = createFromGraph(graph)
            val vertices = graph.vertexSet()
            repeat(innerRepeats) {
                var startVertex: Int
                var endVertex: Int
                do {
                    startVertex = rng.nextInt(vertices.size)
                    endVertex = rng.nextInt(vertices.size)
                } while (startVertex == endVertex)
                val dijkstra: DijkstraShortestPath<Int?, IntEdge?> = DijkstraShortestPath(graph, startVertex, endVertex)
                val route: IntArray = toIntArray(dijkstra.getPathEdgeList().mapNotNull { it })

                val mapper: WikiRoutes.PageMapper = fromMap(orderedPageMap)
                val myRoute = RouteFinder.find(startVertex, endVertex, mapper, null)

                route.size shouldBe myRoute.size
            }
        }
    }

    companion object {
        private fun toIntArray(path: List<IntEdge>): IntArray {
            if (path.isEmpty()) return IntArray(0)
            val startPoints =
                Stream.concat(
                    path.stream().map { intEdge: IntEdge -> intEdge.start }, Stream.of(
                        path[path.size - 1].end
                    )
                ).mapToInt { obj: Int? -> obj!! }.toArray()
            return startPoints
        }

        private fun fromMap(map: MutableMap<Int, OrderedPage>): WikiRoutes.PageMapper {
            return object : WikiRoutes.PageMapper {
                override fun forEachLinkIndex(pageIndex: Int, c: IntConsumer) {
                    return map[pageIndex]!!.forEachLinkIndex(c)
                }
            }
        }

        private fun createFromGraph(graph: SimpleDirectedGraph<Int, IntEdge>): MutableMap<Int, OrderedPage> {
            val result: MutableMap<Int, OrderedPage> = mutableMapOf()
            for (vertex in graph.vertexSet()) {
                val out = graph.outgoingEdgesOf(vertex)
                val targets = out.stream().mapToInt { e: IntEdge? -> e!!.end }.toArray()
                val bwp = BufferWikiPage.createFrom(vertex, IntArray(0), "", false)
                val page = OrderedPage(bwp, targets)
                result[vertex] = page
            }
            return result
        }
    }
}