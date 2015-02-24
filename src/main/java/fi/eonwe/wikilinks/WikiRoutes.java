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
public class WikiRoutes {

    private final List<PackedWikiPage> pages;
    private final HashLongIntMap idIndexMap;
    private final PackedWikiPage[] sortedNames;

    private static final Logger logger = Logger.getLogger(WikiRoutes.class.getCanonicalName());
    static {
        logger.setLevel(Level.WARNING);
    }

    public WikiRoutes(List<PackedWikiPage> pages) {
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
        PackedWikiPage page = getPage(name);
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

    private List<String> findRoute(PackedWikiPage startPage, PackedWikiPage endPage) {
        long[] route = RouteFinder.find(startPage.getId(), endPage.getId(), pages, idIndexMap);
        List<String> path = Arrays.asList(Arrays.stream(route).mapToObj(id -> {
            int index = idIndexMap.getOrDefault(id, -1);
            return pages.get(index).getTitle();
        }).toArray(String[]::new));
        return path;
    }

    private static HashLongIntMap constructIdIndexMap(List<PackedWikiPage> pages) {
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

    private static PackedWikiPage[] constructSortedNames(List<PackedWikiPage> pages) {
        long startTime = System.currentTimeMillis();
        logger.info("Starting to sort names");
        PackedWikiPage[] pagesArray = pages.toArray(new PackedWikiPage[pages.size()]);
        Arrays.sort(pagesArray, PackedWikiPage::compareTitle);
        logger.info(() -> String.format("Took %d ms to sort names", System.currentTimeMillis() - startTime));
        return pagesArray;
    }

    private PackedWikiPage getPage(String name) {
        int ix = Arrays.binarySearch(sortedNames, new PackedWikiPage(Integer.MAX_VALUE, new long[0], name), PackedWikiPage::compareTitle);
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
