package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import io.kotest.matchers.collections.shouldContainExactly
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.junit.jupiter.api.Test
import java.util.stream.Collectors

class WikiProcessorTest {
    @Test
    fun itReadSilesionWikiPageNames() {
        javaClass.getResourceAsStream("/szlwiki-20190801-pages-articles-multistream.xml.bz2").use { `is` ->
            BZip2CompressorInputStream(`is`, true).use { wikiInput ->
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