package fi.eonwe.wikilinks.leanpages;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 */
public abstract class AbstractSerialization<T extends LeanWikiPage<T>> {

    private final PageCreator<T> creator;

    protected AbstractSerialization(PageCreator<T> creator) {
        this.creator = creator;
    }

    protected long headerSize() {
        return Integer.BYTES + Long.BYTES;
    }

    protected void readHeader(ByteBuffer buffer, long[] headerData) throws IOException {
        ByteOrder oldOrder = buffer.order();
        buffer.order(ByteOrder.BIG_ENDIAN);
        try {
            if (buffer.remaining() < headerSize()) {
                throw new IOException("Too few bytes remaning to read header (" + buffer.remaining() + ")");
            }
            int versionNumber = buffer.getInt();
            long count = buffer.getLong();
            headerData[0] = versionNumber;
            headerData[1] = count;
        } finally {
            buffer.order(oldOrder);
        }
    }

    public abstract int getMagicCookie();

    protected void deserializePiece(ByteBuffer buffer, List<T> pages, int[] lastOffset, int[] readCount, int totalCount) {
        // We assume that we're aligned here
        final int startReadCount = readCount[0];
        for (int curReadCount = startReadCount + 1, offset = buffer.position(); curReadCount <= totalCount; curReadCount++) {
            T newPage = creator.createFrom(buffer, offset);
            pages.add(newPage);
            offset += newPage.size();
            lastOffset[0] = offset;
            readCount[0] = curReadCount;
        }
    }

    public List<T> readFromSerialized(FileInputStream fis) throws IOException {
        FileChannel channel = fis.getChannel();
        return readFromSerialized(fromFileChannel(channel));
    }

    public List<T> readFromSerialized(FileChannel buffer) throws IOException {
        return readFromSerialized(fromFileChannel(buffer));
    }

    public List<T> readFromSerialized(ByteBuffer buffer) throws IOException {
        return readFromSerialized(fromByteBuffer(buffer));
    }

    private interface BufferProvider {
        ByteBuffer map(long startOffset, long size) throws IOException;
        long size() throws IOException;
    }

    private List<T> readFromSerialized(BufferProvider bufferProvider) throws IOException {
        final long fileSize = bufferProvider.size();
        long startOffset = 0;
        long readSize = Math.min(fileSize - startOffset,  headerSize());
        ByteBuffer buffer = bufferProvider.map(startOffset, readSize);
        long[] headerData = new long[2];
        readHeader(buffer, headerData);
        setByteOrder(buffer);
        int versionNumber = (int) headerData[0];
        if (versionNumber != getMagicCookie()) {
            throw new InvalidVersionException(getMagicCookie(), versionNumber);
        }
        int count = Ints.checkedCast(headerData[1]);
        startOffset += readSize;
        int[] lastOffset = { 0 };
        int[] readCount = { 0 };
        List<T> pages = Lists.newArrayListWithCapacity(count);
        do {
            readSize = Math.min(fileSize - startOffset, Integer.MAX_VALUE);
            buffer = setByteOrder(bufferProvider.map(startOffset, readSize));
            deserializePiece(buffer, pages, lastOffset, readCount, count);
            startOffset += lastOffset[0];
            if (startOffset > fileSize && readCount[0] < count) {
                throw new IOException("Invalid stream: promised to contain " + count + " entities, but only " + readCount[0] + " found until end");
            }
        } while (readCount[0] < count);
        return pages;
    }

    private class Flyweight implements Iterable<T> {

        private final BufferProvider bufferProvider;
        private IOException exception;
        private long fileSize;

        public Flyweight(BufferProvider bufferProvider) {
            this.bufferProvider = bufferProvider;

            doTry(() -> {
                try {
                    this.fileSize = bufferProvider.size();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        private void doTry(Runnable r) {
            try {
                if (exception == null) r.run();
            } catch (UncheckedIOException e) {
                this.exception = e.getCause();
            }
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<T>() {
                boolean isBroken = fileSize == 0;
                long startOffset = 0;
                long readSize = Math.min(fileSize - startOffset,  headerSize());
                ByteBuffer buffer;
                int[] lastOffset = { 0 };
                int[] readCount = { 0 };
                int count;
                T next = null;
                {
                    try {
                        buffer = bufferProvider.map(startOffset, readSize);
                        long[] headerData = new long[2];
                        readHeader(buffer, headerData);
                        setByteOrder(buffer);
                        int versionNumber = (int) headerData[0];
                        if (versionNumber != getMagicCookie()) {
                            isBroken = true;
                        }
                        count = Ints.checkedCast(headerData[1]);
                        startOffset += readSize;
                    } catch (IOException e) {
                        isBroken = true;
                    }
                }

                @Override
                public boolean hasNext() {
                    if (next == null) {
                        fetchNext();
                    }
                    return next != null;
                }

                private void fetchNext() {
                    if (isBroken) {
                        next = null;
                    }
                }

                @Override
                public T next() {
                    if (next == null) {
                        fetchNext();
                    }
                    if (next == null) {
                        throw new NoSuchElementException();
                    } else {
                        T returned = next;
                        next = null;
                        return returned;
                    }
                }
            };
        }
    }

    private interface ExceptionRunnable<T extends Exception> {

        void run() throws T;

    }

    public void serialize(Collection<T> graph, WritableByteChannel channel) throws IOException {
        serialize(graph.stream(), graph.size(), channel);
    }

    public void serialize(Stream<T> graph, int size, WritableByteChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(getMagicCookie());
        buffer.putLong(size);
        buffer.flip();
        channel.write(buffer);
        try {
        graph.forEach(new Consumer<T>() {
            @Override
            public void accept(T t) {
                try {
                    channel.write(t.getBuffer());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private BufferProvider fromByteBuffer(ByteBuffer buffer) {
        return new BufferProvider() {
            @Override
            public ByteBuffer map(long startOffset, long size) throws IOException {
                if (startOffset < 0 || size < 0 || startOffset + size > buffer.limit()) {
                    throw new IllegalArgumentException("offset " + startOffset + ", size " + size);
                }
                ByteBuffer copy = buffer.duplicate();
                copy.position(Ints.checkedCast(startOffset));
                copy.limit(Ints.checkedCast(startOffset + size));
                return copy;
            }

            @Override
            public long size() throws IOException {
                return buffer.limit();
            }
        };
    }

    protected ByteBuffer setByteOrder(ByteBuffer buffer) {
        return buffer.order(ByteOrder.BIG_ENDIAN);
    }

    private BufferProvider fromFileChannel(FileChannel channel) {
        return new BufferProvider() {
            @Override
            public ByteBuffer map(long startOffset, long size) throws IOException {
                return channel.map(FileChannel.MapMode.READ_ONLY, startOffset, size);
            }

            @Override
            public long size() throws IOException {
                return channel.size();
            }
        };
    }

    public static final class InvalidVersionException extends IOException {

        public InvalidVersionException(int expected, int actual) {
            super(String.format("Version %d did not match the expected %d", actual, expected));
        }

    }

}
