package fi.eonwe.wikilinks;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import fi.eonwe.wikilinks.leanpages.BufferWikiPage;
import fi.eonwe.wikilinks.leanpages.OrderedPage;
import net.openhft.koloboke.collect.hash.HashConfig;
import net.openhft.koloboke.collect.map.hash.HashIntIntMap;
import net.openhft.koloboke.collect.map.hash.HashIntIntMaps;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import static fi.eonwe.wikilinks.Helpers.quote;

/**
 */
public class WikiRoutes {

    private final List<BufferWikiPage> pages;
//    private final HashIntIntMap idIndexMap;
    private final OrderedPage[] leanPages;

    private static final Logger logger = Logger.getLogger(WikiRoutes.class.getCanonicalName());
    static {
        logger.setLevel(Level.WARNING);
    }

    public WikiRoutes(List<BufferWikiPage> pages) {
        this.pages = constructSortedNames(pages);
//        this.idIndexMap = constructIdIndexMap(pages);
        this.leanPages = constructLeanArray(pages);
    }

    public Result findRoute(String startPage, String endPage) throws BadRouteException {
        int startPageObj = getPageIndex(startPage);
        int endPageObj = getPageIndex(endPage);
        if (startPageObj == -1 || endPageObj == -1) {
            throw new BadRouteException(startPage == null, endPage == null, startPage, endPage);
        }
        return findRoute(startPageObj, endPageObj);
    }

    public String getRandomPage() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (pages.size() == 1) {
            return pages.get(0).getTitle();
        } else if (pages.isEmpty()) {
            return null;
        }
        int i = rng.nextInt(pages.size());
        return pages.get(i).getTitle();
    }

    private Result findRoute(int startPage, int endPage) {
        long startTime = System.currentTimeMillis();
        PageMapper mapper = createMapper();
        int[] routeIndices = RouteFinder.find(startPage, endPage, mapper);
        List<BufferWikiPage> path = Arrays.asList(Arrays.stream(routeIndices).mapToObj(i -> leanPages[i].getPage()).toArray(BufferWikiPage[]::new));
        return new Result(path, System.currentTimeMillis() - startTime);
    }

    private static HashIntIntMap constructIdIndexMap(List<BufferWikiPage> pages) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting to construct id -> index map");
        HashIntIntMap map = HashIntIntMaps.getDefaultFactory()
                .withHashConfig(HashConfig.fromLoads(0.1, 0.5, 0.75))
                .withKeysDomain(Integer.MIN_VALUE, -1)
                .newImmutableMap(mapCreator -> {
                    for (int i = 0; i < pages.size(); i++) {
                        final int shiftedId = shift(pages.get(i).getId());
                        mapCreator.accept(shiftedId, i);
                    }
                }, pages.size());
//        doSanityCheck(map, pages);
        logger.info(() -> String.format("Took %d ms to create id -> index map", System.currentTimeMillis() - startTime));
        return map;
    }

    private static OrderedPage[] constructLeanArray(List<BufferWikiPage> pages) {
        logger.info("Starting to construct index-based map");
        long startTime = System.currentTimeMillis();
        OrderedPage[] leanPages = new OrderedPage[pages.size()];
        HashIntIntMap map = constructIdIndexMap(pages);
        IntFunction<Integer> mapper = value -> map.getOrDefault(shift(value), -1);
        for (BufferWikiPage page : pages) {
            int tableIndex = mapper.apply(page.getId());
            leanPages[tableIndex] = OrderedPage.convert(page, mapper);
        }
        logger.info(() -> String.format("Took %d ms to create index-based map", System.currentTimeMillis() - startTime));
        return leanPages;
    }

    private static List<BufferWikiPage> constructSortedNames(List<BufferWikiPage> pages) {
        long startTime = System.currentTimeMillis();
        if (!isSorted(pages)) {
            logger.info("Starting to sort names");
            Collections.sort(pages);
            logger.info(() -> String.format("Took %d ms to sort names", System.currentTimeMillis() - startTime));
        }
        return pages;
    }

    private static boolean isSorted(List<BufferWikiPage> pagesArray) {
        for (int i = 0; i < pagesArray.size() - 1; i++) {
            int comp = pagesArray.get(i).compareTitle(pagesArray.get(i + 1));
            if (comp > 0) return false;
        }
        return true;
    }

    public boolean hasPage(String name) {
        BufferWikiPage page = getPage(name);
        return page != null;
    }

    public List<String> findWildcards(String prefix, int maxMatches) {
        List<String> matches = Lists.newArrayList();
        BufferWikiPage p = pages.get(0);
        int ix = Collections.binarySearch(pages, p.createTempFor(prefix), BufferWikiPage::compareTitle);
        final int startingPoint = ix < 0 ? -ix - 1 : ix;
        for (int i = startingPoint; i < pages.size(); i++) {
            String title = pages.get(i).getTitle();
            if (title.startsWith(prefix) && matches.size() < maxMatches) {
                matches.add(title);
            } else {
                break;
            }
        }
        return matches;
    }

    private BufferWikiPage getPage(String name) {
        int ix = getPageIndex(name);
        if (ix < 0) return null;
        return pages.get(ix);
    }

    private int getPageIndex(String name) {
        BufferWikiPage p = pages.get(0);
        int ix = Collections.binarySearch(pages, p.createTempFor(name), BufferWikiPage::compareTitle);
        if (ix < 0) return -1;
        return ix;
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

    private static int shift(int val) {
        return -val - 1;
    }

    public static interface PageMapper {
        OrderedPage getForIndex(int id);
        int getSize();
    }

    private PageMapper createMapper() {
        return new PageMapper() {
            @Override
            public OrderedPage getForIndex(int index) {
                return leanPages[index];
            }

            @Override
            public int getSize() {
                return leanPages.length;
            }
        };
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
