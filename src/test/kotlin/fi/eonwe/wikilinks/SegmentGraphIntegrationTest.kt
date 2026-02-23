package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.TestHelper.usingTestDump
import fi.eonwe.wikilinks.fatpages.PagePointer
import fi.eonwe.wikilinks.fatpages.WikiPageData
import fi.eonwe.wikilinks.segmentgraph.SegmentWikiGraphSerialization
import fi.eonwe.wikilinks.segmentgraph.SegmentWikiRoutes
import io.kotest.matchers.collections.shouldContainExactly
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

class SegmentGraphIntegrationTest {
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `serialize to segment graph and query routes from dump`() {
        val pages = usingTestDump(TestData.Sileasin) { input ->
            BZip2CompressorInputStream(input, true).use { wikiInput ->
                WikiProcessor.readPages(wikiInput)
            }
        }

        val temp = Files.createTempFile("wikilinks-segment-", ".graph")
        try {
            java.nio.channels.FileChannel.open(
                temp,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            ).use { channel ->
                SegmentWikiGraphSerialization().serialize(pages, channel)
            }

            SegmentWikiGraphSerialization.open(temp).use { store ->
                val routes = SegmentWikiRoutes(store)
                routes.findRoute("Gdańsk", "Polska") shouldContainExactly listOf("Gdańsk", "Polska")
                routes.findRoute("Polska", "Bałtycke Morze") shouldContainExactly
                    listOf("Polska", "Polsko", "Bałtycke Morze")
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    @Test
    fun `segment graph can be built from WikiPageData`() {
        val aPtr = PagePointer(null)
        val bPtr = PagePointer(null)
        val cPtr = PagePointer(null)

        val a = WikiPageData("A", 0, listOf(bPtr))
        val b = WikiPageData("B", 1, listOf(cPtr))
        val c = WikiPageData("C", 2, emptyList())

        aPtr.page = a
        bPtr.page = b
        cPtr.page = c

        val temp = Files.createTempFile("wikilinks-segment-fat-", ".graph")
        try {
            java.nio.channels.FileChannel.open(
                temp,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            ).use { channel ->
                SegmentWikiGraphSerialization().serializeFatPages(listOf(a, b, c), channel)
            }

            SegmentWikiGraphSerialization.open(temp).use { store ->
                val routes = SegmentWikiRoutes(store)
                routes.findRoute("A", "C") shouldContainExactly listOf("A", "B", "C")
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
