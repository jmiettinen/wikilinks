package fi.eonwe.wikilinks;

import java.nio.ByteBuffer;

/**
 */
public interface BufferHolder {

    ByteBuffer getLinks();
    ByteBuffer getStrings();

}
