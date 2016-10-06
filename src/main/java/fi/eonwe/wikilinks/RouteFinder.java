package fi.eonwe.wikilinks;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import fi.eonwe.wikilinks.utils.IntQueue;
import net.openhft.koloboke.collect.map.IntIntMap;
import net.openhft.koloboke.collect.map.hash.HashIntIntMaps;
import net.openhft.koloboke.function.IntIntConsumer;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

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
            if (!forwardIsTooBig || backwardIsTooBig) {
                final int forwardId = forwardQueue.removeFirst();
                if (backwardPrev.containsKey(forwardId)) {
                    return recordRoute(startIndex, endIndex, forwardPrev, backwardPrev);
                }
                mapper.forEachLinkIndex(forwardId, linkId -> {
                    if (forwardPrev.putIfAbsent(linkId, forwardId) == NOT_FOUND) {
                        forwardQueue.addLast(linkId);
                    }
                });
                forwardIsTooBig = forwardPrev.size() > TOO_BIG;
            }
            if (!backwardIsTooBig || forwardIsTooBig) {
                final int backwardId = backwardQueue.removeFirst();
                if (forwardPrev.containsKey(backwardId)) {
                    return recordRoute(startIndex, endIndex, forwardPrev, backwardPrev);
                }
                reverseMapper.forEachLinkIndex(backwardId, linkId -> {
                    if (backwardPrev.putIfAbsent(linkId, backwardId) == NOT_FOUND) {
                        backwardQueue.addLast(linkId);
                    }
                });
                backwardIsTooBig = backwardPrev.size() > TOO_BIG;
            }
        }
        return new int[0];
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
                return Ints.toArray(recordRoute(startIndex, endIndex, previous));
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

    private int countPath(IntIntMap map, int startIndex, int endIndex) {
        int size = 0;
        int cur = startIndex;
        while ((cur = map.getOrDefault(cur, endIndex)) != endIndex) {
            size++;
        }
        return size;
    }

    private int[] recordRoute(int startIndex, int endIndex, IntIntMap forwardPrev, IntIntMap backwardPrev) {
        int[] bestPath = { -1, Integer.MAX_VALUE };
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
        return Ints.toArray(Lists.newArrayList(Iterables.concat(firstPart.subList(1, firstPart.size()), secondPart)));
    }

    private List<Integer> recordRoute(int startIndex, int endIndex, IntIntMap previous) {
        List<Integer> list = Lists.newArrayList();
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
