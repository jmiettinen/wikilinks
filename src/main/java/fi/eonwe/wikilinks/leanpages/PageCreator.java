package fi.eonwe.wikilinks.leanpages;

import java.nio.ByteBuffer;

/**
 * Create a page from set of bytes
 */
public interface PageCreator<T> {

    T createFrom(ByteBuffer buffer, int offset);

}
