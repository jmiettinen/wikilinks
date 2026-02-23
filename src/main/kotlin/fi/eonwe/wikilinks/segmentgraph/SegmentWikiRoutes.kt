package fi.eonwe.wikilinks.segmentgraph

import fi.eonwe.wikilinks.BadRouteException
import fi.eonwe.wikilinks.RouteFinder
import fi.eonwe.wikilinks.WikiRoutes
import java.util.function.IntConsumer

class SegmentWikiRoutes(private val store: SegmentWikiGraphStore) {
    private val forwardMapper = SegmentMapper(store, reverse = false)
    private val reverseMapper = SegmentMapper(store, reverse = true)

    @Throws(BadRouteException::class)
    fun findRoute(startPage: String, endPage: String): List<String> {
        val startId = store.findIdByTitle(startPage)
        val endId = store.findIdByTitle(endPage)
        if (startId == null || endId == null) {
            throw BadRouteException(startId == null, endId == null, startPage, endPage)
        }
        val route = RouteFinder.find(startId, endId, forwardMapper, reverseMapper)
        return route.asSequence().map(store::titleOf).toList()
    }

    fun hasPage(name: String): Boolean = store.hasTitle(name)

    fun findWildcards(prefix: String, maxMatches: Int): List<String> = store.findTitlesByPrefix(prefix, maxMatches)

    fun randomPage(): String? = store.randomTitle()

    private class SegmentMapper(
        private val store: SegmentWikiGraphStore,
        private val reverse: Boolean
    ) : WikiRoutes.PageMapper {
        override fun forEachLinkIndex(pageIndex: Int, c: IntConsumer) {
            val cursor = if (reverse) {
                store.inNeighbors(pageIndex)
            } else {
                store.outNeighbors(pageIndex)
            }
            while (cursor.hasNext()) {
                c.accept(cursor.nextInt())
            }
        }
    }
}
