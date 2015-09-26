package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import fi.eonwe.wikilinks.utils.FixedSizeIntQueue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/**
 */
public class RouteFinder {

    private final FixedSizeIntQueue queue;
    private long[] distanceAndPrevious;
    private final int endIndex;
    private final long startValue;
    private final WikiRoutes.PageMapper mapper;

    private static final long UNSET_ENTRY = createFrom(-1, Integer.MAX_VALUE);

    private RouteFinder(int startIndex, int endIndex, WikiRoutes.PageMapper mapper) {
        this.mapper = mapper;
        this.startValue = createFrom(startIndex, 0);
        this.endIndex = endIndex;
        this.queue = new FixedSizeIntQueue(mapper.getSize());
        this.distanceAndPrevious = new long[mapper.getSize()];
        Arrays.fill(distanceAndPrevious, UNSET_ENTRY);
        setup(startIndex);
    }

    public static int[] find(int startIndex, int endIndex, WikiRoutes.PageMapper mapper) {
        RouteFinder finder = new RouteFinder(startIndex, endIndex, mapper);
        int[] route = finder.find();
        return route;
    }

    private void setup(int startIndex) {
        queue.addLast(startIndex);
        distanceAndPrevious[startIndex] = createFrom(startIndex, 0);
    }

    private int[] find() {
        while (!queue.isEmpty()) {
            final int pageIndex = queue.removeFirst();
            final long minVal = distanceAndPrevious[pageIndex];
            final int distance = extractKey(minVal);
            final int newDistance = distance + 1;
            if (pageIndex == endIndex) {
                return recordRoute(endIndex);
            }
            mapper.getForIndex(pageIndex).forEachLinkIndex(linkIndex -> {
                final long oldVal = distanceAndPrevious[linkIndex];
                if (oldVal == UNSET_ENTRY) {
                    boolean didFit = queue.addLast(linkIndex);
                    assert didFit;
                    distanceAndPrevious[linkIndex] = createFrom(pageIndex, newDistance);
                }
            });
        }
        return new int[0];
    }

    private int[] recordRoute(int endIndex) {
        List<Integer> list = Lists.newArrayList();
        long cur = createFrom(endIndex, -1);
        do {
            int index = extractData(cur);
            list.add(index);
            cur = distanceAndPrevious[index];
        } while (cur != startValue);
        Collections.reverse(list);
        return Ints.toArray(list);
    }

    private static int extractData(long val) {
        return (int) val;
    }

    private static int extractKey(long val) {
        return (int) (val >>> 32);
    }

    private static long createFrom(int data, int key) {
        return data | ((long) key) << 32;
    }


}
