package fi.eonwe.wikilinks;

import com.google.common.base.Charsets;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 */
public class ConvertedWikiPages implements BufferHolder {

    private final ByteBuffer linksBuffer;
    private final ByteBuffer stringsBuffer;
    private final Page[] pages;

    @Override
    public ByteBuffer getLinks() {
        return linksBuffer;
    }

    @Override
    public ByteBuffer getStrings() {
        return stringsBuffer;
    }

    public ConvertedWikiPages(ByteBuffer pageBuffer, ByteBuffer linksBuffer, ByteBuffer stringsBuffer, int pageCount) {
        this.linksBuffer = linksBuffer.duplicate();
        this.stringsBuffer = stringsBuffer.duplicate();
        this.pages = new Page[pageCount];
        fillPages(pageBuffer);
    }

    public List<Page> getPages() {
        return Arrays.asList(pages);
    }

    public void writeLinksTo(ByteBuffer out) {
        out.put(linksBuffer.duplicate());
    }

    public void writeStringsTo(ByteBuffer out) {
        out.put(stringsBuffer.duplicate());
    }

    private void fillPages(ByteBuffer pageBuffer) {
        ByteBuffer buffer = pageBuffer.duplicate();
        for (int i = 0; i < pages.length; i++) {
            final int id = buffer.getInt();
            final int linkOffset = buffer.getInt();
            final short linkLen = buffer.getShort();
            final int stringOffset = buffer.getInt();
            final short stringLen = buffer.getShort();
            pages[i] = new Page(id, linkOffset, linkLen, stringOffset, stringLen, this);
        }
    }

    public static int getPageSize() {
        return Integer.BYTES +
                Integer.BYTES +
                Short.BYTES +
                Integer.BYTES +
                Short.BYTES;
    }

    public static class Page implements LeanWikiPage<Page> {
        private final int id;
        private final int linkOffset;
        private final short linkCount;
        private final int stringOffset;
        private final short stringByteLen;
        private final BufferHolder holder;

        public Page(int id, int linkOffset, short linkCount, int stringOffset, short stringByteLen, BufferHolder holder) {
            this.id = id;
            this.linkOffset = linkOffset;
            this.linkCount = linkCount;
            this.stringOffset = stringOffset;
            this.stringByteLen = stringByteLen;
            this.holder = holder;
        }

        public long getId() {
            return this.id;
        }

        @Override
        public int compareTitle(Page o) {
            int comparison = Integer.compare(stringByteLen, o.stringByteLen);
            ByteBuffer buf1 = holder.getStrings();
            ByteBuffer buf2 = o.holder.getLinks();
            if (comparison == 0) {
                for (int i = 0; i < stringByteLen; i++) {
                    byte b1 = buf1.get(stringOffset + i);
                    byte b2 = buf2.get(o.stringOffset + i);
                    comparison = Byte.compare(b1, b2);
                    if (comparison != 0) return comparison;
                }
            }
            return 0;
        }

        @Override
        public Page createTempFor(String title) {
            byte[] bytes = title.getBytes(Charsets.UTF_8);
            BufferHolder h = new BufferHolder() {
                ByteBuffer strings = ByteBuffer.wrap(bytes);
                ByteBuffer links = ByteBuffer.wrap(new byte[0]);

                @Override
                public ByteBuffer getLinks() { return links; }

                @Override
                public ByteBuffer getStrings() { return strings; }
            };
            return new Page(0, 0, (short) 0, 0, (short) bytes.length, h);
        }

        public void forEachLinkInt(IntConsumer consumer) {
            ByteBuffer buf = holder.getLinks();
            for (int i = 0; i < linkCount; i++) {
                int targetId = buf.getInt(linkOffset + i);
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
            ByteBuffer src = holder.getStrings().duplicate();
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
