package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.List;

/**
 */
public class WikiSerialization {

    private static final int VERSION_NUMBER = 0x52ea2a00 | 1;

    public static List<PackedWikiPage> readFromSerialized(FileInputStream fis) throws IOException {
        FileChannel channel = fis.getChannel();
        return readFromSerialized(channel);
    }

    public static List<PackedWikiPage> readFromSerialized(FileChannel channel) throws IOException {
        final long fileSize = channel.size();
        long startOffset = 0;
        long readSize = Math.min(fileSize - startOffset,  Integer.BYTES + Long.BYTES);
        MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, startOffset, readSize);
        long[] headerData = new long[2];
        readHeader(buffer, headerData);
        int versionNumber = (int) headerData[0];
        int count = Ints.checkedCast(headerData[1]);
        startOffset += readSize;
        int[] lastOffset = { 0 };
        int[] readCount = { 0 };
        List<PackedWikiPage> pages = Lists.newArrayListWithCapacity(count);
        do {
            readSize = Math.min(fileSize - startOffset,  Integer.MAX_VALUE);
            buffer = channel.map(FileChannel.MapMode.READ_ONLY, startOffset, readSize);
            deserializePiece(buffer, pages, lastOffset, readCount, count);
            startOffset += lastOffset[0];
            if (startOffset > fileSize && readCount[0] < count) {
                throw new IOException("Invalid stream: promised to contain " + count + " entities, but only " + readCount[0] + " found until end");
            }
        } while (readCount[0] < count);
        return pages;
    }

    public static List<PackedWikiPage> deserialize(ByteBuffer buffer) throws IOException {
        long[] headerData = new long[2];
        readHeader(buffer, headerData);
        int versionNumber = (int) headerData[0];
        int count = Ints.checkedCast(headerData[1]);
        List<PackedWikiPage> pages = Lists.newArrayListWithCapacity(count);
        for (int i = 0, offset = buffer.position(); i < count; i++) {
            PackedWikiPage newPage = new PackedWikiPage(buffer, offset);
            pages.add(newPage);
            offset += newPage.getLength();
        }
        return pages;
    }

    private static void readHeader(ByteBuffer buffer, long[] headerData) throws IOException {
        if (buffer.remaining() < Integer.BYTES + Long.BYTES) {
            throw new IOException("Too few bytes remaning to read header (" + buffer.remaining() + ")");
        }
        int versionNumber = buffer.getInt();
        if (versionNumber != VERSION_NUMBER) {
            throw new IOException(String.format("Magic cookie %d did not match the expected %d", versionNumber, VERSION_NUMBER));
        }
        long count = buffer.getLong();
        headerData[0] = versionNumber;
        headerData[1] = count;
    }

    private static void deserializePiece(ByteBuffer buffer, List<PackedWikiPage> pages, int[] lastOffset, int[] readCount, int totalCount) {
        // We assume that we're aligned here
        final int startReadCount = readCount[0];
        for (int curReadCount = startReadCount + 1, offset = buffer.position(); curReadCount <= totalCount; curReadCount++) {
            int len = PackedWikiPage.getLength(buffer, offset);
            if (len < 0) {
                return;
            }
            PackedWikiPage newPage = new PackedWikiPage(buffer, offset);
            pages.add(newPage);
            offset += newPage.getLength();
            lastOffset[0] = offset;
            readCount[0] = curReadCount;
        }
    }

    public static void serialize(List<PackedWikiPage> graph, ByteBuffer output) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES);
        buffer.putInt(VERSION_NUMBER);
        buffer.putLong(graph.size());
        buffer.flip();
        output.put(buffer);
        for (PackedWikiPage packedWikiPage : graph) {
            packedWikiPage.writeTo(output);
        }
    }

    public static void serialize(List<PackedWikiPage> graph, WritableByteChannel channel) throws IOException {
        // Format:
        // i32: magic bytes
        // i64: article_count
        // article_count repeats:
        //   i64 article_id
        //   i32 link_count
        //   link_count repeats:
        //     i64 link_target_id
        //   i32 title_byte_size
        //     title_byte_size repeats:
        //     i8 title_byte
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES);
        buffer.putInt(VERSION_NUMBER);
        buffer.putLong(graph.size());
        buffer.flip();
        channel.write(buffer);
        for (PackedWikiPage packedWikiPage : graph) {
            packedWikiPage.writeTo(channel);
        }
    }
}
