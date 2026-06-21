package fi.eonwe.wikilinks.segmentgraph

import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import fi.eonwe.wikilinks.server.WikiSearchIndex
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class SegmentWikiGraphStoreConcurrencyTest {
    @Test
    fun `store and wrappers support concurrent reads while open`() {
        withSegmentStore { store ->
            val routes = SegmentWikiRoutes(store)
            val searchIndex = WikiSearchIndex(store)
            val executor = Executors.newFixedThreadPool(8)
            try {
                val tasks = (1..16).map {
                    Callable {
                        repeat(20) {
                            store.findIdByTitle("A") shouldBe 1
                            store.hasTitle("D") shouldBe true
                            store.findTitlesByPrefix("A", 5) shouldContainExactly listOf("A", "A redirect")
                            store.titleOf(1) shouldBe "A"
                            store.isRedirect(5) shouldBe true
                            store.outNeighbors(1).toList() shouldContainExactly listOf(2, 3)
                            store.inNeighbors(4).toList() shouldContainExactly listOf(2)

                            val titles = ArrayList<String>()
                            store.forEachTitleRecord { _, title, _ -> titles.add(title) }
                            titles shouldContainExactly listOf("A", "B", "C", "D", "A redirect")

                            searchIndex.search("A", 3).first() shouldBe "A"
                            routes.findRoute("A", "D") shouldContainExactly listOf("A", "B", "D")
                        }
                    }
                }
                for (future in executor.invokeAll(tasks)) {
                    future.get()
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test
    fun `closed store rejects later reads and can be closed again`() {
        val path = createSegmentFile()
        try {
            val store = SegmentWikiGraphSerialization.open(path)
            store.close()
            store.close()

            val error = shouldThrow<IllegalStateException> {
                store.hasTitle("A")
            }
            error.message shouldBe "SegmentWikiGraphStore is closed"
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun withSegmentStore(block: (SegmentWikiGraphStore) -> Unit) {
        val path = createSegmentFile()
        try {
            SegmentWikiGraphSerialization.open(path).use(block)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun createSegmentFile(): Path {
        val path = Files.createTempFile("wikilinks-segment-concurrency-", ".graph")
        FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
            SegmentWikiGraphSerialization().serialize(
                listOf(
                    BufferWikiPage.createFrom(1, intArrayOf(2, 3), "A", false),
                    BufferWikiPage.createFrom(2, intArrayOf(4), "B", false),
                    BufferWikiPage.createFrom(3, intArrayOf(), "C", false),
                    BufferWikiPage.createFrom(4, intArrayOf(), "D", false),
                    BufferWikiPage.createFrom(5, intArrayOf(1), "A redirect", true)
                ),
                channel
            )
        }
        return path
    }

    private fun IntCursor.toList(): List<Int> {
        val out = ArrayList<Int>()
        while (hasNext()) {
            out.add(nextInt())
        }
        return out
    }
}
