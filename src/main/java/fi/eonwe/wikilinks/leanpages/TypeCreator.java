package fi.eonwe.wikilinks.leanpages;

import java.nio.ByteBuffer;

/**
 */
public interface TypeCreator<T> {

    T createFrom(ByteBuffer buffer, int offset);

}
