package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import fi.eonwe.wikilinks.utils.FixedSizeIntQueue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 */
public class RouteFinder {
    private final int startIndex;
    private final int endIndex;
    private final WikiRoutes.PageMapper mapper;

    private RouteFinder(int startIndex, int endIndex, WikiRoutes.PageMapper mapper) {
        this.mapper = mapper;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    public static int[] find(int startIndex, int endIndex, WikiRoutes.PageMapper mapper) {
        RouteFinder finder = new RouteFinder(startIndex, endIndex, mapper);
        int[] route = finder.find();
        return route;
    }

    private int[] find() {
        int[] previous = new int[mapper.getSize()];
        Arrays.fill(previous, -1);
        
        FixedSizeIntQueue queue = new FixedSizeIntQueue(mapper.getSize());
        
        previous[startIndex] = startIndex;
        queue.addLast(startIndex);
        while (!queue.isEmpty()) {
            final int pageIndex = queue.removeFirst();
            if (pageIndex == endIndex) {
                return recordRoute(endIndex, previous);
            }
            mapper.getForIndex(pageIndex).forEachLinkIndex(linkIndex -> {
                if (previous[linkIndex] == -1) {
                    boolean didFit = queue.addLast(linkIndex);
                    assert didFit;
                    previous[linkIndex] = pageIndex;
                }
            });
        }
        return new int[0];
    }

    private int[] recordRoute(int endIndex, int[] previous) {
        List<Integer> list = Lists.newArrayList();
        int cur = endIndex;
        do {
            list.add(cur);
            cur = previous[cur];
        } while (cur != startIndex);
        list.add(startIndex);
        Collections.reverse(list);
        return Ints.toArray(list);
    }
}
