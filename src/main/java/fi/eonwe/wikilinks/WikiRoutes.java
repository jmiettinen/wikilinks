package fi.eonwe.wikilinks;

import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.LongIntMap;
import com.carrotsearch.hppc.LongIntOpenHashMap;
import com.carrotsearch.hppc.procedures.LongProcedure;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 */
public class WikiRoutes {

    private final List<PackedWikiPage> pages;
    private final LongIntMap idIndexMap;
    private final PackedNameHelper[] sortedNames;
    private final String[] names;

    public WikiRoutes(List<PackedWikiPage> pages) {
        this.pages = pages;
        this.idIndexMap = constructIdIndexMap(pages);
        this.sortedNames = constructSortedNames(pages);
        this.names = Arrays.stream(sortedNames).map(p -> p.getTitle()).limit(500).toArray(String[]::new);
    }

    public List<String> findRoute(String startPage, String endPage) {
        return findRoute(getIndex(startPage), getIndex(endPage));
    }

    private int findNameIndex(String name) {
        return Arrays.binarySearch(Arrays.stream(sortedNames).map(p -> p.getTitle()).toArray(String[]::new), name);
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
        return findRoute(startIx, endIx);
    }

    public List<String> listLinks(String name) {
        int index = getIndex(name);
        if (index < 0) return Collections.emptyList();
        List<String> names = Lists.newArrayList();
        pages.get(index).forEachLink(id -> {
            int idIndex = idIndexMap.getOrDefault(id, -1);
            if (idIndex >= 0) {
                names.add(pages.get(idIndex).getTitle());
            }
        });
        return names;
    }

    private List<String> findRoute(int start, int end) {
        PackedWikiPage startPage = pages.get(start);
        PackedWikiPage endPage = pages.get(end);
        long[] route = RouteFinder.find(startPage.getId(), endPage.getId(), pages, idIndexMap);
        List<String> path = Arrays.asList(Arrays.stream(route).mapToObj(id -> {
            int index = idIndexMap.getOrDefault(id, -1);
            return pages.get(index).getTitle();
        }).toArray(String[]::new));
        return path;
    }

    private static LongIntMap constructIdIndexMap(List<PackedWikiPage> pages) {
        LongIntMap map = new LongIntOpenHashMap(pages.size() * 2, 0.5f);
        for (int i = 0; i < pages.size(); i++) {
            map.put(pages.get(i).getId(), i);
        }
        return map;
    }

    private static PackedNameHelper[] constructSortedNames(List<PackedWikiPage> pages) {
        PackedNameHelper[] names = pages.stream().map(PackedNameHelper::new).toArray(PackedNameHelper[]::new);
        Arrays.sort(names, COMP);
        return names;
    }

    private static interface NameHelper {
        String getTitle();
    }

    private static Comparator<NameHelper> COMP = new Comparator<NameHelper>() {
        @Override
        public int compare(NameHelper o1, NameHelper o2) {
            if (o1.getClass() == o2.getClass()) {
                if (o1.getClass() == StringNameHelper.class) {
                    return doCompare((StringNameHelper) o1, (StringNameHelper) o2);
                } else {
                    PackedNameHelper b1 = (PackedNameHelper) o1;
                    PackedNameHelper b2 = (PackedNameHelper) o2;
                    return b1.page.compareTitle(b2.page);
                }
            } else {
                if (o1.getClass() == PackedNameHelper.class) {
                    return ((PackedNameHelper) o1).page.compareTitle(((StringNameHelper) o2).bytes);
                } else {
                    return -((PackedNameHelper) o2).page.compareTitle(((StringNameHelper) o1).bytes);
                }
            }
        }
    };

    private static int doCompare(StringNameHelper s1, StringNameHelper s2) {
        byte[] b1 = s1.bytes;
        byte[] b2 = s2.bytes;
        int lenComp = Integer.compare(b1.length, b2.length);
        if (lenComp != 0) return lenComp;
        for (int i = 0; i < b1.length; i++) {
            int comp = Byte.compare(b1[i], b2[i]);
            if (comp != 0) return comp;
        }
        return 0;
    }

    private int getIndex(String name) {
        int ix = Arrays.binarySearch(sortedNames, new StringNameHelper(name), COMP);
        if (ix < 0) return -1;
        return idIndexMap.getOrDefault(sortedNames[ix].page.getId(), -1);
    }

    public boolean hasPage(String title) {
        return getIndex(title) >= 0;
    }

    private static class StringNameHelper implements NameHelper {
        private final byte[] bytes;

        public StringNameHelper(String str) {
            this.bytes = str.getBytes(Charsets.UTF_8);
        }

        @Override
        public String getTitle() {
            return new String(bytes, Charsets.UTF_8);
        }
    }

    private static class PackedNameHelper implements NameHelper {
        private final PackedWikiPage page;

        public PackedNameHelper(PackedWikiPage page) {
            this.page = page;
        }

        @Override
        public String getTitle() {
            return page.getTitle();
        }
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
