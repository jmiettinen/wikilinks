package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import fi.eonwe.wikilinks.fibonacciheap.FibonacciHeap;
import fi.eonwe.wikilinks.fibonacciheap.FibonacciHeapNode;

import java.util.Collections;
import java.util.List;

/**
 */
public class RouteFinder {

    private final FibonacciHeap<RouteData> heap;
    private FibonacciHeapNode[] nodes;
    private final int startIndex;
    private final int endIndex;
    private final WikiRoutes.PageMapper mapper;

    private RouteFinder(int startIndex, int endIndex, WikiRoutes.PageMapper mapper) {
        this.mapper = mapper;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.heap = new FibonacciHeap<>();
        this.nodes = new FibonacciHeapNode[mapper.getSize()];
        setup(startIndex);
    }

    public static int[] find(int startIndex, int endIndex, WikiRoutes.PageMapper mapper) {
        RouteFinder finder = new RouteFinder(startIndex, endIndex, mapper);
        int[] route = finder.find();
        return route;
    }

    private void setup(int startId) {
        FibonacciHeapNode<RouteData> node = getNode(startId);
        heap.insert(node, 0.0);
    }

    private static class RouteData {
        public RouteData prev;
        public final int linkIndex;

        private RouteData(int linkIndex) {
            this.linkIndex = linkIndex;
        }
    }

    private FibonacciHeapNode<RouteData> getNode(int linkIndex) {
        @SuppressWarnings("unchecked")
        FibonacciHeapNode<RouteData> node = nodes[linkIndex];
        if (node == null) {
            node = new FibonacciHeapNode<>(new RouteData(linkIndex));
            nodes[linkIndex] = node;
            heap.insert(node, Double.POSITIVE_INFINITY);
        }
        return node;
    }

    private int[] find() {
        while (!heap.isEmpty()) {
            FibonacciHeapNode<RouteData> min = heap.removeMin();
            RouteData data = min.getData();
            final double newDistance = min.getKey() + 1.0;
            int pageIndex = data.linkIndex;
            if (pageIndex == endIndex) return recordRoute(data);
            mapper.getForIndex(pageIndex).forEachLinkIndex(linkIndex -> {
                FibonacciHeapNode<RouteData> node = getNode(linkIndex);
                if (newDistance < node.getKey()) {
                    heap.decreaseKey(node, newDistance);
                    node.getData().prev = data;
                }
            });
        }
        return new int[0];
    }

    private int[] recordRoute(RouteData endPoint) {
        List<Integer> list = Lists.newArrayList();
        RouteData cur = endPoint;
        while (cur != null) {
            list.add(cur.linkIndex);
            cur = cur.prev;
        }
        Collections.reverse(list);
        return Ints.toArray(list);
    }
}
