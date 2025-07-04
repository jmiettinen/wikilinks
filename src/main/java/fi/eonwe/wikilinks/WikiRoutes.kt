package fi.eonwe.wikilinks

import com.google.common.base.Joiner
import com.google.common.collect.Lists
import com.google.common.primitives.Ints
import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import fi.eonwe.wikilinks.leanpages.LeanWikiPage
import fi.eonwe.wikilinks.utils.Functions.IntIntIntIntProcedure
import fi.eonwe.wikilinks.utils.Helpers
import net.openhft.koloboke.collect.hash.HashConfig
import net.openhft.koloboke.collect.map.IntIntMap
import net.openhft.koloboke.collect.map.hash.HashIntIntMap
import net.openhft.koloboke.collect.map.hash.HashIntIntMaps
import net.openhft.koloboke.function.IntIntConsumer
import java.util.Arrays
import java.util.Collections
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.IntConsumer
import java.util.function.IntFunction
import java.util.function.Supplier
import java.util.logging.Level
import java.util.logging.Logger
import java.util.stream.Collectors

/**
 */
class WikiRoutes(pages: MutableList<BufferWikiPage>) {
    private val pagesByTitle: List<BufferWikiPage>
    private val pagesById: List<BufferWikiPage>
    private val mapper: LeanPageMapper
    private val reverseMapper: LeanPageMapper

    init {
        val pagesByTitle = pages.toMutableList()
        val pagesById = pages.toMutableList()
        this.mapper = LeanPageMapper.Companion.convert(pages)
        this.reverseMapper = this.mapper.reverse()
        sortIfNeeded(pagesById, "by id", byId())
        sortIfNeeded(
            pagesByTitle,
            "by title",
            Comparator { obj: BufferWikiPage, that: BufferWikiPage -> obj.compareTitle(that) })
        this.pagesById = pagesById
        this.pagesByTitle = pagesByTitle
    }

    @Throws(BadRouteException::class)
    fun findRoute(startPage: String?, endPage: String?): Result {
        val startPageObj = getPage(startPage)
        val endPageObj = getPage(endPage)
        if (startPageObj == null || endPageObj == null) {
            throw BadRouteException(startPage == null, endPage == null, startPage, endPage)
        }
        return findRoute(startPageObj, endPageObj)
    }

    val randomPage: String?
        get() {
            val rng = ThreadLocalRandom.current()
            if (pagesByTitle.isEmpty()) {
                return null
            } else {
                val i = rng.nextInt(pagesByTitle.size)
                return pagesByTitle.get(i).getTitle()
            }
        }

    private fun findRoute(startPage: BufferWikiPage, endPage: BufferWikiPage): Result {
        val startTime = System.nanoTime()
        val routeIds = RouteFinder.find(startPage.getId(), endPage.getId(), mapper, reverseMapper)
        val path = routeIds.map { id ->
            val index = findPageIndex(pagesById, id)
            pagesById[index]
        }
        return WikiRoutes.Result(path, System.nanoTime() - startTime)
    }

    fun hasPage(name: String?): Boolean {
        val page = getPage(name)
        return page != null
    }

    fun findWildcards(prefix: String, maxMatches: Int): MutableList<String?> {
        val matches: MutableList<String?> = Lists.newArrayList()
        val ix = findPageByName(prefix)
        val startingPoint = if (ix < 0) -ix - 1 else ix
        for (i in startingPoint..<pagesByTitle.size) {
            val title = pagesByTitle.get(i).getTitle()
            if (title.startsWith(prefix) && matches.size < maxMatches) {
                matches.add(title)
            } else {
                break
            }
        }
        return matches
    }

    private fun getPage(name: String?): BufferWikiPage? {
        val ix = findPageByName(name)
        if (ix < 0) return null
        return pagesByTitle.get(ix)
    }

    private fun findPageByName(name: String?): Int {
        val target = BufferWikiPage.createTempFor(name)
        return pagesByTitle.binarySearch(
            target,
            Comparator { obj: BufferWikiPage, that: BufferWikiPage -> obj.compareTitle(that) })
    }

    interface PageMapper {
        fun forEachLinkIndex(pageIndex: Int, c: IntConsumer)
    }

    private class LeanPageMapper(private val index: HashIntIntMap, private val links: IntArray) : PageMapper {
        override fun forEachLinkIndex(pageId: Int, c: IntConsumer) {
            val value = index.getOrDefault(pageId, -1)
            // Not all pages are linked to.
            if (value < 0) return
            visitLinks(value, c)
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
            val startTime = System.currentTimeMillis()
            val reverseCounts: IntIntMap = HashIntIntMaps.newMutableMap(index.size)
            val reverseLinkerCount = intArrayOf(0)
            visitLinkArray(
                links,
                IntIntIntIntProcedure { linkerId: Int, linkCount: Int, firstLinkIndex: Int, firstPastLastLinkIndex: Int ->
                    for (i in firstLinkIndex..<firstPastLastLinkIndex) {
                        val targetId = links[i]
                        reverseCounts.addValue(targetId, 1, 0)
                        reverseLinkerCount[0]++
                    }
                })
            val reversedIndex = HashIntIntMaps.newMutableMap(reverseCounts.size)
            val linkIndex = intArrayOf(0)
            val reversedLinks =
                IntArray(Ints.checkedCast((reverseLinkerCount[0] + ADDITIONAL_INFO * reverseCounts.size).toLong()))
            reverseCounts.forEach(IntIntConsumer { targetId: Int, count: Int ->
                val startLinkIndex = linkIndex[0]
                reversedIndex.put(targetId, startLinkIndex)
                reversedLinks[startLinkIndex] = targetId
                reversedLinks[startLinkIndex + 1] = 0
                linkIndex[0] += count + ADDITIONAL_INFO
            })
            fillLinks(reversedLinks, reversedIndex)
            logger.info(Supplier {
                String.format(
                    "Took %d ms to create reverse page mapper",
                    System.currentTimeMillis() - startTime
                )
            })
            return LeanPageMapper(reversedIndex, reversedLinks)
        }

        fun fillLinks(reversedLinks: IntArray, reversedIndex: HashIntIntMap) {
            visitLinkArray(
                links,
                IntIntIntIntProcedure { linkerId: Int, linkCount: Int, firstLinkIndex: Int, firstPastLastLinkIndex: Int ->
                    for (i in firstLinkIndex..<firstPastLastLinkIndex) {
                        val targetId = links[i]
                        val startLinkIndex = reversedIndex.getOrDefault(targetId, Int.Companion.MIN_VALUE)
                        val reverseLinkIndex = startLinkIndex + 1
                        val reverseLinksWritten = reversedLinks[reverseLinkIndex]
                        val newLinkerIndex = reverseLinkIndex + reverseLinksWritten + 1
                        reversedLinks[newLinkerIndex] = linkerId
                        reversedLinks[reverseLinkIndex] = reverseLinksWritten + 1
                    }
                })
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

            fun convert(pages: MutableList<BufferWikiPage>): LeanPageMapper {
                val startTime = System.currentTimeMillis()
                val totalLinkCount = pages.stream().mapToLong { obj: BufferWikiPage? -> obj!!.getLinkCount().toLong() }.sum()
                val links = IntArray(Ints.checkedCast(totalLinkCount) + ADDITIONAL_INFO * pages.size)
                val map = HashIntIntMaps.getDefaultFactory()
                    .withHashConfig(HashConfig.fromLoads(0.1, 0.5, 0.75))
                    .newImmutableMap(Consumer { mapCreator: IntIntConsumer? ->
                        val linkIndex = intArrayOf(0)
                        for (page in pages) {
                            val sourceId = page.getId()
                            val linkCount = page.getLinkCount()
                            val startLinkIndex = linkIndex[0]
                            links[linkIndex[0]++] = sourceId
                            links[linkIndex[0]++] = linkCount
                            page.forEachLink(IntConsumer { linkTarget: Int ->
                                links[linkIndex[0]++] = linkTarget
                            })
                            mapCreator!!.accept(sourceId, startLinkIndex)
                        }
                    }, pages.size)

                logger.info(Supplier {
                    String.format(
                        "Took %d ms to create page mapper",
                        System.currentTimeMillis() - startTime
                    )
                })
                return LeanPageMapper(map, links)
            }
        }
    }

    class Result(
        private val route: List<BufferWikiPage>,
        private val runtimeInNanos: Long
    ) {
        fun getRoute(): List<LeanWikiPage<*>> {
            return route
        }

        val runtime: Long
            get() = TimeUnit.NANOSECONDS.toMillis(runtimeInNanos)

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
            var level: Level?
            try {
                level = Level.parse(logLevel)
            } catch (e: IllegalArgumentException) {
                level = Level.WARNING
            }
            logger.setLevel(level)
        }

        private fun sortIfNeeded(
            list: MutableList<BufferWikiPage>,
            name: String?,
            comp: Comparator<in BufferWikiPage>
        ) {
            val startTime = System.currentTimeMillis()
            if (!isSorted(list, comp)) {
                logger.info { "Starting to sort by " + name }
                list.sortWith(comp)
                logger.info(Supplier {
                    String.format(
                        "Took %d ms to sort by %s",
                        System.currentTimeMillis() - startTime,
                        name
                    )
                })
            }
        }

        private fun isSorted(pages: MutableList<BufferWikiPage>, comparator: Comparator<in BufferWikiPage>): Boolean {
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
