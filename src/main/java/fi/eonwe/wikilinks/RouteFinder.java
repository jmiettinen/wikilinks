package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import com.google.common.primitives.Longs;
import net.openhft.koloboke.collect.map.hash.HashLongIntMap;
import net.openhft.koloboke.collect.map.hash.HashLongObjMap;
import net.openhft.koloboke.collect.map.hash.HashLongObjMaps;
import org.jgrapht.util.FibonacciHeap;
import org.jgrapht.util.FibonacciHeapNode;

import java.util.Collections;
import java.util.List;

/**
 */
public class RouteFinder {

    private final FibonacciHeap<RouteData> heap;
    private final HashLongObjMap<FibonacciHeapNode<RouteData>> nodes = HashLongObjMaps.newMutableMap();
    private final HashLongIntMap idIndexMap;
    private final List<? extends LeanWikiPage> graph;
    private final long startId;
    private final long endId;

    private RouteFinder(long startId, long endId, List<? extends LeanWikiPage> graph, HashLongIntMap idIndexMap) {
        this.graph = graph;
        this.idIndexMap = idIndexMap;
        this.startId = startId;
        this.endId = endId;
        this.heap = new FibonacciHeap<>();
        setup(startId);
    }

    public static long[] find(long startId, long endId, List<? extends LeanWikiPage> graph, HashLongIntMap idIndexMap) {
        RouteFinder finder = new RouteFinder(startId, endId, graph, idIndexMap);
        long[] route = finder.find();
        return route;
    }

    private void setup(long startId) {
        FibonacciHeapNode<RouteData> node = getNode(startId);
        heap.insert(node, 0.0);
    }

    private LeanWikiPage forId(long id) {
        int index = idIndexMap.getOrDefault(id, -1);
        if (index < 0 || index >= graph.size()) return null;
        return graph.get(index);
    }

    private static class RouteData {
        public RouteData prev;
        public final LeanWikiPage page;

        private RouteData(LeanWikiPage page) {
            this.page = page;
        }
    }

    private FibonacciHeapNode<RouteData> getNode(long articleId) {
        FibonacciHeapNode<RouteData> node = nodes.getOrDefault(articleId, null);
        if (node == null) {
            node = new FibonacciHeapNode<>(new RouteData(forId(articleId)), Double.POSITIVE_INFINITY);
            nodes.put(articleId, node);
            heap.insert(node, node.getKey());
        }
        return node;
    }

    private long[] find() {
        while (!heap.isEmpty()) {
            FibonacciHeapNode<RouteData> min = heap.removeMin();
            final double distance = min.getKey();
            LeanWikiPage page = min.getData().page;
            if (page.getId() == endId) return recordRoute(min.getData());
            page.forEachLink(linkTarget -> {
                FibonacciHeapNode<RouteData> node = getNode(linkTarget);
                final double newDistance = distance + 1.0;
                if (newDistance < node.getKey()) {
                    heap.decreaseKey(node, newDistance);
                    node.getData().prev = min.getData();
                }
            });
        }
        return new long[0];
    }

    private long[] recordRoute(RouteData endPoint) {
        List<Long> list = Lists.newArrayList();
        RouteData cur = endPoint;
        while (cur != null) {
            list.add(cur.page.getId());
            cur = cur.prev;
        }
        Collections.reverse(list);
        return Longs.toArray(list);
    }


}
