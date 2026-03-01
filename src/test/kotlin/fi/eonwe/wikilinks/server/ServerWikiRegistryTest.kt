package fi.eonwe.wikilinks.server

import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import fi.eonwe.wikilinks.segmentgraph.SegmentWikiGraphSerialization
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.pathString

class ServerWikiRegistryTest {
    @Test
    fun `parser accepts valid name-file pairs`() {
        val file = createTinySegmentFile()
        val parsed = ServerWikiInputParser.parse(listOf("sileasin", file.pathString))
        parsed.size shouldBeExactly 1
        parsed[0].name shouldBe "sileasin"
        parsed[0].file shouldBe file
    }

    @Test
    fun `parser rejects odd argument count`() {
        val error = runCatching {
            ServerWikiInputParser.parse(listOf("only-name"))
        }.exceptionOrNull()
        error.shouldNotBeNull()
        (error is IllegalArgumentException) shouldBe true
    }

    @Test
    fun `parser rejects duplicate names`() {
        val file = createTinySegmentFile()
        val error = runCatching {
            ServerWikiInputParser.parse(listOf("dup", file.pathString, "dup", file.pathString))
        }.exceptionOrNull()
        error.shouldNotBeNull()
        (error is IllegalArgumentException) shouldBe true
    }

    @Test
    fun `parser rejects missing file`() {
        val missing = Files.createTempDirectory("wikilinks-server-missing").resolve("missing.segment")
        val error = runCatching {
            ServerWikiInputParser.parse(listOf("missing", missing.pathString))
        }.exceptionOrNull()
        error.shouldNotBeNull()
        (error is IllegalArgumentException) shouldBe true
    }

    @Test
    fun `registry opens all configured stores`() {
        val file1 = createTinySegmentFile()
        val file2 = createTinySegmentFile()
        val inputs = ServerWikiInputParser.parse(
            listOf("alpha", file1.pathString, "beta", file2.pathString)
        )
        ServerWikiRegistry.open(inputs).use { registry ->
            registry.wikiNames().shouldContainExactly(listOf("alpha", "beta"))
            registry.wiki("alpha").shouldNotBeNull().store.hasTitle("A") shouldBe true
            registry.wiki("beta").shouldNotBeNull().store.hasTitle("B") shouldBe true
        }
    }

    private fun createTinySegmentFile() = Files.createTempFile("wikilinks-server", ".segment").also { path ->
        path.toFile().deleteOnExit()
        val pages = listOf(
            BufferWikiPage.createFrom(1, intArrayOf(2), "A", false),
            BufferWikiPage.createFrom(2, intArrayOf(), "B", false)
        )
        FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
            SegmentWikiGraphSerialization().serialize(pages, channel)
        }
    }
}
