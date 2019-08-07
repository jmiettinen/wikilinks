package fi.eonwe.wikilinks;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.collect.Maps;
import fi.eonwe.wikilinks.leanpages.OrderedPage;
import org.jgrapht.EdgeFactory;
import org.jgrapht.VertexFactory;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.generate.RandomGraphGenerator;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 */
public class RouteFinderTest {

    private static class IntEdge {
        public final int start;
        public final int end;

        private IntEdge(Integer start, Integer end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public String toString() {
            return String.format("%d->%d", start, end);
        }
    }

    private static class VF implements VertexFactory<Integer> {

        private int vertexCount = 0;

        @Override
        public Integer createVertex() {
            return vertexCount++;
        }

        public void reset() { vertexCount = 0; }
    }

    private static class EF implements EdgeFactory<Integer, IntEdge> {
        @Override
        public IntEdge createEdge(Integer sourceVertex, Integer targetVertex) {
            return new IntEdge(sourceVertex, targetVertex);
        }
    }

    @Test
    @Ignore("Too slow")
    public void findsTheSameSizedRoutes() {
        final Random rng = new Random(0xcafebabe);
        final int vertexCount = 1000;
        final int edgeCount = vertexCount * Math.min(vertexCount / 2, 100);
        RandomGraphGenerator<Integer, IntEdge> generator = new RandomGraphGenerator<>(vertexCount, edgeCount);
        final int repeats = 10;
        final int innerRepeats = 10;
        for (int i = 0; i < repeats; i++) {
            SimpleDirectedGraph<Integer, IntEdge> graph = new SimpleDirectedGraph<>(new EF());
            Map<String, Integer> resultMap = Maps.newHashMap();
            generator.generateGraph(graph, new VF(), resultMap);
            Map<Integer, OrderedPage> orderedPageMap = createFromGraph(graph);
            Set<Integer> vertices = graph.vertexSet();
            for (int j = 0; j < innerRepeats; j++) {
                int startVertex, endVertex;
                do {
                    startVertex = rng.nextInt(vertices.size());
                    endVertex = rng.nextInt(vertices.size());
                } while (startVertex == endVertex);
                long dijkstraStart = System.currentTimeMillis();
                DijkstraShortestPath<Integer, IntEdge> dijkstra = new DijkstraShortestPath<>(graph, startVertex, endVertex);
                int[] route = toIntArray(dijkstra.getPathEdgeList());
                long jgraphtTime = System.currentTimeMillis() - dijkstraStart;

                WikiRoutes.PageMapper mapper = fromMap(orderedPageMap);
                long myStart = System.currentTimeMillis();
                int[] myRoute = RouteFinder.find(startVertex, endVertex, mapper, null);
                long myTime = System.currentTimeMillis() - myStart;
//                System.out.printf("%d vertices, %d edges: JGraphT=%d ms, RouteFinder=%d ms%n", vertexCount, (long) edgeCount * vertexCount, jgraphtTime, myTime);
                assertEquals(route.length, myRoute.length);
            }
        }
    }

    private static int[] toIntArray(List<IntEdge> path) {
        if (path.isEmpty()) return new int[0];
        int[] startPoints = Stream.concat(path.stream().map(intEdge -> intEdge.start), Stream.of(path.get(path.size() - 1).end)).mapToInt(Number::intValue).toArray();
        return startPoints;
    }

    private static WikiRoutes.PageMapper fromMap(Map<Integer, OrderedPage> map) {
        return (pageIndex, c) -> map.get(pageIndex).forEachLinkIndex(c);
    }

    private static Map<Integer, OrderedPage> createFromGraph(SimpleDirectedGraph<Integer, IntEdge> graph) {
        Map<Integer, OrderedPage> result = Maps.newHashMap();
        for (Integer vertex : graph.vertexSet()) {
            Set<IntEdge> out = graph.outgoingEdgesOf(vertex);
            int[] targets = out.stream().mapToInt(e -> e.end).toArray();
            OrderedPage page = new OrderedPage(null, targets) {
                private final int id = vertex;
                @Override
                public int getId() {
                    return id;
                }
            };
            result.put(vertex, page);
        }
        return result;
    }


}
