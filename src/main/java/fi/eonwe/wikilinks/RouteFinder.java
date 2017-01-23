package fi.eonwe.wikilinks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import fi.eonwe.wikilinks.utils.IntQueue;
import net.openhft.koloboke.collect.map.IntIntMap;
import net.openhft.koloboke.collect.map.hash.HashIntIntMaps;
import net.openhft.koloboke.function.IntIntConsumer;

/**
 */
public class RouteFinder {
    private final int startIndex;
    private final int endIndex;
    private final WikiRoutes.PageMapper mapper;
    @Nullable private final WikiRoutes.PageMapper reverseMapper;

    private static final int NOT_FOUND = -1;
    private static final int DEFAULT_SIZE = 65536;
    private static final int TOO_BIG = 1 << 18;

    private RouteFinder(int startIndex, int endIndex, WikiRoutes.PageMapper mapper, @Nullable WikiRoutes.PageMapper reverseMapper) {
        this.mapper = mapper;
        this.reverseMapper = reverseMapper;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    public static int[] find(int startIndex, int endIndex, WikiRoutes.PageMapper forwardMapper, @Nullable WikiRoutes.PageMapper reverseMapper) {
        RouteFinder finder = new RouteFinder(startIndex, endIndex, forwardMapper, reverseMapper);
        final int[] route;
        if (reverseMapper == null) {
            route = finder.find();
        } else {
            route = finder.findWithReverse();
        }
        return route;
    }

    private int[] findWithReverse() {
        assert reverseMapper != null;
        IntIntMap forwardPrev = HashIntIntMaps.getDefaultFactory()
                                           .withDefaultValue(NOT_FOUND)
                                           .newMutableMap(DEFAULT_SIZE);
        IntIntMap backwardPrev = HashIntIntMaps.getDefaultFactory()
                                               .withDefaultValue(NOT_FOUND)
                                               .newMutableMap(DEFAULT_SIZE);

        IntQueue forwardQueue = IntQueue.growingQueue(DEFAULT_SIZE);
        IntQueue backwardQueue = IntQueue.growingQueue(DEFAULT_SIZE);
        forwardPrev.put(startIndex, startIndex);
        backwardPrev.put(endIndex, endIndex);
        forwardQueue.addLast(startIndex);
        backwardQueue.addLast(endIndex);
        // Try to limit the sizes of the maps when we're not making any progress.
        boolean forwardIsTooBig = false;
        boolean backwardIsTooBig = false;

        while (!backwardQueue.isEmpty() && !forwardQueue.isEmpty()) {
            boolean foundRoute = false;
            if (!forwardIsTooBig || backwardIsTooBig) {
                foundRoute = findRoute(forwardQueue, forwardPrev, mapper, backwardPrev);
                forwardIsTooBig = forwardPrev.size() > TOO_BIG;
            }
            if (!foundRoute && (!backwardIsTooBig || forwardIsTooBig)) {
                foundRoute = findRoute(backwardQueue, backwardPrev, reverseMapper, forwardPrev);
                backwardIsTooBig = backwardPrev.size() > TOO_BIG;
            }
            if (foundRoute) {
                return recordRoute(startIndex, endIndex, forwardPrev, backwardPrev);
            }
        }
        return new int[0];
    }

    private static boolean findRoute(IntQueue queue, IntIntMap prevMap, WikiRoutes.PageMapper mapper, IntIntMap reversePrevMap) {
        final int id = queue.removeFirst();
        if (reversePrevMap.containsKey(id)) {
            return true;
        }
        mapper.forEachLinkIndex(id, linkId -> {
            if (prevMap.putIfAbsent(linkId, id) == NOT_FOUND) {
                queue.addLast(linkId);
            }
        });
        return false;
    }

    private int[] find() {
        IntIntMap previous = HashIntIntMaps.getDefaultFactory()
                .withDefaultValue(NOT_FOUND)
                .newMutableMap(DEFAULT_SIZE);

        IntQueue queue = IntQueue.growingQueue(DEFAULT_SIZE);

        previous.put(startIndex, startIndex);
        queue.addLast(startIndex);
        while (!queue.isEmpty()) {
            final int pageId = queue.removeFirst();
            if (pageId == endIndex) {
                return toInt(recordRoute(startIndex, endIndex, previous));
            }
            mapper.forEachLinkIndex(pageId, linkId -> {
                if (previous.putIfAbsent(linkId, pageId) == NOT_FOUND) {
                    boolean didFit = queue.addLast(linkId);
                    assert didFit;
                }
            });
        }
        return new int[0];
    }

    private static int countPath(IntIntMap map, int startIndex, int endIndex) {
        int size = 0;
        int cur = startIndex;
        while ((cur = map.getOrDefault(cur, endIndex)) != endIndex) {
            size++;
        }
        return size;
    }

    private static int[] recordRoute(int startIndex, int endIndex, IntIntMap forwardPrev, IntIntMap backwardPrev) {
        int[] bestPath = { NOT_FOUND, Integer.MAX_VALUE };
        final int scoreIndex = bestPath.length - 1;
        backwardPrev.forEach((IntIntConsumer) (target, source) -> {
            if (forwardPrev.containsKey(target)) {
                int stepsBackwards = countPath(forwardPrev, target, startIndex);
                int stepsForwards = countPath(backwardPrev, target, endIndex);
                int totalSteps = 1 + stepsBackwards + stepsForwards;
                if (bestPath[scoreIndex] > totalSteps) {
                    bestPath[scoreIndex] = totalSteps;
                    bestPath[0] = target;
                }
            }
        });
        List<Integer> firstPart = recordRoute(startIndex, bestPath[0], forwardPrev);
        List<Integer> secondPart = recordRoute(endIndex, bestPath[0], backwardPrev);
        Collections.reverse(secondPart);
        final List<Integer> firstPartSplit;
        final List<Integer> secondPartSplit;
        if (firstPart.size() > 1) {
            firstPartSplit = firstPart.subList(0, firstPart.size() - 1);
            secondPartSplit = secondPart;
        } else {
            firstPartSplit = firstPart;
            secondPartSplit = secondPart.subList(1, secondPart.size());
        }
        return toInt(firstPartSplit, secondPartSplit);
    }

    @SafeVarargs
    private static int[] toInt(Iterable<? extends Integer> ... values) {
        return Arrays.stream(values)
                     .flatMap(iter -> StreamSupport.stream(iter.spliterator(), false))
                     .mapToInt(Integer::intValue)
                     .toArray();
    }

    private static List<Integer> recordRoute(int startIndex, int endIndex, IntIntMap previous) {
        List<Integer> list = new ArrayList<>();
        int cur = endIndex;
        do {
            list.add(cur);
            cur = previous.getOrDefault(cur, startIndex);
        } while (cur != startIndex);
        list.add(startIndex);
        Collections.reverse(list);
        return list;
    }
}
