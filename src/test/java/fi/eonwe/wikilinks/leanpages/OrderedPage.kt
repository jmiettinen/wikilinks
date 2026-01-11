package fi.eonwe.wikilinks.leanpages

import java.util.function.IntConsumer

/**
 * Old page-format that is only used in tests anymore.
 */
class OrderedPage(val page: BufferWikiPage, private val targetIndices: IntArray) {
    val id: Int
        get() = page.getId()

    fun forEachLinkIndex(c: IntConsumer) {
        for (i in targetIndices) c.accept(i)
    }
}
