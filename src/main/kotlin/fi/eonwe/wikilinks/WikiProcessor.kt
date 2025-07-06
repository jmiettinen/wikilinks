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
import org.apache.commons.io.input.CloseShieldInputStream
import org.xml.sax.SAXException
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Arrays
import java.util.IdentityHashMap
import kotlin.math.min

/**
 */
class WikiProcessor private constructor() {
    fun preProcess(input: InputStream): HashObjObjMap<String, PagePointer> {
        val titleToPage = HashObjObjMaps.newMutableMap<String, PagePointer>(12000000)
        try {
            val parser = WikiXMLParser(input) { article, siteinfo ->
                if (article.isMain) {
                    val text = article.text ?: ""
                    val matcher = WikiPatternMatcher(text)
                    // The page identifier is assumed to integer. I don't think this is actually guaranteed anywhere
                    // in wikimedia XML schema (if it exists), but so far I've only encountered number < 2^31.
                    val id = article.id.toInt()
                    if (matcher.isRedirect) {
                        val page = WikiRedirectPage(article.title.intern(), id, matcher.redirectText.intern())
                        fixPagePointers(titleToPage, page)
                    } else {
                        val links = matcher.links.asSequence()
                            .map { linkName -> possiblyCapitalize(linkName) }
                            .distinct()
                            .toList()
                        val pointerLinks = arrayOfNulls<PagePointer>(links.size)
                        for (i in links.indices) {
                            val link = links[i]
                            var ptr = titleToPage[link]
                            if (ptr == null) {
                                ptr = PagePointer(null)
                                titleToPage.put(link.intern(), ptr)
                            }
                            pointerLinks[i] = ptr
                        }
                        val page = WikiPageData(article.title.intern(), id, pointerLinks)
                        fixPagePointers(titleToPage, page)
                    }
                }
            }
            parser.parse()
            return titleToPage
        } catch (_: SAXException) {
            return titleToPage
        } catch (_: IOException) {
            return titleToPage
        }
    }

    companion object {

        fun readPages(input: InputStream): MutableList<BufferWikiPage> {
            val processor = WikiProcessor()
            val pages = processor.preProcess(input)
            printStatistics(pages)
            dropRedirectLoops(pages)
            printStatistics(pages)
            val packedPages = packPages(pages)
            return packedPages
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
                    page != null && page.isRedirect()
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
                val redirectPointer = map[redirectPage.target()]
                if (redirectPointer == null) {
                    return false
                } else {
                    return endSomewhere(redirectPointer, map, visited)
                }
            } else {
                return true
            }
        }

        fun printStatistics(map: MutableMap<String?, PagePointer>) {
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
                    for (linkPointer in p.links()) {
                        val linkedPage = linkPointer.page
                        if (linkedPage == null) {
                            nullLinkCount++
                        } else {
                            linkCount++
                        }
                    }
                }
            }
            System.out.printf(
                "Articles: %d%n" +
                        "Redirects: %d%n" +
                        "Links: %d (/ article: %.2f)%n" +
                        "Null links %d%n",
                articleCount, redirectCount,
                linkCount, linkCount / articleCount.toDouble(),
                nullLinkCount
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
                        val target = redirectPage.target()
                        val pagePointer = map[target]
                        if (pagePointer == null || pagePointer.page == null) {
                            links = EMPTY_ARRAY
                        } else {
                            links = intArrayOf(pagePointer.page!!.id())
                        }
                        true
                    } else {
                        val pageData = page as WikiPageData
                        links = Arrays.stream(pageData.links())
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
