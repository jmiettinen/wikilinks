package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.TestHelper.usingTestDumpBB
import fi.eonwe.wikilinks.TestHelper.usingTestDump
import fi.eonwe.wikilinks.Utils.asInputStream
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class WikiReaderTest {

    private data class CanonicalPage(
        val isRedirect: Boolean,
        val links: List<String>
    )

    private fun canonicalizePagesByTitle(pages: List<fi.eonwe.wikilinks.leanpages.BufferWikiPage>): Map<String, CanonicalPage> {
        val titlesById = pages.associate { it.id to it.title }
        return pages.associate { page ->
            val links = buildList {
                page.forEachLink { linkId ->
                    add(titlesById[linkId] ?: error("Missing title for linked id $linkId"))
                }
            }.sorted()
            page.title to CanonicalPage(page.isRedirect, links)
        }
    }

    @Test
    fun `all parts decompress properly bb`() {
        val partCount = usingTestDumpBB { bb ->
            var readCount = 0
            WikiReader.generateSubstreams(bb).forEach { bzip2bb ->
                BZip2CompressorInputStream(bzip2bb.asInputStream(), false).use { bzip2 ->
                    val bytes = bzip2.readAllBytes()
                    bytes.size shouldBeGreaterThan 0
                    readCount++
                }
            }
            readCount

        }
        partCount shouldBe 166
    }

    @Test
    fun `generateSubstreams returns empty for empty data`() {
        val parts = WikiReader.generateSubstreams(ByteBuffer.wrap(byteArrayOf())).toList()
        parts.size shouldBe 0
    }

    @Test
    fun `generateSubstreams ignores incomplete and invalid headers`() {
        val data = byteArrayOf(
            0x42, 0x5A, 0x68, // incomplete BZh
            0x42, 0x5A, 0x68, '0'.code.toByte(), // invalid BZh0
            0x42, 0x5A // incomplete at end
        )
        val parts = WikiReader.generateSubstreams(ByteBuffer.wrap(data)).toList()
        parts.size shouldBe 0
    }

    @Test
    fun `generateSubstreams finds streams with surrounding bytes`() {
        val data = byteArrayOf(
            0x01, 0x02, 0x03, // prefix junk
            0x42, 0x5A, 0x68, '1'.code.toByte(), 0x11, 0x12, // stream A
            0x42, 0x5A, 0x68, '9'.code.toByte(), 0x21, 0x22, 0x23, // stream B
            0x55, 0x66 // suffix junk
        )
        val parts = WikiReader.generateSubstreams(ByteBuffer.wrap(data)).toList()
        parts.size shouldBe 2
        parts[0].remaining() shouldBe 6
        parts[1].remaining() shouldBe 9
    }

    @Test
    fun `generateSubstreams keeps exact bytes per stream`() {
        val streamA = byteArrayOf(0x42, 0x5A, 0x68, '1'.code.toByte(), 0x70, 0x71, 0x72)
        val streamB = byteArrayOf(0x42, 0x5A, 0x68, '2'.code.toByte(), 0x40, 0x41)
        val data = streamA + streamB

        val parts = WikiReader.generateSubstreams(ByteBuffer.wrap(data))
            .map { it.array().sliceArray(it.arrayOffset() + it.position() until it.arrayOffset() + it.limit()) }
            .toList()

        parts shouldContainExactly listOf(streamA, streamB)
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `parallel stuff does not block forever`() {
        usingTestDumpBB { bb ->
            WikiReader.processWiki(bb, ProcessingConfig(
                parallelism = 10U,
                maxBlocksWaiting = 4U
            ))
        }
    }

    @Test
    fun `wikireader and wikiprocessor produce equivalent graphs on fixture`() {
        val fromReader = usingTestDumpBB { bb ->
            WikiReader.readPages(
                bb,
                ProcessingConfig(
                    parallelism = 8U,
                    maxBlocksWaiting = 8U
                )
            )
        }

        val fromProcessor = usingTestDump { input ->
            BZip2CompressorInputStream(input, true).use { wikiInput ->
                WikiProcessor.readPages(wikiInput)
            }
        }

        canonicalizePagesByTitle(fromReader) shouldBe canonicalizePagesByTitle(fromProcessor)
    }

}
