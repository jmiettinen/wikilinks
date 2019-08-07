package fi.eonwe.wikilinks;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

import fi.eonwe.wikilinks.utils.IntQueue;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.*;

/**
 */
public class IntQueueTest {

    private static final int SIZE = 1023;

    private IntQueue queue;

    @Test
    public void itAddsUntilMaxCapacity() {
        queue = IntQueue.fixedSizeQueue(SIZE);
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
    public void behavesLikeLinkedQueueUnbound() {
        queue = IntQueue.growingQueue(SIZE);
        testQueue(queue, true);
    }

    @Test
    public void behavesLikeLinkedQueue() {
        queue = IntQueue.fixedSizeQueue(SIZE);
        testQueue(queue, false);
    }

    private static void testQueue(IntQueue queue, boolean canGrow) {
        final Random rng = new Random(0xcafebabe);
        Queue<Integer> jdkQueue = new ArrayDeque<>();

        int i = 0;
        int iteration = 0;
        while (iteration < 1_000_000) {
            boolean noop = true;
            final double val = rng.nextDouble();
            if (val < 0.499 && (i < SIZE || canGrow)) {
                int newVal = i++;
                noop = false;
                queue.addLast(newVal);
                jdkQueue.offer(newVal);
                assertEquals(jdkQueue.size(), queue.size());
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
                    jdkQueue.offer(i);
                    assertEquals(jdkQueue.size(), queue.size());
                    noop = false;
                }
            } else {
                // remove all
                while (!jdkQueue.isEmpty()) {
                    int removed = queue.removeFirst();
                    int removedJdk = jdkQueue.remove();
                    assertEquals(removedJdk, removed);
                    assertEquals("Sizes differ", jdkQueue.size(), queue.size());
                    noop = false;
                }
                assertTrue(queue.isEmpty());
                i = 0;
            }
            if (!noop) iteration++;
        }
    }


}
