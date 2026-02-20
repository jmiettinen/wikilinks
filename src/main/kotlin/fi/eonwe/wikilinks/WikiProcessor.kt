package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.fatpages.PagePointer
import fi.eonwe.wikilinks.fatpages.WikiPage
import fi.eonwe.wikilinks.fatpages.WikiPageData
import fi.eonwe.wikilinks.fatpages.WikiRedirectPage
import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import info.bliki.wiki.dump.WikiPatternMatcher
import info.bliki.wiki.dump.WikiXMLParser
import net.openhft.koloboke.collect.map.hash.HashObjObjMap
import net.openhft.koloboke.collect.map.hash.HashObjObjMaps
import java.io.InputStream
import java.util.Arrays
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 */
class WikiProcessor private constructor() {
    data class GraphStatistics(
        val articleCount: Int,
        val redirectCount: Int,
        val linkCount: Int,
        val nullLinkCount: Int
    )

    data class ReadPagesResult(
        val pages: MutableList<BufferWikiPage>,
        val beforeRedirectCleanup: GraphStatistics,
        val afterRedirectCleanup: GraphStatistics
    )

    fun preProcess(input: InputStream): HashObjObjMap<String, PagePointer> {
        val titleToPage = HashObjObjMaps.newMutableMap<String, PagePointer>(12000000)
        var nextInternalId = 0

        fun nextPageId(): Int {
            if (nextInternalId == Int.MAX_VALUE) {
                throw IllegalStateException("Too many pages to index with 32-bit ids")
            }
            return nextInternalId++
        }

        val parser = WikiXMLParser(input) { article, siteinfo ->
            if (article.isMain) {
                val text = article.text ?: ""
                val matcher = WikiPatternMatcher(text)
                // Wikimedia page ids can be larger than Int. We use a compact internal id instead.
                val id = nextPageId()
                if (matcher.isRedirect) {
                    val page = WikiRedirectPage(article.title, id, matcher.redirectText)
                    fixPagePointers(titleToPage, page)
                } else {
                    val links = matcher.links.asSequence()
                        .map { linkName -> possiblyCapitalize(linkName) }
                        .distinct()
                    val pointerLinks = buildList {
                        links.forEach { link ->
                            var ptr = titleToPage[link]
                            if (ptr == null) {
                                ptr = PagePointer(null)
                                titleToPage[link] = ptr
                            }
                            add(ptr)
                        }
                    }
                    val page = WikiPageData(article.title, id, pointerLinks)
                    fixPagePointers(titleToPage, page)
                }
            }
        }
        parser.parse()
        return titleToPage
    }

    companion object {
        private val xmlLimitsConfigured = AtomicBoolean(false)

        private fun configureXmlParserLimitsForTrustedWikiDump() {
            if (!xmlLimitsConfigured.compareAndSet(false, true)) {
                return
            }
            // Allow very large trusted Wikimedia dumps to parse without JAXP default caps.
            setSystemPropertyIfMissing("jdk.xml.totalEntitySizeLimit", "0")
            setSystemPropertyIfMissing("jdk.xml.entityExpansionLimit", "0")
            setSystemPropertyIfMissing("jdk.xml.maxGeneralEntitySizeLimit", "0")
            setSystemPropertyIfMissing("jdk.xml.maxParameterEntitySizeLimit", "0")
        }

        private fun setSystemPropertyIfMissing(name: String, value: String) {
            if (System.getProperty(name).isNullOrBlank()) {
                System.setProperty(name, value)
            }
        }

        fun readPages(input: InputStream): MutableList<BufferWikiPage> {
            return readPagesWithStats(input).pages
        }

        fun readPagesWithStats(input: InputStream): ReadPagesResult {
            configureXmlParserLimitsForTrustedWikiDump()
            val processor = WikiProcessor()
            val pages = processor.preProcess(input)
            val before = gatherStatistics(pages)
            dropRedirectLoops(pages)
            val after = gatherStatistics(pages)
            val packedPages = packPages(pages)
            return ReadPagesResult(
                pages = packedPages,
                beforeRedirectCleanup = before,
                afterRedirectCleanup = after
            )
        }

        private fun possiblyCapitalize(linkName: String): String {
            if (linkName.isNotEmpty() && !Character.isUpperCase(linkName[0])) {
                val chars = linkName.toCharArray()
                chars[0] = chars[0].uppercaseChar()
                return String(chars)
            }
            return linkName
        }

        fun fixPagePointers(titleToPage: HashObjObjMap<String, PagePointer>, page: WikiPage) {
            var pointer = titleToPage[page.title()]
            if (pointer != null) {
                pointer.page = page
            } else {
                pointer = PagePointer(page)
                titleToPage.put(page.title(), pointer)
            }
        }

        fun dropRedirectLoops(map: HashObjObjMap<String, PagePointer>) {
            map.values.asSequence()
                .filter { p ->
                    val page = p.page
                    page != null && page.isRedirect
                }.forEach { p ->
                    val didFindEndPage = endSomewhere(p, map, null)
                    if (!didFindEndPage) {
                        p.page = null
                    }
                }
        }

        private fun endSomewhere(
            redirect: PagePointer,
            map: HashObjObjMap<String, PagePointer>,
            visited: IdentityHashMap<WikiPage, Boolean>?
        ): Boolean {
            var visited = visited
            val immediateTarget = redirect.page
            if (immediateTarget is WikiRedirectPage) {
                if (visited == null) {
                    visited = IdentityHashMap<WikiPage, Boolean>()
                }
                if (visited.containsKey(immediateTarget)) {
                    return false
                } else {
                    visited.put(immediateTarget, true)
                }
                val redirectPage = immediateTarget
                val redirectPointer = map[redirectPage.target]
                if (redirectPointer == null) {
                    return false
                } else {
                    return endSomewhere(redirectPointer, map, visited)
                }
            } else {
                return true
            }
        }

        fun gatherStatistics(map: MutableMap<String?, PagePointer>): GraphStatistics {
            var articleCount = 0
            var redirectCount = 0
            var linkCount = 0
            var nullLinkCount = 0
            for (ptr in map.values) {
                val page = ptr.page
                if (page == null) {
                    nullLinkCount++
                    continue
                }
                if (page is WikiRedirectPage) {
                    redirectCount++
                } else if (page is WikiPageData) {
                    articleCount++
                    val p = page
                    for (linkPointer in p.links) {
                        val linkedPage = linkPointer.page
                        if (linkedPage == null) {
                            nullLinkCount++
                        } else {
                            linkCount++
                        }
                    }
                }
            }
            return GraphStatistics(
                articleCount = articleCount,
                redirectCount = redirectCount,
                linkCount = linkCount,
                nullLinkCount = nullLinkCount
            )
        }

        fun printStatistics(map: MutableMap<String?, PagePointer>) {
            printStatistics(gatherStatistics(map))
        }

        fun printStatistics(stats: GraphStatistics) {
            System.out.printf(
                "Articles: %d%n" +
                        "Redirects: %d%n" +
                        "Links: %d (/ article: %.2f)%n" +
                        "Null links %d%n",
                stats.articleCount, stats.redirectCount,
                stats.linkCount, stats.linkCount / stats.articleCount.toDouble(),
                stats.nullLinkCount
            )
        }

        private val EMPTY_ARRAY = IntArray(0)

        fun packPages(map: HashObjObjMap<String, PagePointer>): MutableList<BufferWikiPage> {
            val list = mutableListOf<BufferWikiPage>()
            map.forEach { (title: String, ptr: PagePointer) ->
                val page = ptr.page
                if (page != null) {
                    var links: IntArray
                    val isRedirect = if (page is WikiRedirectPage) {
                        val redirectPage = page
                        val target = redirectPage.target
                        val pagePointer = map[target]
                        if (pagePointer == null || pagePointer.page == null) {
                            links = EMPTY_ARRAY
                        } else {
                            links = intArrayOf(pagePointer.page!!.id())
                        }
                        true
                    } else {
                        val pageData = page as WikiPageData
                        links = pageData.links
                            .stream()
                            .map { it.page }
                            .filter { it != null }
                            .mapToInt { p -> p!!.id() }
                            .distinct()
                            .toArray()
                        if (links.isEmpty()) links = EMPTY_ARRAY
                        Arrays.sort(links)
                        false
                    }
                    val packedPage = BufferWikiPage.createFrom(page.id(), links, title, isRedirect)
                    list.add(packedPage)
                }
            }
            list.sortBy { it.id }
            return list
        }
    }
}
