package fi.eonwe.wikilinks;

import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.LongIntMap;
import com.carrotsearch.hppc.LongObjectMap;
import com.carrotsearch.hppc.LongObjectOpenHashMap;
import com.carrotsearch.hppc.procedures.LongProcedure;
import com.google.common.primitives.Longs;
import org.jgrapht.util.FibonacciHeap;
import org.jgrapht.util.FibonacciHeapNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 */
public class RouteFinder {

    private final FibonacciHeap<RouteData> heap;
    private final LongObjectMap<FibonacciHeapNode<RouteData>> nodes = new LongObjectOpenHashMap<>();
    private final LongIntMap idIndexMap;
    private final List<PackedWikiPage> graph;
    private final long startId;
    private final long endId;

    private RouteFinder(long startId, long endId, List<PackedWikiPage> graph, LongIntMap idIndexMap) {
        this.graph = graph;
        this.idIndexMap = idIndexMap;
        this.startId = startId;
        this.endId = endId;
        this.heap = new FibonacciHeap<>();
        setup(startId);
    }

    public static long[] find(long startId, long endId, List<PackedWikiPage> graph, LongIntMap idIndexMap) {
        RouteFinder finder = new RouteFinder(startId, endId, graph, idIndexMap);
        long[] route = finder.find();
        return route;
    }

    private void setup(long startId) {
        FibonacciHeapNode<RouteData> node = getNode(startId);
        heap.insert(node, 0.0);
    }

    private PackedWikiPage forId(long id) {
        int index = idIndexMap.getOrDefault(id, -1);
        if (index < 0 || index >= graph.size()) return null;
        return graph.get(index);
    }

    private static class RouteData {
        public RouteData prev;
        public final PackedWikiPage page;

        private RouteData(PackedWikiPage page) {
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
            PackedWikiPage page = min.getData().page;
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
        LongArrayList list = new LongArrayList();
        RouteData cur = endPoint;
        while (cur != null) {
            list.add(cur.page.getId());
            cur = cur.prev;
        }
        List<Long> listlist =  Longs.asList(list.toArray());
        Collections.reverse(listlist);
        return Longs.toArray(listlist);
    }


}
