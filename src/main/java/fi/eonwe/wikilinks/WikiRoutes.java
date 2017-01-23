package fi.eonwe.wikilinks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import fi.eonwe.wikilinks.leanpages.BufferWikiPage;
import fi.eonwe.wikilinks.utils.Functions;
import net.openhft.koloboke.collect.hash.HashConfig;
import net.openhft.koloboke.collect.map.IntIntMap;
import net.openhft.koloboke.collect.map.hash.HashIntIntMap;
import net.openhft.koloboke.collect.map.hash.HashIntIntMaps;
import net.openhft.koloboke.function.IntIntConsumer;

import static fi.eonwe.wikilinks.utils.Helpers.quote;

/**
 */
public class WikiRoutes {

    private final List<BufferWikiPage> pagesByTitle;
    private final List<BufferWikiPage> pagesById;
    private final LeanPageMapper mapper;
    private final LeanPageMapper reverseMapper;

    private static final Logger logger = Logger.getLogger(WikiRoutes.class.getCanonicalName());
    static {
        String logLevel = System.getProperty("wikilinks.loglevel", "WARNING");
        Level level;
        try {
            level = Level.parse(logLevel);
        } catch (IllegalArgumentException e) {
            level = Level.WARNING;
        }
        logger.setLevel(level);
    }

    public WikiRoutes(List<BufferWikiPage> pages) {
        this.pagesByTitle = new ArrayList<>(pages);
        this.pagesById = new ArrayList<>(pages);
        this.mapper = LeanPageMapper.convert(pages);
        this.reverseMapper = this.mapper.reverse();
        sortIfNeeded(this.pagesById, "by id", byId());
        sortIfNeeded(this.pagesByTitle, "by title", BufferWikiPage::compareTitle);
    }

    public Result findRoute(String startPage, String endPage) throws BadRouteException {
        BufferWikiPage startPageObj = getPage(startPage);
        BufferWikiPage endPageObj = getPage(endPage);
        if (startPageObj == null || endPageObj == null) {
            throw new BadRouteException(startPage == null, endPage == null, startPage, endPage);
        }
        return findRoute(startPageObj, endPageObj);
    }

    @Nullable
    public String getRandomPage() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (pagesByTitle.size() == 1) {
            return pagesByTitle.get(0).getTitle();
        } else if (pagesByTitle.isEmpty()) {
            return null;
        }
        int i = rng.nextInt(pagesByTitle.size());
        return pagesByTitle.get(i).getTitle();
    }

    private static void sortIfNeeded(List<BufferWikiPage> list, String name, Comparator<? super BufferWikiPage> comp) {
        long startTime = System.currentTimeMillis();
        if (!isSorted(list, comp)) {
            logger.info(() -> "Starting to sort by " + name);
            list.sort(comp);
            logger.info(() -> String.format("Took %d ms to sort by %s", System.currentTimeMillis() - startTime, name));
        }
    }

    private Result findRoute(BufferWikiPage startPage, BufferWikiPage endPage) {
        long startTime = System.nanoTime();
        int[] routeIds = RouteFinder.find(startPage.getId(), endPage.getId(), mapper, reverseMapper);
        List<BufferWikiPage> path = Arrays.stream(routeIds).mapToObj(id -> {
            BufferWikiPage needle = BufferWikiPage.createFrom(id, new int[0], "ignored", false);
            int index = Collections.binarySearch(pagesById, needle, byId());
            return pagesById.get(index);
        }).collect(Collectors.toList());
        return new Result(path, System.nanoTime() - startTime);
    }

    private static boolean isSorted(List<BufferWikiPage> pages, Comparator<? super BufferWikiPage> comparator) {
        BufferWikiPage earlier = null;
        for (BufferWikiPage page : pages) {
            if (earlier != null) {
                int comp = comparator.compare(earlier, page);
                if (comp > 0) return false;
            }
            earlier = page;
        }
        return true;
    }

    public boolean hasPage(String name) {
        BufferWikiPage page = getPage(name);
        return page != null;
    }

    public List<String> findWildcards(String prefix, int maxMatches) {
        List<String> matches = Lists.newArrayList();
        int ix = findPageByName(prefix);
        final int startingPoint = ix < 0 ? -ix - 1 : ix;
        for (int i = startingPoint; i < pagesByTitle.size(); i++) {
            String title = pagesByTitle.get(i).getTitle();
            if (title.startsWith(prefix) && matches.size() < maxMatches) {
                matches.add(title);
            } else {
                break;
            }
        }
        return matches;
    }

    @Nullable
    private BufferWikiPage getPage(String name) {
        int ix = findPageByName(name);
        if (ix < 0) return null;
        return pagesByTitle.get(ix);
    }

    private int findPageByName(String name) {
        BufferWikiPage target = BufferWikiPage.createTempFor(name);
        return Collections.binarySearch(pagesByTitle, target, BufferWikiPage::compareTitle);
    }

    public static class BadRouteException extends Exception {

        private final boolean startDoesNotExist;
        private final boolean endDoesNotExist;

        private final String startName;
        private final String endName;


        public BadRouteException(boolean startDoesNotExist, boolean endDoesNotExist, @Nullable String startName, @Nullable String endName) {
            this.startDoesNotExist = startDoesNotExist;
            this.endDoesNotExist = endDoesNotExist;
            this.startName = startName;
            this.endName = endName;
        }

        public BadRouteException(String startName, String endName) {
            this(false, false, startName, endName);
        }

        public boolean startExists() {
            return !startDoesNotExist;
        }

        public boolean endExist() {
            return !endDoesNotExist;
        }

        @Nullable
        public String getStartName() {
            return startName;
        }

        @Nullable
        public String getEndName() {
            return endName;
        }

        public boolean noRouteFound() {
            return startExists() && endExist();
        }
    }

    public interface PageMapper {
        void forEachLinkIndex(int pageIndex, IntConsumer c);
    }

    private static class LeanPageMapper implements PageMapper {
        private final HashIntIntMap index;
        private final int[] links;

        private static int ADDITIONAL_INFO = 2;

        private LeanPageMapper(HashIntIntMap index, int[] links) {
            this.index = index;
            this.links = links;
        }

        @Override
        public void forEachLinkIndex(int pageId, IntConsumer c) {
            int val = index.getOrDefault(pageId, -1);
            // Not all pages are linked to.
            if (val < 0) return;
            visitLinks(val, c);
        }

        private void visitLinks(int indexInLinks, IntConsumer c) {
            final int linkCountIndex = indexInLinks + 1;
            final int linkCount = links[linkCountIndex];
            final int start = linkCountIndex + 1;
            final int end = start + linkCount;

            for (int i = start; i < end; i++) {
                c.accept(links[i]);
            }
        }

        private LeanPageMapper reverse() {
            long startTime = System.currentTimeMillis();
            IntIntMap reverseCounts = HashIntIntMaps.newMutableMap(index.size());
            final int[] reverseLinkerCount = { 0 };
            visitLinkArray(links, (linkerId, linkCount, firstLinkIndex, firstPastLastLinkIndex) -> {
                for (int i = firstLinkIndex; i < firstPastLastLinkIndex; i++) {
                    final int targetId = links[i];
                    reverseCounts.addValue(targetId, 1, 0);
                    reverseLinkerCount[0]++;
                }
            });
            HashIntIntMap reversedIndex = HashIntIntMaps.newMutableMap(reverseCounts.size());
            final int[] linkIndex = { 0 };
            int[] reversedLinks = new int[Ints.checkedCast(reverseLinkerCount[0] + ADDITIONAL_INFO * reverseCounts.size())];
            reverseCounts.forEach((IntIntConsumer) (targetId, count) -> {
                final int startLinkIndex = linkIndex[0];
                reversedIndex.put(targetId, startLinkIndex);
                reversedLinks[startLinkIndex] = targetId;
                reversedLinks[startLinkIndex + 1] = 0;
                linkIndex[0] += count + ADDITIONAL_INFO;
            });
            fillLinks(reversedLinks, reversedIndex);
            logger.info(() -> String.format("Took %d ms to create reverse page mapper", System.currentTimeMillis() - startTime));
            return new LeanPageMapper(reversedIndex, reversedLinks);
        }

        private void fillLinks(int[] reversedLinks, HashIntIntMap reversedIndex) {
            visitLinkArray(links, (linkerId, linkCount, firstLinkIndex, firstPastLastLinkIndex) -> {
                for (int i = firstLinkIndex; i < firstPastLastLinkIndex; i++) {
                    final int targetId  = links[i];
                    final int startLinkIndex = reversedIndex.getOrDefault(targetId, Integer.MIN_VALUE);
                    final int reverseLinkIndex = startLinkIndex + 1;
                    final int reverseLinksWritten = reversedLinks[reverseLinkIndex];
                    final int newLinkerIndex = reverseLinkIndex + reverseLinksWritten + 1;
                    reversedLinks[newLinkerIndex] = linkerId;
                    reversedLinks[reverseLinkIndex] = reverseLinksWritten + 1;
                }
            });
        }

        @SuppressWarnings("AssignmentToForLoopParameter")
        private static void visitLinkArray(int[] linkArray, Functions.IntIntIntIntProcedure procedure) {
            int linkerId = -1;
            for (int i = 0; i < linkArray.length;) {
                if (linkerId < 0) {
                    linkerId = linkArray[i];
                    i++;
                } else {
                    final int linkCount = linkArray[i];
                    final int firstLinkIndex = i + 1;
                    final int firstPastLastLinkIndex = firstLinkIndex + linkCount;
                    procedure.apply(linkerId, linkCount, firstLinkIndex, firstPastLastLinkIndex);
                    i = firstPastLastLinkIndex;
                    linkerId = -1;
                }
            }
        }

        private static LeanPageMapper convert(List<BufferWikiPage> pages) {
            long startTime = System.currentTimeMillis();
            long totalLinkCount = pages.stream().mapToLong(BufferWikiPage::getLinkCount).sum();
            int[] links = new int[Ints.checkedCast(totalLinkCount) + ADDITIONAL_INFO * pages.size()];
            HashIntIntMap map = HashIntIntMaps.getDefaultFactory()
                                              .withHashConfig(HashConfig.fromLoads(0.1, 0.5, 0.75))
                                              .newImmutableMap(mapCreator -> {
                                                    final int[] linkIndex = { 0 };
                                                    for (BufferWikiPage page : pages) {
                                                        final int sourceId = page.getId();
                                                        final int linkCount = page.getLinkCount();
                                                        final int startLinkIndex = linkIndex[0];
                                                        links[linkIndex[0]++] = sourceId;
                                                        links[linkIndex[0]++] = linkCount;
                                                        page.forEachLink(linkTarget -> {
                                                            links[linkIndex[0]++] = linkTarget;
                                                        });
                                                        mapCreator.accept(sourceId, startLinkIndex);
                                                    }
                                              }, pages.size());

            logger.info(() -> String.format("Took %d ms to create page mapper", System.currentTimeMillis() - startTime));
            return new LeanPageMapper(map, links);
        }
    }

    private static Comparator<BufferWikiPage> byId() {
        return (o1, o2) -> Integer.compare(o1.getId(), o2.getId());
    }

    public static class Result {

        private final List<BufferWikiPage> route;
        private final long runtimeInNanos;

        private Result(List<BufferWikiPage> route, long runtimeInNanos) {
            this.route = route;
            this.runtimeInNanos = runtimeInNanos;
        }

        List<BufferWikiPage> getRoute() {
            return route;
        }

        public long getRuntime() {
            return TimeUnit.NANOSECONDS.toMillis(runtimeInNanos);
        }

        public String toString() {
            if (route.isEmpty()) return "No route found";
            return Joiner.on(" -> ").join(getRoute().stream().map(p -> quote(p.getTitle())).toArray());
        }
    }

}
