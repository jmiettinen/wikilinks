package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.TestHelper.usingTestDump
import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import io.kotest.matchers.collections.shouldContainExactly
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.util.stream.Collectors

class WikiProcessorTest {

    @Test
    fun itReadSilesionWikiPageNames() {
        usingTestDump { input: InputStream ->
            BZip2CompressorInputStream(input, true).use { wikiInput ->
                val pages = WikiProcessor.readPages(wikiInput)
                val pageNames =
                    pages.stream().map { obj: BufferWikiPage? -> obj!!.getTitle() }.collect(Collectors.toSet())
                val pagesThatShouldExists = listOf("Gůrny Ślůnsk", "Gdańsk", "Legwan", "Wikipedyjo")
                val actual = pagesThatShouldExists.map { it to pageNames.contains(it) }
                val expected = pagesThatShouldExists.map { it to true }
                actual shouldContainExactly expected
            }
        }
    }

}