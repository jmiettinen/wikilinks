package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import net.openhft.koloboke.collect.map.hash.HashLongIntMap;
import net.openhft.koloboke.collect.map.hash.HashLongIntMaps;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 */
public class WikiRoutes<T extends LeanWikiPage<T>> {

    private final List<T> pages;
    private final HashLongIntMap idIndexMap;
    private final LeanWikiPage[] sortedNames;

    private static final Logger logger = Logger.getLogger(WikiRoutes.class.getCanonicalName());
    static {
        logger.setLevel(Level.WARNING);
    }

    public WikiRoutes(List<T> pages) {
        this.pages = pages;
        this.idIndexMap = constructIdIndexMap(pages);
        this.sortedNames = constructSortedNames(pages);
    }

    public List<String> findRoute(String startPage, String endPage) {
        return findRoute(getPage(startPage), getPage(endPage));
    }

    public List<String> findRandomRoute() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (sortedNames.length == 1) {
            return Lists.newArrayList(sortedNames[0].getTitle());
        } else if (sortedNames.length == 0) {
            return Collections.emptyList();
        }
        int startIx = rng.nextInt(sortedNames.length);
        int endIx;
        do {
            endIx = rng.nextInt(sortedNames.length);
        } while (endIx == startIx);
        return findRoute(sortedNames[startIx], sortedNames[endIx]);
    }

    public List<String> listLinks(String name) {
        LeanWikiPage page = getPage(name);
        if (page == null) return Collections.emptyList();
        List<String> names = Lists.newArrayList();
        page.forEachLink(id -> {
            int idIndex = idIndexMap.getOrDefault(id, -1);
            if (idIndex >= 0) {
                names.add(pages.get(idIndex).getTitle());
            }
        });
        return names;
    }

    private List<String> findRoute(LeanWikiPage startPage, LeanWikiPage endPage) {
        long[] route = RouteFinder.find(startPage.getId(), endPage.getId(), pages, idIndexMap);
        List<String> path = Arrays.asList(Arrays.stream(route).mapToObj(id -> {
            int index = idIndexMap.getOrDefault(id, -1);
            return pages.get(index).getTitle();
        }).toArray(String[]::new));
        return path;
    }

    private static HashLongIntMap constructIdIndexMap(List<? extends LeanWikiPage<?>> pages) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting to construct id -> index map");
        HashLongIntMap map = HashLongIntMaps.newImmutableMap(mapCreator -> {
            for (int i = 0; i < pages.size(); i++) {
                mapCreator.accept(pages.get(i).getId(), i);
            }
        }, pages.size());
        logger.info(() -> String.format("Took %d ms to create id -> index map", System.currentTimeMillis() - startTime));
        return map;
    }

    private static LeanWikiPage[] constructSortedNames(List<? extends LeanWikiPage<?>> pages) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting to sort names");
        LeanWikiPage[] pagesArray = pages.toArray(new LeanWikiPage[pages.size()]);
        Arrays.sort(pagesArray, LeanWikiPage::compareTitle);
        logger.info(() -> String.format("Took %d ms to sort names", System.currentTimeMillis() - startTime));
        return pagesArray;
    }

    private LeanWikiPage getPage(String name) {
        LeanWikiPage p = sortedNames[0];
        int ix = Arrays.binarySearch(sortedNames, p.createTempFor(name), LeanWikiPage::compareTitle);
        if (ix < 0) return null;
        return sortedNames[ix];
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
    }

}
