package fi.eonwe.wikilinks.heaps;

import com.google.common.collect.Maps;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/**
 */
public class RadixHeapTest {

    Random rng;

    @Before
    public void setup() {
        rng  = new Random(0xcafebabe);
    }

    @Test
    public void extractMinSimple() {
        RadixHeap heap = new RadixHeap();

        final int count = 100_000;
        int[] numbers = new int[count];

        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = rng.nextInt(50_000);
            heap.insert(numbers[i], i);
        }

        Arrays.sort(numbers);

        for (int expected : numbers) {
            int extractedVal = RadixHeap.extractKey(heap.extractMin());
            assertEquals(expected, extractedVal);
        }

        assertTrue(heap.isEmpty());
    }

    @Test
    public void decreaseKey() {
        RadixHeap heap = new RadixHeap();

        final int count = 10_000;
        final int divisor = 10;
        assertTrue(count % divisor == 0);
        long[] numbers = new long[count];
        for (int i = 0; i < numbers.length; i++) {
            numbers[i] = heap.insert(i, i);
        }
        Map<Integer, Set<Long>> values = Maps.newHashMap();
        long sum = 0;
        for (int i = numbers.length - 1; i >= 0; i--) {
            long val = numbers[i];
            int newKey = RadixHeap.extractKey(val) % divisor;
            heap.decreaseKey(newKey, val);
        }
        for (int i = 0; i < count; i++) {
            long packed = heap.extractMin();
            sum += RadixHeap.extractKey(packed);
        }
        int checkSum = 0;
        for (int i = 0; i < divisor; i++) {
            checkSum += (count / divisor) * i;
        }

        assertEquals(checkSum, sum);
        assertTrue(heap.isEmpty());
    }

    @Test
    public void decreaseKeyAndExtract() {
        RadixHeap heap = new RadixHeap();

        final int count = 10_000;

    }

    @Test
    public void itPacksCorrectly() {
        for (int data = 0; data < 100_000; data++) {
            int key = rng.nextInt(Integer.MAX_VALUE);
            long packed = RadixHeap.createFrom(key, data);
            assertEquals(RadixHeap.extractKey(packed), key);
            assertEquals(RadixHeap.extractData(packed), data);
        }
    }

}
