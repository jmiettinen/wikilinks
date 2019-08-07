package fi.eonwe.wikilinks.leanpages;

import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import com.google.common.primitives.UnsignedBytes;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.function.IntConsumer;

/**
 */
public class BufferWikiPage implements LeanWikiPage<BufferWikiPage> {

    /**
     * All these offsets are offsets to the offset-field. I.e. the real offset for links is this.offset + LINKS_OFFSET.
     */
    private static final int ID_OFFSET = 0;
    private static final int LINK_SIZE_OFFSET = ID_OFFSET + Integer.BYTES;
    private static final int LINKS_OFFSET = LINK_SIZE_OFFSET + Short.BYTES;
    private static final int TITLE_SIZE_OFFSET = LINKS_OFFSET + Integer.BYTES;
    private static final int TITLE_OFFSET = TITLE_SIZE_OFFSET + Short.BYTES;

    private final ByteBuffer data;
    private final int offset;
    private int id = Integer.MIN_VALUE;

    private static final int[] EMPTY_ARRAY = new int[0];

    private static int getHeaderSize() {
        return Integer.BYTES +
                Short.BYTES +
                Integer.BYTES +
                Short.BYTES +
                Integer.BYTES;
    }

    public BufferWikiPage(ByteBuffer buffer, int offset) {
        this.data = buffer;
        this.offset = offset;
    }

    private static ByteBuffer bufferFrom(int id, int[] links, String title, boolean isRedirect) {
        byte[] stringBytes = title.getBytes(Charsets.UTF_8);
        final int linksSize = Integer.BYTES * links.length;
        final int stringSize = Byte.BYTES * stringBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(getHeaderSize() + linksSize + stringSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(Ints.checkedCast(isRedirect ? -id : id)); // ID_OFFSET
        buffer.putShort(Shorts.checkedCast(links.length)); // LINK_SIZE_OFFSET
        buffer.putInt(getHeaderSize()); // LINKS_OFFSET
        buffer.putShort(Shorts.checkedCast(stringBytes.length)); // TITLE_SIZE_OFFSET
        buffer.putInt(getHeaderSize() + linksSize); // TITLE_OFFSET
        for (int link : links) {
            buffer.putInt(link);
        }
        buffer.put(stringBytes);
        buffer.flip();
        return buffer;
    }

    public static BufferWikiPage createFrom(int id, int[] links, String title, boolean isRedirect) {
        return new BufferWikiPage(bufferFrom(id, links, title, isRedirect), 0);
    }

    @Override
    public int getTitleLength() {
        return data.getShort(offset + TITLE_SIZE_OFFSET);
    }

    @Override
    public String getTitle() {
        int stringSize = getTitleLength();
        int offset = relativeOffset(data.getInt(relativeOffset(TITLE_OFFSET)));
        byte[] dataArray;
        if (data.hasArray()) {
            dataArray = data.array();
            offset += data.arrayOffset();
            return new String(dataArray, offset, stringSize, Charsets.UTF_8);
        } else {
            dataArray = new byte[stringSize];
            ByteBuffer copy = data.duplicate();
            copy.position(offset);
            copy.get(dataArray);
            return new String(dataArray, 0, stringSize, Charsets.UTF_8);
        }
    }

    private int relativeOffset(int offset) {
        return this.offset + offset;
    }

    @Override
    public void forEachLink(IntConsumer procedure) {
        final int size = getLinkCount();
        int linkOffset = relativeOffset(data.getInt(relativeOffset(LINKS_OFFSET)));
        for (int i = 0; i < size; i++, linkOffset += Integer.BYTES) {
            procedure.accept(data.getInt(linkOffset));
        }
    }

    @Override
    public int getLinkCount() {
        return data.getShort(relativeOffset(LINK_SIZE_OFFSET));
    }

    private int getRawId() {
        return data.getInt(relativeOffset(ID_OFFSET));
    }

    @Override
    public int getId() {
        fetchAndStoreId();
        int id = this.id;
        return id < 0 ? -id : id;
    }

    void fetchAndStoreId() {
        int id = this.id;
        if (id == Integer.MIN_VALUE) {
            this.id = getRawId();
        }
    }

    @Override
    public int size() {
        int size = getHeaderSize() + getLinkCount() * Integer.BYTES + getTitleLength() * Byte.BYTES;
        return size;
    }

    @Override
    public int compareTitle(BufferWikiPage that) {
        ByteBuffer d1 = data;
        ByteBuffer d2 = that.data;

        final int s1 = getTitleLength();
        final int s2 = that.getTitleLength();

        final int minSize = Math.min(s1, s2);

        final int s1offset = this.relativeOffset(d1.getInt(this.relativeOffset(TITLE_OFFSET)));
        final int s2offset = that.relativeOffset(d2.getInt(that.relativeOffset(TITLE_OFFSET)));
        int comparison = 0;
        for (int i = 0; i < minSize; i++) {
            byte b1 = d1.get(s1offset + i);
            byte b2 = d2.get(s2offset + i);
            comparison = UnsignedBytes.compare(b1, b2);
            if (comparison != 0) break;
        }
        if (comparison == 0) comparison = Integer.compare(s1, s2);
        return comparison;
    }

    public static BufferWikiPage createTempFor(String title) {
        return createFrom(0, EMPTY_ARRAY, title, false);
    }

    @Override
    public ByteBuffer getBuffer() {
        ByteBuffer copy = data.asReadOnlyBuffer();
        copy.position(offset);
        copy.limit(offset + size());
        return copy;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BufferWikiPage) {
            BufferWikiPage other = (BufferWikiPage) obj;
            int len1 = size();
            int len2 = size();
            if (len1 != len2) return false;
            int i1 = offset;
            int i2 = other.offset;
            final ByteBuffer otherData = other.data;
            // Compare by strides of long. To be really performative, this probably should do aligned access.
            for (; i1 + Long.BYTES < offset + len1; i1 += Long.BYTES, i2 += Long.BYTES) {
                long v1 = data.getLong(i1);
                long v2 = otherData.getLong(i2);
                if (v1 != v2) return false;
            }
            for (; i1 < offset + len1; i1++, i2++) {
                if (data.get(i1) != otherData.get(i2)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isRedirect() {
        return getRawId() < 0;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(getId());
    }

    @Override
    public String toString() {
        return String.format("\"%s\" (#%d), %d links", getTitle(), getId(), getLinkCount());
    }

}
