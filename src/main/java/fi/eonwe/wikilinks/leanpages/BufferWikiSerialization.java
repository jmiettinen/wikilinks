package fi.eonwe.wikilinks.leanpages;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 */
public class BufferWikiSerialization extends AbstractSerialization<BufferWikiPage> {

    private static final int VERSION_NUMBER = 0x823890 + 1;

    public BufferWikiSerialization() {
        super((buffer, offset) -> {
            BufferWikiPage page = new BufferWikiPage(buffer, offset);
            // Cache it here.
            page.getId();
            return page;
        });
    }

    @Override
    public int getMagicCookie() {
        return VERSION_NUMBER;
    }

    @Override
    protected ByteBuffer setByteOrder(ByteBuffer buffer) {
        return buffer.order(ByteOrder.LITTLE_ENDIAN);
    }
}
