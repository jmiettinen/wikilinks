package fi.eonwe.wikilinks;

import com.google.common.base.Charsets;

import java.nio.ByteBuffer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 */
public class ConvertedWikiPages {

    private final ByteBuffer linksBuffer;
    private final ByteBuffer stringsBuffer;
    private final Page[] pages;


    public ConvertedWikiPages(ByteBuffer pageBuffer, ByteBuffer linksBuffer, ByteBuffer stringsBuffer, int pageCount) {
        this.linksBuffer = linksBuffer.duplicate();
        this.stringsBuffer = stringsBuffer.duplicate();
        this.pages = new Page[pageCount];
        fillPages(pageBuffer);
    }

    private void fillPages(ByteBuffer pageBuffer) {
        ByteBuffer buffer = pageBuffer.duplicate();
        for (int i = 0; i < pages.length; i++) {
            final int id = buffer.getInt();
            final int linkOffset = buffer.getInt();
            final short linkLen = buffer.getShort();
            final int stringOffset = buffer.getInt();
            final short stringLen = buffer.getShort();
            pages[i] = new Page(id, linkOffset, linkLen, stringOffset, stringLen);
        }
    }

    public static int getPageSize() {
        return Integer.BYTES +
                Integer.BYTES +
                Short.BYTES +
                Integer.BYTES +
                Short.BYTES;
    }

    public class Page implements LeanWikiPage<Page> {
        private final int id;
        private final int linkOffset;
        private final short linkCount;
        private final int stringOffset;
        private final short stringByteLen;

        public Page(int id, int linkOffset, short linkCount, int stringOffset, short stringByteLen) {
            this.id = id;
            this.linkOffset = linkOffset;
            this.linkCount = linkCount;
            this.stringOffset = stringOffset;
            this.stringByteLen = stringByteLen;
        }

        public long getId() {
            return this.id;
        }

        @Override
        public int compareTitle(Page o) {
            int comparison = Integer.compare(stringByteLen, o.stringByteLen);
            if (comparison == 0) {
                for (int i = 0; i < stringByteLen; i++) {
                    byte b1 = stringsBuffer.get(stringOffset + i);
                    byte b2 = stringsBuffer.get(o.stringOffset + i);
                    comparison = Byte.compare(b1, b2);
                    if (comparison != 0) return comparison;
                }
            }
            return 0;
        }

        public void forEachLinkInt(IntConsumer consumer) {
            for (int i = 0; i < linkCount; i++) {
                int targetId = linksBuffer.getInt(linkOffset + i);
                consumer.accept(targetId);
            }
        }

        public int[] getLinks() {
            int[] links = new int[linkCount];
            final int[] i = {0};
            forEachLinkInt((targetId) -> {
                links[i[0]++] = targetId;
            });
            return links;
        }

        @Override
        public int getTitleLength() {
            return stringByteLen;
        }

        public String getTitle() {
            byte[] stringBytes = new byte[stringByteLen];
            ByteBuffer src = stringsBuffer.duplicate();
            src.position(stringOffset);
            src.get(stringBytes);
            return new String(stringBytes, Charsets.UTF_8);
        }

        @Override
        public void forEachLink(LongConsumer procedure) {
            forEachLinkInt(procedure::accept);
        }

        @Override
        public int getLinkCount() {
            return linkCount;
        }

        public String toString() {
            return String.format("%s (#%d)", getTitle(), getId());
        }
    }


}
