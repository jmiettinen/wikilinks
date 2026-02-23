package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import fi.eonwe.wikilinks.leanpages.LeanWikiPage
import fi.eonwe.wikilinks.utils.Functions.IntIntIntIntProcedure
import fi.eonwe.wikilinks.utils.Helpers
import fi.eonwe.wikilinks.utils.IntIntOpenHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.function.IntConsumer
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Duration
import kotlin.time.measureTimedValue

/**
 */
class WikiRoutes(pages: List<BufferWikiPage>) {
    private val pagesByTitle: List<BufferWikiPage>
    private val pagesById: List<BufferWikiPage>
    private val mapper: LeanPageMapper
    private val reverseMapper: LeanPageMapper

    init {
        val pagesByTitle = pages.toMutableList()
        val pagesById = pages.toMutableList()
        sortIfNeeded(pagesById, "by id", byId())
        sortIfNeeded(
            pagesByTitle,
            "by title",
            Comparator { obj: BufferWikiPage, that: BufferWikiPage -> obj.compareTitle(that) })
        this.mapper = LeanPageMapper.Companion.convert(pages)
        this.reverseMapper = this.mapper.reverse()
        this.pagesById = pagesById
        this.pagesByTitle = pagesByTitle
    }

    @Throws(BadRouteException::class)
    fun findRoute(startPage: String, endPage: String): Result {
        val startPageObj = getPage(startPage)
        val endPageObj = getPage(endPage)
        if (startPageObj == null || endPageObj == null) {
            throw BadRouteException(startPageObj == null, endPageObj == null, startPage, endPage)
        }
        return findRoute(startPageObj, endPageObj)
    }

    val randomPage: String?
        get() {
            if (pagesByTitle.isEmpty()) {
                return null
            } else {
                val rng = ThreadLocalRandom.current()
                val i = rng.nextInt(pagesByTitle.size)
                return pagesByTitle[i].getTitle()
            }
        }

    private fun findRoute(startPage: BufferWikiPage, endPage: BufferWikiPage): Result {
        val (path, duration) = measureTimedValue {
            val routeIds = RouteFinder.find(startPage.getId(), endPage.getId(), mapper, reverseMapper)
            routeIds.map { id ->
                val index = findPageIndex(pagesById, id)
                pagesById[index]
            }
        }
        return Result(path, duration)
    }

    fun hasPage(name: String): Boolean {
        val page = getPage(name)
        return page != null
    }

    fun findWildcards(prefix: String, maxMatches: Int): List<String> {
        val matches = mutableListOf<String>()
        val ix = findPageByName(prefix)
        val startingPoint = if (ix < 0) -ix - 1 else ix
        for (i in startingPoint..<pagesByTitle.size) {
            val title = pagesByTitle[i].getTitle()
            if (title.startsWith(prefix) && matches.size < maxMatches) {
                matches.add(title)
            } else {
                break
            }
        }
        return matches
    }

    private fun getPage(name: String): BufferWikiPage? {
        val ix = findPageByName(name)
        if (ix < 0) return null
        return pagesByTitle[ix]
    }

    private fun findPageByName(name: String): Int {
        val target = BufferWikiPage.createTempFor(name)
        return pagesByTitle.binarySearch(
            target,
            Comparator { obj: BufferWikiPage, that: BufferWikiPage -> obj.compareTitle(that) })
    }

    interface PageMapper {
        fun forEachLinkIndex(pageIndex: Int, c: IntConsumer)
    }

    private class LeanPageMapper(private val index: IntIntOpenHashMap, private val links: IntArray) : PageMapper {
        override fun forEachLinkIndex(pageIndex: Int, c: IntConsumer) {
            val indexInLinks = index.getOrDefault(pageIndex, -1)
            // Not all pages are linked to.
            if (indexInLinks >= 0) {
                visitLinks(indexInLinks, c)
            }
        }

        fun visitLinks(indexInLinks: Int, c: IntConsumer) {
            val linkCountIndex = indexInLinks + 1
            val linkCount = links[linkCountIndex]
            val start = linkCountIndex + 1
            val end = start + linkCount

            for (i in start..<end) {
                c.accept(links[i])
            }
        }

        fun reverse(): LeanPageMapper {
            val (res, duration) = measureTimedValue {
                val reverseCounts = IntIntOpenHashMap(index.size, 0)
                var reverseLinkerCount = 0
                visitLinkArray(
                    links
                ) { _: Int, _: Int, firstLinkIndex: Int, firstPastLastLinkIndex: Int ->
                    for (i in firstLinkIndex..<firstPastLastLinkIndex) {
                        val targetId = links[i]
                        reverseCounts.addValue(targetId, 1, 0)
                        reverseLinkerCount++
                    }
                }
                val reversedIndex = IntIntOpenHashMap(reverseCounts.size)

                var linkIndex = 0
                val reversedLinks =
                    IntArray((reverseLinkerCount + ADDITIONAL_INFO * reverseCounts.size.toLong()).toIntOrThrow())
                reverseCounts.forEach { targetId: Int, count: Int ->
                    val startLinkIndex = linkIndex
                    reversedIndex.put(targetId, startLinkIndex)
                    reversedLinks[startLinkIndex] = targetId
                    reversedLinks[startLinkIndex + 1] = 0
                    linkIndex += count + ADDITIONAL_INFO
                }
                fillLinks(reversedLinks, reversedIndex)
                reversedIndex to reversedLinks
            }
            logger.info {
                String.format(
                    "Took %d ms to create reverse page mapper",
                    duration.inWholeMilliseconds
                )
            }
            val (reversedIndex, reversedLinks) = res
            return LeanPageMapper(reversedIndex, reversedLinks)
        }

        fun fillLinks(reversedLinks: IntArray, reversedIndex: IntIntOpenHashMap) {
            visitLinkArray(
                links
            ) { linkerId: Int, linkCount: Int, firstLinkIndex: Int, firstPastLastLinkIndex: Int ->
                for (i in firstLinkIndex..<firstPastLastLinkIndex) {
                    val targetId = links[i]
                    val startLinkIndex = reversedIndex.getOrDefault(targetId, Int.Companion.MIN_VALUE)
                    val reverseLinkIndex = startLinkIndex + 1
                    val reverseLinksWritten = reversedLinks[reverseLinkIndex]
                    val newLinkerIndex = reverseLinkIndex + reverseLinksWritten + 1
                    reversedLinks[newLinkerIndex] = linkerId
                    reversedLinks[reverseLinkIndex] = reverseLinksWritten + 1
                }
            }
        }

        companion object {
            private const val ADDITIONAL_INFO = 2

            private fun visitLinkArray(linkArray: IntArray, procedure: IntIntIntIntProcedure) {
                var linkerId = -1
                var i = 0
                while (i < linkArray.size) {
                    if (linkerId < 0) {
                        linkerId = linkArray[i]
                        i++
                    } else {
                        val linkCount = linkArray[i]
                        val firstLinkIndex = i + 1
                        val firstPastLastLinkIndex = firstLinkIndex + linkCount
                        procedure.apply(linkerId, linkCount, firstLinkIndex, firstPastLastLinkIndex)
                        i = firstPastLastLinkIndex
                        linkerId = -1
                    }
                }
            }

            fun convert(pages: List<BufferWikiPage>): LeanPageMapper {
                val startTime = System.currentTimeMillis()
                val totalLinkCount = pages.asSequence().map { it.linkCount.toLong() }.sum()
                val links = IntArray(totalLinkCount.toIntOrThrow() + ADDITIONAL_INFO * pages.size)
                val map = IntIntOpenHashMap(pages.size)
                var nextLinkIndex = 0
                for (page in pages) {
                    val sourceId = page.getId()
                    val linkCount = page.linkCount
                    val startLinkIndex = nextLinkIndex
                    links[nextLinkIndex++] = sourceId
                    links[nextLinkIndex++] = linkCount
                    page.forEachLink { linkTarget: Int ->
                        links[nextLinkIndex++] = linkTarget
                    }
                    map.put(sourceId, startLinkIndex)
                }

                logger.info {
                    String.format(
                        "Took %d ms to create page mapper",
                        System.currentTimeMillis() - startTime
                    )
                }
                return LeanPageMapper(map, links)
            }
        }
    }

    class Result(
        private val route: List<BufferWikiPage>,
        private val duration: Duration
    ) {
        fun getRoute(): List<LeanWikiPage<*>> {
            return route
        }

        val runtime: Long
            get() = duration.inWholeMilliseconds

        override fun toString(): String {
            if (route.isEmpty()) return "No route found"
            return getRoute().joinToString(separator = " -> ") { v ->
                Helpers.quote(v.title)
            }
        }
    }

    companion object {
        private val logger: Logger = Logger.getLogger(WikiRoutes::class.java.getCanonicalName())

        init {
            val logLevel = System.getProperty("wikilinks.loglevel", "WARNING")
            val level: Level = try {
                Level.parse(logLevel)
            } catch (_: IllegalArgumentException) {
                Level.WARNING
            }
            logger.setLevel(level)
        }

        private fun sortIfNeeded(
            list: MutableList<BufferWikiPage>,
            name: String,
            comp: Comparator<in BufferWikiPage>
        ) {
            val startTime = System.currentTimeMillis()
            if (!isSorted(list, comp)) {
                logger.info { "Starting to sort by $name" }
                list.sortWith(comp)
                logger.info {
                    String.format(
                        "Took %d ms to sort by %s",
                        System.currentTimeMillis() - startTime,
                        name
                    )
                }
            }
        }

        private fun isSorted(pages: List<BufferWikiPage>, comparator: Comparator<in BufferWikiPage>): Boolean {
            var earlier: BufferWikiPage? = null
            for (page in pages) {
                if (earlier != null) {
                    val comp = comparator.compare(earlier, page)
                    if (comp > 0) return false
                }
                earlier = page
            }
            return true
        }

        private fun byId(): Comparator<BufferWikiPage> {
            return Comparator { o1: BufferWikiPage, o2: BufferWikiPage ->
                o1.getId().compareTo(o2.getId())
            }
        }


        private fun findPageIndex(pages: List<BufferWikiPage>, id: Int): Int {
            val needle = BufferWikiPage.createFrom(id, IntArray(0), "ignored", false)
            return pages.binarySearch(needle, byId())
        }
    }
}

fun Long.toIntOrThrow(): Int {
    if (this in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
        return toInt()
    } else {
        throw IllegalArgumentException("Too large to fit int $this")
    }
}
