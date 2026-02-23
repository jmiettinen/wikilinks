package fi.eonwe.wikilinks.segmentgraph

import fi.eonwe.wikilinks.leanpages.BufferWikiPage

interface GraphDataSource : AutoCloseable {
    val nodeCount: Int
    fun titleOf(id: Int): String?
    fun forEachNode(consumer: (NodeRecord) -> Unit)
    override fun close() = Unit
}

data class NodeRecord(
    val id: Int,
    val title: String,
    val isRedirect: Boolean,
    val outLinks: IntArray
)

class BufferPagesGraphDataSource(private val pages: List<BufferWikiPage>) : GraphDataSource {
    private val titleById = pages.associate { it.id to it.title }

    override val nodeCount: Int
        get() = pages.size

    override fun titleOf(id: Int): String? = titleById[id]

    override fun forEachNode(consumer: (NodeRecord) -> Unit) {
        for (page in pages) {
            val links = IntArray(page.linkCount)
            var i = 0
            page.forEachLink { links[i++] = it }
            consumer(
                NodeRecord(
                    id = page.id,
                    title = page.title,
                    isRedirect = page.isRedirect,
                    outLinks = links
                )
            )
        }
    }
}

class SegmentStoreGraphDataSource(private val store: SegmentWikiGraphStore) : GraphDataSource {
    override val nodeCount: Int
        get() = store.nodeCount

    override fun titleOf(id: Int): String? {
        return try {
            store.titleOf(id)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun forEachNode(consumer: (NodeRecord) -> Unit) {
        store.forEachNode(consumer)
    }

    override fun close() {
        store.close()
    }
}

