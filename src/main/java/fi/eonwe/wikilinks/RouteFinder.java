package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import fi.eonwe.wikilinks.heaps.RadixHeap;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 */
public class RouteFinder {

    private final RadixHeap heap;
    private long[] nodes;
    private int[] previous;
    private final int startIndex;
    private final int endIndex;
    private final WikiRoutes.PageMapper mapper;

    private RouteFinder(int startIndex, int endIndex, WikiRoutes.PageMapper mapper) {
        this.mapper = mapper;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.heap = new RadixHeap(bucketIndex -> {
            if (bucketIndex < 3) {
                return 1 << 18;
            } else if (bucketIndex < 8) {
                return 1 << 15;
            } else {
                return 1 << 10;
            }
        });
        this.nodes = new long[mapper.getSize()];
        this.previous = new int[mapper.getSize()];
        Arrays.fill(nodes, -1);
        Arrays.fill(previous, -1);
        setup(startIndex);
    }

    public static int[] find(int startIndex, int endIndex, WikiRoutes.PageMapper mapper) {
        RouteFinder finder = new RouteFinder(startIndex, endIndex, mapper);
        int[] route = finder.find();
        return route;
    }

    private void setup(int startIndex) {
        nodes[startIndex] = heap.insert(0, startIndex);
    }

    private int[] find() {
        while (!heap.isEmpty()) {
            long minVal = heap.extractMin();
            int pageIndex = RadixHeap.extractData(minVal);
            int key = RadixHeap.extractKey(minVal);
            final int newDistance = key + 1;
            if (pageIndex == endIndex) return recordRoute(endIndex);
            for (int linkIndex : mapper.getForIndex(pageIndex).getTargetIndices()) {
                long existingVal = nodes[linkIndex];
                if (existingVal != -1) {
                    int oldKey = RadixHeap.extractKey(existingVal);
                    if (newDistance < oldKey) {
                        nodes[linkIndex] = heap.decreaseKey(newDistance, existingVal);
                        previous[linkIndex] = pageIndex;
                    }
                } else {
                    nodes[linkIndex] = heap.insert(newDistance, linkIndex);
                    previous[linkIndex] = pageIndex;
                }
            }
        }
        return new int[0];
    }

    private int[] recordRoute(int endIndex) {
        List<Integer> list = Lists.newArrayList();
        int cur = endIndex;
        do {
            list.add(cur);
            cur = previous[cur];
        } while (cur != -1);
        Collections.reverse(list);
        return Ints.toArray(list);
    }
}
