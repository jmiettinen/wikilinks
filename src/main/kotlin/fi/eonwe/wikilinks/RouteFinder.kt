package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.utils.IntQueue
import fi.eonwe.wikilinks.utils.IntIntOpenHashMap
import java.util.function.IntConsumer

/**
 */
class RouteFinder private constructor(
    private val startIndex: Int,
    private val endIndex: Int,
    private val mapper: WikiRoutes.PageMapper,
    private val reverseMapper: WikiRoutes.PageMapper?
) {
    private fun findWithReverse(): IntArray {
        checkNotNull(reverseMapper)
        val forwardPrev = IntIntOpenHashMap(DEFAULT_SIZE, NOT_FOUND)
        val backwardPrev = IntIntOpenHashMap(DEFAULT_SIZE, NOT_FOUND)

        val forwardQueue = IntQueue.growingQueue(DEFAULT_SIZE)
        val backwardQueue = IntQueue.growingQueue(DEFAULT_SIZE)
        forwardPrev.put(startIndex, startIndex)
        backwardPrev.put(endIndex, endIndex)
        forwardQueue.addLast(startIndex)
        backwardQueue.addLast(endIndex)
        // Try to limit the sizes of the maps when we're not making any progress.
        var forwardIsTooBig = false
        var backwardIsTooBig = false

        while (!backwardQueue.isEmpty() && !forwardQueue.isEmpty()) {
            var foundRoute = false
            if (!forwardIsTooBig || backwardIsTooBig) {
                foundRoute = findRoute(forwardQueue, forwardPrev, mapper, backwardPrev)
                forwardIsTooBig = forwardPrev.size > TOO_BIG
            }
            if (!foundRoute && (!backwardIsTooBig || forwardIsTooBig)) {
                foundRoute = findRoute(backwardQueue, backwardPrev, reverseMapper, forwardPrev)
                backwardIsTooBig = backwardPrev.size > TOO_BIG
            }
            if (foundRoute) {
                return recordRoute(startIndex, endIndex, forwardPrev, backwardPrev)
            }
        }
        return IntArray(0)
    }

    private fun find(): IntArray {
        val previous = IntIntOpenHashMap(DEFAULT_SIZE, NOT_FOUND)

        val queue = IntQueue.growingQueue(DEFAULT_SIZE)

        previous.put(startIndex, startIndex)
        queue.addLast(startIndex)
        while (!queue.isEmpty()) {
            val pageId = queue.removeFirst()
            if (pageId == endIndex) {
                return toInt(recordRoute(startIndex, endIndex, previous))
            }
            mapper.forEachLinkIndex(pageId, IntConsumer { linkId: Int ->
                if (previous.putIfAbsent(linkId, pageId) == NOT_FOUND) {
                    val didFit = queue.addLast(linkId)
                    assert(didFit)
                }
            })
        }
        return IntArray(0)
    }

    companion object {
        private val NOT_FOUND = -1
        private const val DEFAULT_SIZE = 65536
        private val TOO_BIG = 1 shl 18

        fun find(startIndex: Int, endIndex: Int, forwardMapper: WikiRoutes.PageMapper, reverseMapper: WikiRoutes.PageMapper?): IntArray {
            val finder = RouteFinder(startIndex, endIndex, forwardMapper, reverseMapper)
            val route: IntArray?
            if (reverseMapper == null) {
                route = finder.find()
            } else {
                route = finder.findWithReverse()
            }
            return route
        }

        private fun findRoute(
            queue: IntQueue,
            prevMap: IntIntOpenHashMap,
            mapper: WikiRoutes.PageMapper,
            reversePrevMap: IntIntOpenHashMap
        ): Boolean {
            val id = queue.removeFirst()
            if (reversePrevMap.containsKey(id)) {
                return true
            }
            mapper.forEachLinkIndex(id) { linkId: Int ->
                if (prevMap.putIfAbsent(linkId, id) == NOT_FOUND) {
                    queue.addLast(linkId)
                }
            }
            return false
        }

        private fun countPath(map: IntIntOpenHashMap, startIndex: Int, endIndex: Int): Int {
            var size = 0
            var cur = startIndex
            while ((map.getOrDefault(cur, endIndex).also { cur = it }) != endIndex) {
                size++
            }
            return size
        }

        /**
         * With the following graph (here - depict a link from article on the left to the one on right)
         * <pre>
         * a - b - d
         * /   \
         * e - f - g - h - i
        </pre> *
         * When searching for connection from a to i we'll start the search from both and i. As the search progresses,
         * we might have two routes [a, b, g] and [i, h, g]. This method then combines them to form path [a,b, g, h, i].
         * @param startIndex Index we start the search from
         * @param endIndex Index we want to end in
         * @param forwardPrev Mapping of index to the index that lead to it when searching forwards in graph
         * @param backwardPrev Mapping of index to the index that lead to it when searching backwards in graph
         * @return A route of indices [startIndex, ... indices on the route, endIndex] or an empty array if no route was found.
         */
        private fun recordRoute(
            startIndex: Int,
            endIndex: Int,
            forwardPrev: IntIntOpenHashMap,
            backwardPrev: IntIntOpenHashMap
        ): IntArray {
            val bestPath = intArrayOf(NOT_FOUND, Int.Companion.MAX_VALUE)
            val scoreIndex = bestPath.size - 1
            backwardPrev.forEach { target: Int, source: Int ->
                if (forwardPrev.containsKey(target)) {
                    val stepsBackwards: Int = countPath(forwardPrev, target, startIndex)
                    val stepsForwards: Int = countPath(backwardPrev, target, endIndex)
                    val totalSteps = 1 + stepsBackwards + stepsForwards
                    if (bestPath[scoreIndex] > totalSteps) {
                        bestPath[scoreIndex] = totalSteps
                        bestPath[0] = target
                    }
                }
            }
            val firstPart = recordRoute(startIndex, bestPath[0], forwardPrev)
            val secondPart = recordRoute(endIndex, bestPath[0], backwardPrev).reversed()
            // We'll drop the index that was found both ways.
            val (firstPartSplit, secondPartSplit) = if (firstPart.size > 1) {
                firstPart.subList(0, firstPart.size - 1) to secondPart
            } else {
                firstPart to secondPart.subList(1, secondPart.size)
            }
            return toInt(firstPartSplit, secondPartSplit)
        }

        @SafeVarargs
        private fun toInt(vararg values: Iterable<out Int>): IntArray {
            return values.asSequence().flatten().map { it }.toList().toIntArray()
        }

        private fun recordRoute(startIndex: Int, endIndex: Int, previous: IntIntOpenHashMap): List<Int> {
            if (startIndex == endIndex) {
                return mutableListOf(startIndex)
            }
            val list: MutableList<Int> = mutableListOf()
            var cur = endIndex
            do {
                list.add(cur)
                cur = previous.getOrDefault(cur, startIndex)
            } while (cur != startIndex)
            list.add(startIndex)
            list.reverse()
            return list
        }
    }
}
