package fi.eonwe.wikilinks;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

import fi.eonwe.wikilinks.utils.FixedSizeIntQueue;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 */
public class FixedSizeIntQueueTest {


    private static final int SIZE = 1023;

    private FixedSizeIntQueue queue;

    @Before
    public void setup() {
        queue = new FixedSizeIntQueue(SIZE);
    }

    @Test
    public void itAddsUntilMaxCapacity() {
        for (int i = 0; i < SIZE; i++) {
            assertEquals(i, queue.size());
            boolean success = queue.addLast(i);
            assertTrue(success);
            assertEquals(i + 1, queue.size());

        }
        boolean success = queue.addLast(SIZE);
        assertFalse(success);
        assertEquals(SIZE, queue.size());
    }

    @Test
    public void behavesLikeLinkedQueue() {
        Queue<Integer> jdkQueue = new ArrayDeque<>();

        final Random rng = new Random(0xcafebabe);

        int i = 0;
        int iteration = 0;
        while (iteration < 1_000_000) {
            boolean noop = true;
            final double val = rng.nextDouble();
            if (val < 0.499 && i < SIZE) {
                int newVal = i++;
                noop = false;
                queue.addLast(newVal);
                jdkQueue.add(newVal);
            } else if (val < 0.998 && i > 0) {
                int removed = queue.removeFirst();
                int removedJdk = jdkQueue.remove();
                assertEquals(removedJdk, removed);
                assertEquals(jdkQueue.size(), queue.size());
                noop = false;
                i--;
            } else if (val < 0.999) {
                for (; i < SIZE; i++) {
                    queue.addLast(i);
                    jdkQueue.add(i);
                    assertEquals(jdkQueue.size(), queue.size());
                    noop = false;
                }
            } else {
                // remove all
                while (!jdkQueue.isEmpty()) {
                    int removed = queue.removeFirst();
                    int removedJdk = jdkQueue.remove();
                    assertEquals(removedJdk, removed);
                    assertEquals(jdkQueue.size(), queue.size());
                    noop = false;
                }
                assertTrue(queue.isEmpty());
                i = 0;
            }
            if (!noop) iteration++;
        }
    }


}
