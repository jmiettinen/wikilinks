package fi.eonwe.wikilinks.leanpages;

import com.google.common.collect.Lists;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 */
public interface LeanWikiPage<T extends LeanWikiPage<T>> extends Comparable<T> {

    int getTitleLength();
    String getTitle();
    void forEachLink(IntConsumer procedure);
    int getLinkCount();
    int getId();

    default boolean isRedirect() { return false; }

    int compareTitle(T other);

    default int compareTo(T other) {
        return compareTitle(other);
    }

    default int[] getLinks() {
        int[] links = new int[getLinkCount()];
        final int[] i = {0};
        forEachLink(id -> links[i[0]++] = id);
        return links;
    }

    T createTempFor(String title);
    ByteBuffer getBuffer();
    int size();

}
