package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.TestHelper.usingTestDump
import fi.eonwe.wikilinks.TestHelper.usingTestDumpBB
import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.DynamicTest
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class WikiReaderTest {
    private data class CanonicalPage(
        val isRedirect: Boolean,
        val links: List<String>
    )

    private enum class ReaderSourceType {
        IN_MEMORY,
        FILE
    }

    private val baselineCache = mutableMapOf<TestData, Map<String, CanonicalPage>>()

    private fun canonicalizePagesByTitle(pages: List<BufferWikiPage>): Map<String, CanonicalPage> {
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

    private fun sourceFrom(bytes: ByteArray): CompressedSource = ByteBufferCompressedSource(ByteBuffer.wrap(bytes))

    private fun usingTempCompressedFile(testData: TestData, block: (Path) -> Unit) {
        val bytes = usingTestDump(testData) { it.readAllBytes() }
        val temp = Files.createTempFile("wikireader-test-", ".xml.bz2")
        try {
            Files.write(temp, bytes)
            block(temp)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun readWithWikiReader(testData: TestData, sourceType: ReaderSourceType, config: ProcessingConfig): Map<String, CanonicalPage> {
        val pages = when (sourceType) {
            ReaderSourceType.IN_MEMORY -> usingTestDumpBB(testData) { bb ->
                WikiReader.readPages(ByteBufferCompressedSource(bb), config)
            }

            ReaderSourceType.FILE -> {
                lateinit var pages: MutableList<BufferWikiPage>
                usingTempCompressedFile(testData) { path ->
                    pages = WikiReader.readPages(FileCompressedSource(path), config)
                }
                pages
            }
        }
        return canonicalizePagesByTitle(pages)
    }

    private fun baselineFor(testData: TestData): Map<String, CanonicalPage> {
        return baselineCache.getOrPut(testData) {
            val pages = usingTestDump(testData) { input ->
                BZip2CompressorInputStream(input, true).use { wikiInput ->
                    WikiProcessor.readPages(wikiInput)
                }
            }
            canonicalizePagesByTitle(pages)
        }
    }

    @Nested
    inner class GenerateSubstreams {

        @Test
        fun `all parts decompress properly bb`() {
            val partCount = usingTestDumpBB { bb ->
                val source = ByteBufferCompressedSource(bb)
                var readCount = 0
                WikiReader.generateSubstreams(source).forEach { range ->
                    source.openRange(range).use { raw ->
                        BZip2CompressorInputStream(raw, false).use { bzip2 ->
                            val bytes = bzip2.readAllBytes()
                            bytes.size shouldBeGreaterThan 0
                            readCount++
                        }
                    }
                }
                readCount
            }
            partCount shouldBe 166
        }

        @Test
        fun `generateSubstreams returns empty for empty data`() {
            val parts = WikiReader.generateSubstreams(sourceFrom(byteArrayOf())).toList()
            parts.size shouldBe 0
        }

        @Test
        fun `generateSubstreams ignores incomplete and invalid headers`() {
            val data = byteArrayOf(
                0x42, 0x5A, 0x68,
                0x42, 0x5A, 0x68, '0'.code.toByte(),
                0x42, 0x5A
            )
            val parts = WikiReader.generateSubstreams(sourceFrom(data)).toList()
            parts.size shouldBe 0
        }

        @Test
        fun `generateSubstreams finds streams with surrounding bytes`() {
            val data = byteArrayOf(
                0x01, 0x02, 0x03,
                0x42, 0x5A, 0x68, '1'.code.toByte(), 0x11, 0x12,
                0x42, 0x5A, 0x68, '9'.code.toByte(), 0x21, 0x22, 0x23,
                0x55, 0x66
            )
            val parts = WikiReader.generateSubstreams(sourceFrom(data)).toList()
            parts.size shouldBe 2
            (parts[0].endExclusive - parts[0].start) shouldBe 6L
            (parts[1].endExclusive - parts[1].start) shouldBe 9L
        }

        @Test
        fun `generateSubstreams keeps exact bytes per stream`() {
            val streamA = byteArrayOf(0x42, 0x5A, 0x68, '1'.code.toByte(), 0x70, 0x71, 0x72)
            val streamB = byteArrayOf(0x42, 0x5A, 0x68, '2'.code.toByte(), 0x40, 0x41)
            val data = streamA + streamB
            val source = sourceFrom(data)

            val parts = WikiReader.generateSubstreams(source)
                .map { source.openRange(it).use { part -> part.readAllBytes() } }
                .toList()

            parts shouldContainExactly listOf(streamA, streamB)
        }

        @Test
        fun `filecompressedsource substreams decompress properly`() {
            usingTempCompressedFile(TestData.Sileasin) { path ->
                val source = FileCompressedSource(path)
                var readCount = 0
                WikiReader.generateSubstreams(source).forEach { range ->
                    source.openRange(range).use { raw ->
                        BZip2CompressorInputStream(raw, false).use { bzip2 ->
                            bzip2.readAllBytes().size shouldBeGreaterThan 0
                            readCount++
                        }
                    }
                }
                readCount shouldBe 166
            }
        }
    }

    @Nested
    inner class Liveness {
        @Test
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        fun `parallel stuff does not block forever`() {
            usingTestDumpBB { bb ->
                WikiReader.readPages(
                    ByteBufferCompressedSource(bb),
                    ProcessingConfig(
                        parallelism = 10U,
                        maxBlocksWaiting = 4U
                    )
                )
            }
        }
    }

    @Nested
    inner class Pipeline {
        private val configs = listOf(
            ProcessingConfig(parallelism = 8U, maxBlocksWaiting = 8U),
            ProcessingConfig(parallelism = 4U, maxBlocksWaiting = 1U)
        )
        private val dataSets = listOf(TestData.Sileasin, TestData.Faroese)
        private val sourceTypes = listOf(ReaderSourceType.IN_MEMORY, ReaderSourceType.FILE)

        @TestFactory
        @Timeout(value = 180, unit = TimeUnit.SECONDS)
        fun `wikireader matches wikiprocessor on full matrix`(): List<DynamicTest> {
            return sourceTypes.flatMap { sourceType ->
                configs.flatMap { config ->
                    dataSets.map { data ->
                        DynamicTest.dynamicTest(
                            "source=$sourceType config=$config data=$data"
                        ) {
                            val fromReader = readWithWikiReader(data, sourceType, config)
                            val fromProcessor = baselineFor(data)
                            fromReader shouldBe fromProcessor
                        }
                    }
                }
            }
        }
    }
}
