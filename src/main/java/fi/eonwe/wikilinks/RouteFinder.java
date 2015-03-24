package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import fi.eonwe.wikilinks.fibonacciheap.FibonacciHeap;
import fi.eonwe.wikilinks.fibonacciheap.FibonacciHeapNode;
import fi.eonwe.wikilinks.leanpages.LeanWikiPage;
import net.openhft.koloboke.collect.hash.HashConfig;
import net.openhft.koloboke.collect.map.hash.HashIntObjMap;
import net.openhft.koloboke.collect.map.hash.HashIntObjMaps;

import java.util.Collections;
import java.util.List;

/**
 */
public class RouteFinder {

    private final FibonacciHeap<RouteData> heap;
    private final HashIntObjMap<FibonacciHeapNode<RouteData>> nodes = HashIntObjMaps.getDefaultFactory()
            .withHashConfig(HashConfig.fromLoads(0.1, 0.5, 0.75))
            .withKeysDomain(Integer.MIN_VALUE, -1)
            .newMutableMap(1 << 17);
    private final int startId;
    private final int endId;
    private final WikiRoutes.PageMapper mapper;

    private RouteFinder(int startId, int endId, WikiRoutes.PageMapper mapper) {
        this.mapper = mapper;
        this.startId = startId;
        this.endId = endId;
        this.heap = new FibonacciHeap<>();
        setup(startId);
    }

    public static int[] find(int startId, int endId, WikiRoutes.PageMapper mapper) {
        RouteFinder finder = new RouteFinder(startId, endId, mapper);
        int[] route = finder.find();
        return route;
    }

    private void setup(int startId) {
        FibonacciHeapNode<RouteData> node = getNode(startId);
        heap.insert(node, 0.0);
    }

    private static class RouteData {
        public RouteData prev;
        public final int pageId;

        private RouteData(int pageId) {
            this.pageId = pageId;
        }
    }

    private FibonacciHeapNode<RouteData> getNode(int articleId) {
        final int shiftedId = shift(articleId);
        FibonacciHeapNode<RouteData> node = nodes.get(shiftedId);
        if (node == null) {
            node = new FibonacciHeapNode<>(new RouteData(articleId));
            nodes.put(shiftedId, node);
            heap.insert(node, Double.POSITIVE_INFINITY);
        }
        return node;
    }

    private static int shift(int value) { return (-value) - 1; }

    private int[] find() {
        while (!heap.isEmpty()) {
            FibonacciHeapNode<RouteData> min = heap.removeMin();
            final double distance = min.getKey();
            int pageId = min.getData().pageId;
            if (pageId == endId) return recordRoute(min.getData());
            mapper.getForId(pageId).forEachLink(linkTarget -> {
                FibonacciHeapNode<RouteData> node = getNode(linkTarget);
                final double newDistance = distance + 1.0;
                if (newDistance < node.getKey()) {
                    heap.decreaseKey(node, newDistance);
                    node.getData().prev = min.getData();
                }
            });
        }
        return new int[0];
    }

    private int[] recordRoute(RouteData endPoint) {
        List<Integer> list = Lists.newArrayList();
        RouteData cur = endPoint;
        while (cur != null) {
            list.add(cur.pageId);
            cur = cur.prev;
        }
        Collections.reverse(list);
        return Ints.toArray(list);
    }
}
