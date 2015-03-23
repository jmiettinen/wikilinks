package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import fi.eonwe.wikilinks.fibonacciheap.FibonacciHeap;
import fi.eonwe.wikilinks.fibonacciheap.FibonacciHeapNode;
import fi.eonwe.wikilinks.leanpages.LeanWikiPage;
import net.openhft.koloboke.collect.map.hash.HashIntObjMap;
import net.openhft.koloboke.collect.map.hash.HashIntObjMaps;

import java.util.Collections;
import java.util.List;

/**
 */
public class RouteFinder {

    private final FibonacciHeap<RouteData> heap;
    private final HashIntObjMap<FibonacciHeapNode<RouteData>> nodes = HashIntObjMaps.getDefaultFactory()
            .withKeysDomain(Integer.MIN_VALUE, -1)
            .newMutableMap(65536);
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
        public final LeanWikiPage page;

        private RouteData(LeanWikiPage page) {
            this.page = page;
        }
    }

    private FibonacciHeapNode<RouteData> getNode(int articleId) {
        final int shiftedId = shift(articleId);
        FibonacciHeapNode<RouteData> node = nodes.get(shiftedId);
        if (node == null) {
            node = new FibonacciHeapNode<>(new RouteData(mapper.getForId(articleId)));
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
        return new int[0];
    }

    private int[] recordRoute(RouteData endPoint) {
        List<Integer> list = Lists.newArrayList();
        RouteData cur = endPoint;
        while (cur != null) {
            list.add(cur.page.getId());
            cur = cur.prev;
        }
        Collections.reverse(list);
        return Ints.toArray(list);
    }
}
