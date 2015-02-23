package fi.eonwe.wikilinks;

import com.google.common.base.Charsets;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.nio.ByteBuffer;

/**
 */
public class UTFString implements CharSequence {

    private final ByteBuffer bytes;
    private final int hashCode;

    private UTFString(ByteBuffer bytes, int hashCode) {
        this.bytes = bytes;
        this.hashCode = hashCode;
    }

    public static UTFString createFrom(String from) {
        return new UTFString(createBuffer(from, false), from.hashCode());
    }

    public static UTFString createTempFrom(String from) {
        return new UTFString(createBuffer(from, true), from.hashCode());
    }

    public static ByteBuffer createBuffer(String str, boolean direct) {
        byte[] bytes = str.getBytes(Charsets.UTF_8);
        ByteBuffer buffer;
        if (direct) {
            buffer = ByteBuffer.allocateDirect(bytes.length);
        } else {
            buffer = ByteBuffer.allocate(bytes.length);
        }
        buffer.put(bytes);
        buffer.flip();
        return buffer;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof UTFString) {
            UTFString other = (UTFString) obj;
            if (hashCode != other.hashCode) return false;
            return bytes.equals(other.bytes);
        }
        return false;
    }

    @Override
    public int length() {
        return asString().length();
    }

    @Override
    public char charAt(int index) {
        return asString().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return asString().subSequence(start, end);
    }

    @Override
    public String toString() {
        return asString();
    }

    private String asString() {
        return LRU_CACHE.getUnchecked(this);
    }

    private static final LoadingCache<UTFString, String> LRU_CACHE = CacheBuilder.newBuilder()
            .maximumSize(32 * 1024)
            .build(new CacheLoader<UTFString, String>() {
                @Override
                public String load(UTFString key) {
                    ByteBuffer buf = key.bytes.duplicate();
                    int size = buf.limit();
                    byte[] bytes = new byte[size];
                    buf.get(bytes);
                    return new String(bytes, Charsets.UTF_8);
                }
            });

}
