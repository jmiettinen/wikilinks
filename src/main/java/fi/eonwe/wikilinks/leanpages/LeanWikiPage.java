package fi.eonwe.wikilinks.leanpages;

import java.nio.ByteBuffer;
import java.util.function.IntConsumer;

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

    /**
     * @return a read-only view to the buffer that this offers a flyweight view into as an object.
     */
    ByteBuffer getBuffer();

    /**
     *
     * @return the amount of bytes this object uses from the underlying buffer.
     */
    int size();

}
