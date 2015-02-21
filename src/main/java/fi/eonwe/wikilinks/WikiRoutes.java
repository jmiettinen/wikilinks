package fi.eonwe.wikilinks;

import com.carrotsearch.hppc.LongIntMap;
import com.carrotsearch.hppc.LongIntOpenHashMap;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 */
public class WikiRoutes {

    private final List<PackedWikiPage> pages;
    private final LongIntMap idIndexMap;
    private final NameHelper[] sortedNames;

    public WikiRoutes(List<PackedWikiPage> pages) {
        this.pages = pages;
        this.idIndexMap = constructIdIndexMap(pages);
        this.sortedNames = constructSortedNames(pages);
    }

    public List<String> findRoute(String startPage, String endPage) {
        List<String> pages = Lists.newArrayList();

        return pages;
    }

    private static LongIntMap constructIdIndexMap(List<PackedWikiPage> pages) {
        LongIntMap map = new LongIntOpenHashMap(pages.size() * 2, 0.5f);
        for (int i = 0; i < pages.size(); i++) {
            map.put(pages.get(i).getId(), i);
        }
        return map;
    }

    private static NameHelper[] constructSortedNames(List<PackedWikiPage> pages) {
        NameHelper[] names = pages.stream().map(PackedNameHelper::new).toArray(NameHelper[]::new);
        Arrays.sort(names, COMP);
        return names;
    }

    public String[] getNames() {
        return Arrays.stream(sortedNames).map(NameHelper::getTitle).toArray(String[]::new);
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

}
