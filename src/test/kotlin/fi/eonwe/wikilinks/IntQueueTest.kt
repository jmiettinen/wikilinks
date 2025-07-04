package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.utils.IntQueue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import java.util.Queue
import java.util.Random

/**
 */
class IntQueueTest {

    @Test
    fun itAddsUntilMaxCapacity() {
        val queue = IntQueue.fixedSizeQueue(SIZE)
        for (i in 0..<SIZE) {
            Assertions.assertEquals(i, queue.size())
            val success = queue.addLast(i)
            Assertions.assertTrue(success)
            Assertions.assertEquals(i + 1, queue.size())
        }
        val success = queue.addLast(SIZE)
        Assertions.assertFalse(success)
        Assertions.assertEquals(SIZE, queue.size())
    }

    @Test
    fun behavesLikeLinkedQueueUnbound() {
        val queue = IntQueue.growingQueue(SIZE)
        testQueue(queue!!, true)
    }

    @Test
    fun behavesLikeLinkedQueue() {
        val queue = IntQueue.fixedSizeQueue(SIZE)
        testQueue(queue, false)
    }

    companion object {
        private const val SIZE = 1023

        private fun testQueue(queue: IntQueue, canGrow: Boolean) {
            val rng = Random(-0x35014542)
            val jdkQueue: Queue<Int?> = ArrayDeque<Int?>()

            var i = 0
            var iteration = 0
            while (iteration < 1000000) {
                var noop = true
                val value = rng.nextDouble()
                if (value < 0.499 && (i < SIZE || canGrow)) {
                    val newVal = i++
                    noop = false
                    queue.addLast(newVal)
                    jdkQueue.offer(newVal)
                    Assertions.assertEquals(jdkQueue.size, queue.size())
                } else if (value < 0.998 && i > 0) {
                    val removed = queue.removeFirst()
                    val removedJdk: Int = jdkQueue.remove()!!
                    Assertions.assertEquals(removedJdk, removed)
                    Assertions.assertEquals(jdkQueue.size, queue.size())
                    noop = false
                    i--
                } else if (value < 0.999) {
                    while (i < SIZE) {
                        queue.addLast(i)
                        jdkQueue.offer(i)
                        Assertions.assertEquals(jdkQueue.size, queue.size())
                        noop = false
                        i++
                    }
                } else {
                    // remove all
                    while (!jdkQueue.isEmpty()) {
                        val removed = queue.removeFirst()
                        val removedJdk: Int = jdkQueue.remove()!!
                        Assertions.assertEquals(removedJdk, removed)
                        Assertions.assertEquals(jdkQueue.size, queue.size(), "Sizes differ")
                        noop = false
                    }
                    Assertions.assertTrue(queue.isEmpty())
                    i = 0
                }
                if (!noop) iteration++
            }
        }
    }
}