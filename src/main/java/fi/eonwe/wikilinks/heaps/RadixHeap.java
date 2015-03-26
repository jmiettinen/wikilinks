package fi.eonwe.wikilinks.heaps;

import com.carrotsearch.hppc.LongArrayList;
import com.carrotsearch.hppc.cursors.LongCursor;
import com.carrotsearch.hppc.procedures.LongProcedure;
import com.google.common.collect.Maps;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 */
public class RadixHeap {

    private final int bucketMinSize;
    private LongArrayList[] buckets = new LongArrayList[33];
    private int lastDeleted = 0;
    private int size = 0;

    public RadixHeap(int bucketMinSize) {
        this.bucketMinSize = bucketMinSize;
        buckets[0] = new LongArrayList(bucketMinSize);
    }

    public RadixHeap() {
        this(128);
    }

    private LongArrayList getBucket(int i) {
        LongArrayList list = buckets[i];
        if (list == null) {
            list = new LongArrayList(bucketMinSize);
            buckets[i] = list;
        }
        return list;
    }

    private int getBucketIndex(int value) {
        int xorred = lastDeleted ^ value;
        if (xorred == 0) return 0;
        return getLargestBitSet(xorred) + 1;
    }

    public long insert(int key, int data) {
        int bucketIndex = getBucketIndex(key);
        long result = createFrom(key, data);
        getBucket(bucketIndex).add(result);
        size++;
        return result;

    }

    public long decreaseKey(int newKey, long packedVal) {
        if (newKey >= lastDeleted) {
            final int oldKey = extractKey(packedVal);
            int bucketIndex = getBucketIndex(oldKey);
            LongArrayList array = getBucket(bucketIndex);
            int lastIndex = array.size() - 1;
            if (lastIndex > 0) {
                // Find the value we're removing from the array and copy the last value
                // in the array into its place. Update the size of the array.
                int indexOfRemoved = -1;
                for (int i = 0; i < array.size(); i++) {
                    long val = array.get(i);
                    if (val == packedVal) {
                        indexOfRemoved = i;
                        break;
                    }
                }
                array.set(indexOfRemoved, array.get(lastIndex));
            }
            array.remove(lastIndex);
            size--;
            return insert(newKey, extractData(packedVal));
        }
        throw new IllegalArgumentException("Cannot decrease the key under last deleted key (-> " + newKey + " when last deleted was " + lastDeleted + ")");
    }

    private void extractMin(long packedVal, int indexInBucket) {
        final int deletedKey = extractKey(packedVal);
        int bucketIndex = getBucketIndex(deletedKey);
        lastDeleted = deletedKey;
        size--;
        if (bucketIndex == 0) {
            handleSmallestBucket(indexInBucket);
        } else {
            LongArrayList arrayList = getBucket(bucketIndex);
            arrayList.forEach((LongProcedure) value -> {
                if (value != packedVal) {
                    insert(extractKey(value), extractData(value));
                    size--;
                }
            });
            arrayList.elementsCount = 0;
        }
    }

    private void handleSmallestBucket(int indexInBucket) {
        LongArrayList arrayList = getBucket(0);
        int lastIndex = arrayList.size() - 1;
        if (lastIndex > 0) {
            arrayList.set(indexInBucket, arrayList.get(lastIndex));
        }
        arrayList.remove(lastIndex);
    }

    private int extractSmallest() {
        LongArrayList smallestBucket = buckets[0];
        if (!smallestBucket.isEmpty()) {
            return 0;
        }
        return -1;
    }

    public long extractMin() {
        if (isEmpty()) throw new IllegalStateException("Heap is empty");
        int smallestKey = Integer.MAX_VALUE;
        long smallestPacked = -1;
        int smallestIndex = extractSmallest();
        if (smallestIndex >= 0) {
            smallestPacked = buckets[0].get(smallestIndex);
        } else {
            for (int i = 1; i < buckets.length; i++) {
                LongArrayList list = buckets[i];
                if (list != null && !list.isEmpty()) {
                    for (LongCursor c : list) {
                        int key = extractKey(c.value);
                        if (key < smallestKey) {
                            smallestKey = key;
                            smallestPacked = c.value;
                            smallestIndex = c.index;
                        }
                    }
                    break;
                }
            }
        }
        extractMin(smallestPacked, smallestIndex);
        return smallestPacked;
    }

    public boolean isEmpty() { return size == 0; }

    public static int extractKey(long val) {
        return (int) val;
    }

    public static int extractData(long val) {
        return (int) (val >>> 32);
    }

    static long createFrom(int key, int data) {
        return key | ((long) data) << 32;
    }

    private static int getLargestBitSet(int val) {
        if (val == 0) return -1;
        int leadingZeroes = Integer.numberOfLeadingZeros(val);
        return 31 - leadingZeroes;
    }

    public Map<Integer, String> getPrettyContent() {
        int i = 0;
        TreeMap<Integer, String> map = Maps.newTreeMap();
        for (LongArrayList l : buckets) {
            if (l != null) {
                String asString = l.size() > 10
                        ? String.format("< %d values >", l.size())
                        : Arrays.toString(Arrays.stream(l.toArray()).mapToInt(RadixHeap::extractKey).toArray());
                map.put(i, String.format("%d: %s", i, asString));
            }
            i++;
        }
        return map;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> entry : getPrettyContent().entrySet()) {
            sb.append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

}
