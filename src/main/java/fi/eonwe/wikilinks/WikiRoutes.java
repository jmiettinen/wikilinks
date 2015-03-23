package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import fi.eonwe.wikilinks.leanpages.BufferWikiPage;
import fi.eonwe.wikilinks.leanpages.LeanWikiPage;
import net.openhft.koloboke.collect.map.hash.HashIntIntMap;
import net.openhft.koloboke.collect.map.hash.HashIntIntMaps;
import net.openhft.koloboke.collect.map.hash.HashLongIntMap;
import net.openhft.koloboke.collect.map.hash.HashLongIntMaps;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 */
public class WikiRoutes {

    private final List<BufferWikiPage> pages;
    private final HashIntIntMap idIndexMap;

    private static final Logger logger = Logger.getLogger(WikiRoutes.class.getCanonicalName());
    static {
        logger.setLevel(Level.WARNING);
    }

    public WikiRoutes(List<BufferWikiPage> pages) {
        this.pages = constructSortedNames(pages);
        this.idIndexMap = constructIdIndexMap(pages);
    }

    public Result findRoute(String startPage, String endPage) throws BadRouteException {
        LeanWikiPage<?> startPageObj = getPage(startPage);
        LeanWikiPage<?> endPageObj = getPage(endPage);
        if (startPageObj == null || endPageObj == null) {
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

    public List<String> listLinks(String name) {
        LeanWikiPage page = getPage(name);
        if (page == null) return Collections.emptyList();
        List<String> names = Lists.newArrayList();
        page.forEachLink(id -> {
            int idIndex = getIndex(id);
            if (idIndex >= 0) {
                names.add(pages.get(idIndex).getTitle());
            }
        });
        return names;
    }

    private Result findRoute(LeanWikiPage startPage, LeanWikiPage endPage) {
        long startTime = System.currentTimeMillis();
        PageMapper<BufferWikiPage> mapper = createMapper();
        int[] route = RouteFinder.find(startPage.getId(), endPage.getId(), mapper);
        List<BufferWikiPage> path = Arrays.asList(Arrays.stream(route).mapToObj(id -> {
            int index = getIndex(id);
            return pages.get(index);
        }).toArray(BufferWikiPage[]::new));
        return new Result(path, System.currentTimeMillis() - startTime);
    }

    private static HashIntIntMap constructIdIndexMap(List<? extends LeanWikiPage<?>> pages) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting to construct id -> index map");
        HashIntIntMap map = HashIntIntMaps.getDefaultFactory()
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

    private static void doSanityCheck(HashLongIntMap map, Iterable<? extends LeanWikiPage<?>> pages) {
        for (LeanWikiPage<?> page : pages) {
            page.forEachLink(value -> {
                int target = map.getOrDefault(value, -1);
                if (target < 0) {
                    throw new IllegalStateException("All links should map somewhere!");
                }
            });
        }
    }

    private static <T extends LeanWikiPage<T>> List<T> constructSortedNames(List<T> pages) {
        long startTime = System.currentTimeMillis();
        if (!isSorted(pages)) {
            logger.info("Starting to sort names");
            Collections.sort(pages);
            logger.info(() -> String.format("Took %d ms to sort names", System.currentTimeMillis() - startTime));
        }
        return pages;
    }

    private static <T extends LeanWikiPage<T>> boolean isSorted(List<T> pagesArray) {
        for (int i = 0; i < pagesArray.size() - 1; i++) {
            int comp = pagesArray.get(i).compareTitle(pagesArray.get(i + 1));
            if (comp > 0) return false;
        }
        return true;
    }

    public boolean hasPage(String name) {
        LeanWikiPage<?> page = getPage(name);
        return page != null;
    }

    public List<String> findWildcards(String prefix, int maxMatches) {
        List<String> matches = Lists.newArrayList();
        LeanWikiPage p = pages.get(0);
        int ix = Collections.binarySearch(pages, p.createTempFor(prefix), LeanWikiPage::compareTitle);
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

    private LeanWikiPage getPage(String name) {
        LeanWikiPage p = pages.get(0);
        int ix = Collections.binarySearch(pages, p.createTempFor(name), LeanWikiPage::compareTitle);
        if (ix < 0) return null;
        return pages.get(ix);
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

    private int getIndex(int id) {
        return idIndexMap.getOrDefault(shift(id), -1);
    }

    public static interface PageMapper<T extends LeanWikiPage<T>> {
        T getForId(int id);
    }

    private PageMapper<BufferWikiPage> createMapper() {
        return new PageMapper<BufferWikiPage>() {
            @Override
            public BufferWikiPage getForId(int id) {
                int ix = getIndex(id);
                if (ix < 0 || ix >= pages.size()) {
                    return null;
                }
                return pages.get(ix);
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

    }

}
