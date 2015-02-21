package fi.eonwe.wikilinks;

import com.google.common.base.Charsets;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 */
public class PackedWikiPage {

    private static final int ID_OFFSET = 0;
    private static final int LINK_SIZE_OFFSET = ID_OFFSET + Long.BYTES;
    private static final int LINKS_OFFSET = LINK_SIZE_OFFSET + Integer.BYTES;

    private static final long[] EMPTY_ARRAY = new long[0];

    private final ByteBuffer data;
    private final int offset;

    public PackedWikiPage(long id, long[] links, String title) {
        this.data = bufferFrom(id, links, title);
        this.offset = 0;
    }

    public PackedWikiPage(ByteBuffer data, int offset) {
        this.data = data;
        this.offset = offset;
    }

    private static ByteBuffer bufferFrom(long id, long[] links, String title) {
        byte[] stringBytes = title.getBytes(Charsets.UTF_8);
        final int idSize = Long.BYTES;
        final int linksSize = Integer.BYTES + Long.BYTES * links.length;
        final int stringSize = Integer.BYTES + Byte.BYTES * stringBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(idSize + linksSize + stringSize);
        buffer.putLong(id);
        buffer.putInt(links.length);
        for (long link : links) {
            buffer.putLong(link);
        }
        buffer.putInt(stringBytes.length);
        buffer.put(stringBytes);
        buffer.flip();
        return buffer;
    }

    private static int getStringSizeOffset(ByteBuffer data, int baseOffset) {
        int linkCount = data.getInt(baseOffset + LINK_SIZE_OFFSET);
        return baseOffset + LINKS_OFFSET + linkCount * Long.BYTES;
    }

    private static long[] getLinks(ByteBuffer data, int baseOffset) {
        int linkCount = data.getInt(baseOffset + LINK_SIZE_OFFSET);
        if (linkCount == 0) return EMPTY_ARRAY;
        long[] links = new long[linkCount];
        for (int offset = baseOffset + LINKS_OFFSET, i = 0; i < linkCount; i++, offset += Long.BYTES) {
            links[i] = data.getLong(offset);
        }
        return links;
    }

    private static String getTitle(ByteBuffer data, int baseOffset) {
        int stringSizeOffset = getStringSizeOffset(data, baseOffset);
        int stringSize = data.getInt(stringSizeOffset);
        int offset = stringSizeOffset + Integer.BYTES;
        byte[] dataArray;
        if (data.hasArray()) {
            dataArray = data.array();
            offset += data.arrayOffset();
        } else {
            dataArray = new byte[stringSize];
            offset = 0;
            data.duplicate().get(dataArray, offset, stringSize);
        }
        return new String(dataArray, offset, stringSize, Charsets.UTF_8);
    }

    public long getId() { return data.getLong(offset); }

    public long[] getLinks() { return getLinks(data, offset); }

    public String getTitle() { return getTitle(data, offset); }

    int getLength() {
        return getLength(data, offset);
    }

    public boolean titleMatches(byte[] title) {
        return compareTitle(title) == 0;
    }

    public int compareTitle(byte[] title) {
        int stringSizeOffset = getStringSizeOffset(data, offset);
        int stringSize = data.getInt(stringSizeOffset);
        int lenComp = Integer.compare(stringSize, title.length);
        if (lenComp != 0) return lenComp;
        final int stringOffset = offset + stringSizeOffset + Integer.BYTES;
        for (int i = 0; i < title.length; i++) {
            int comp = Byte.compare(data.get(stringOffset + i), title[i]);
            if (comp != 0) return comp;
        }
        return 0;
    }

    public String toString() {
        return String.format("#%d \"%s\" (%d links)", getId(), getTitle(), getLinks().length);
    }

    public int compareTitle(PackedWikiPage other) {
        ByteBuffer d1 = data;
        ByteBuffer d2 = other.data;
        int thisStringSizeOffset = getStringSizeOffset(d1, offset);
        int thiStringSize = d1.getInt(thisStringSizeOffset);

        int otherStringSizeOffset = getStringSizeOffset(d2, other.offset);
        int otherStringSize = d2.getInt(otherStringSizeOffset);
        int lenComp = Integer.compare(thiStringSize, otherStringSize);
        if (lenComp != 0) return lenComp;
        for (int i = 0; i < thiStringSize; i++) {
            byte thisByte = d1.get(thisStringSizeOffset + i);
            byte otherByte = d2.get(otherStringSizeOffset + i);
            int comp = Byte.compare(thisByte, otherByte);
            if (comp != 0) return comp;
        }
        return 0;
    }

    public static int getLength(ByteBuffer data, int baseOffset) {
        int stringSizeOffset = getStringSizeOffset(data, baseOffset);
        int stringSize = data.getInt(stringSizeOffset);
        int length = stringSizeOffset + Integer.BYTES + stringSize * Byte.BYTES - baseOffset;
        return length;
    }

    public void writeTo(WritableByteChannel channel) throws IOException {
        ByteBuffer writer = data.duplicate();
        writer.position(offset);
        writer.limit(getLength());
        channel.write(writer);
    }

    public void writeTo(ByteBuffer buffer) {
        ByteBuffer writer = data.duplicate();
        writer.position(offset);
        writer.limit(getLength());
        buffer.put(writer);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PackedWikiPage) {
            PackedWikiPage other = (PackedWikiPage) obj;
            int len1 = getLength();
            int len2 = other.getLength();
            if (len1 != len2) return false;
            for (int i1 = offset, i2 = other.offset; i1 < offset + len1; i1++, i2++) {
                if (data.get(i1) != other.data.get(i2)) {
                    return false;
                }
            }
            return true;
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(getId());
    }


}
