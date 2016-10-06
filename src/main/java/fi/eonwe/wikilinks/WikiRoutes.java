package fi.eonwe.wikilinks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import fi.eonwe.wikilinks.leanpages.BufferWikiPage;
import net.openhft.koloboke.collect.hash.HashConfig;
import net.openhft.koloboke.collect.map.IntIntMap;
import net.openhft.koloboke.collect.map.hash.HashIntIntMap;
import net.openhft.koloboke.collect.map.hash.HashIntIntMaps;
import net.openhft.koloboke.collect.set.hash.HashIntSet;
import net.openhft.koloboke.collect.set.hash.HashIntSets;
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
        logger.setLevel(Level.INFO);
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

    private void sortIfNeeded(List<BufferWikiPage> list, String name, Comparator<? super BufferWikiPage> comp) {
        long startTime = System.currentTimeMillis();
        if (!isSorted(list, comp)) {
            logger.info("Starting to sort by " + name);
            Collections.sort(list, comp);
            logger.info(String.format("Took %d ms to sort by %s", System.currentTimeMillis() - startTime, name));
        }
    }

    private Result findRoute(BufferWikiPage startPage, BufferWikiPage endPage) {
        long startTime = System.currentTimeMillis();
        int[] routeIds = RouteFinder.find(startPage.getId(), endPage.getId(), mapper, reverseMapper);
        List<BufferWikiPage> path = Arrays.stream(routeIds).mapToObj(id -> {
            BufferWikiPage needle = BufferWikiPage.createFrom(id, new int[0], "ignored", false);
            int index = Collections.binarySearch(pagesById, needle, byId());
            return pagesById.get(index);
        }).collect(Collectors.toList());
        return new Result(path, System.currentTimeMillis() - startTime);
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


        public BadRouteException(boolean startDoesNotExist, boolean endDoesNotExist, String startName, String endName) {
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

        public String getStartName() {
            return startName;
        }

        public String getEndName() {
            return endName;
        }

        public boolean noRouteFound() {
            return startExists() && endExist();
        }
    }

    public interface PageMapper {
        void forEachLinkIndex(int pageIndex, IntConsumer c);
        int getSize();
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
            final int linkCountIndex = val + 1;
            final int linkCount = unshift(links[linkCountIndex]);
            final int start = linkCountIndex + 1;
            final int end = start + linkCount;

            for (int i = start; i < end; i++) {
                c.accept(links[i]);
            }
        }

        private LeanPageMapper reverse() {
            long startTime = System.currentTimeMillis();
            IntIntMap reverseCounts = HashIntIntMaps.newMutableMap(index.size());
            int reverseLinkerCount = 0;
            for (int targetIdOrCount : links) {
                if (targetIdOrCount >= 0) {
                    reverseCounts.addValue(targetIdOrCount, 1, 0);
                    reverseLinkerCount++;
                }
            }
            HashIntIntMap reversedIndex = HashIntIntMaps.newMutableMap(reverseCounts.size());
            final int[] linkIndex = { 0 };
            int[] reversedLinks = new int[Ints.checkedCast(reverseLinkerCount + ADDITIONAL_INFO * reverseCounts.size())];
            reverseCounts.forEach((IntIntConsumer) (targetId, count) -> {
                final int startLinkIndex = linkIndex[0];
                reversedIndex.put(targetId, startLinkIndex);
                reversedLinks[startLinkIndex] = shift(targetId);
                reversedLinks[startLinkIndex + 1] = shift(0);
                linkIndex[0] += count + ADDITIONAL_INFO;
            });
            int linkerId = -1;
            int linkCount = -1;
            int readLinkCount = 0;
            for (int val : links) {
                if (linkerId < 0) {
                    linkerId = unshift(val);
                } else if (linkCount < 0) {
                    linkCount = unshift(val);
                } else {
                    final int targetId = val;
                    final int startLinkIndex = reversedIndex.getOrDefault(targetId, Integer.MIN_VALUE);
                    final int reverseLinkIndex = startLinkIndex + 1;
                    final int reverseLinksWritten = unshift(reversedLinks[reverseLinkIndex]);
                    final int newLinkerIndex = reverseLinkIndex + reverseLinksWritten + 1;
                    reversedLinks[newLinkerIndex] = linkerId;
                    reversedLinks[reverseLinkIndex] = shift(reverseLinksWritten + 1);
                    readLinkCount++;
                }
                if (readLinkCount == linkCount) {
                    readLinkCount = 0;
                    linkerId = -1;
                    linkCount = -1;
                }
            }
            logger.info(() -> String.format("Took %d ms to create reverse page mapper", System.currentTimeMillis() - startTime));
            return new LeanPageMapper(reversedIndex, reversedLinks);
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
                                                        links[linkIndex[0]++] = shift(sourceId);
                                                        links[linkIndex[0]++] = shift(linkCount);
                                                        page.forEachLink(linkTarget -> {
                                                            links[linkIndex[0]++] = linkTarget;
                                                        });
                                                        mapCreator.accept(sourceId, startLinkIndex);
                                                    }
                                              }, pages.size());

            logger.info(() -> String.format("Took %d ms to create page mapper", System.currentTimeMillis() - startTime));
            return new LeanPageMapper(map, links);
        }

        private static int shift(int val) { return -val - 1; }

        private static int unshift(int val) { return shift(val); }

        @Override
        public int getSize() {
            return index.size();
        }
    }

    private static void checkData(List<BufferWikiPage> leanPages) {
        HashIntSet seenPages = HashIntSets.newMutableSet(leanPages.size());
        HashIntSet seenLinkTargets = HashIntSets.newMutableSet(leanPages.size());
        for (BufferWikiPage page : leanPages) {
            seenPages.add(page.getId());
            page.forEachLink(seenLinkTargets::add);
        }
        if (!seenPages.containsAll(seenLinkTargets)) {
            throw new IllegalStateException();
        }
    }

    private static Comparator<BufferWikiPage> byId() {
        return (o1, o2) -> Integer.compare(o1.getId(), o2.getId());
    }

    public static class Result {

        private final List<BufferWikiPage> route;
        private final long runtime;

        private Result(List<BufferWikiPage> route, long runtime) {
            this.route = route;
            this.runtime = runtime;
        }

        List<BufferWikiPage> getRoute() {
            return route;
        }

        public long getRuntime() {
            return runtime;
        }

        public String toString() {
            if (route.isEmpty()) return "No route found";
            return Joiner.on(" -> ").join(getRoute().stream().map(p -> quote(p.getTitle())).toArray());
        }
    }

}
