package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.TestHelper.usingTestDump
import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldContainKeys
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

    @Test
    fun `it resolves expected links for known articles from fixture`() {
        usingTestDump { input: InputStream ->
            BZip2CompressorInputStream(input, true).use { wikiInput ->
                val pages = WikiProcessor.readPages(wikiInput)
                val pagesByTitle = pages.associateBy { it.title }
                val titlesById = pages.associate { it.id to it.title }

                fun linkedTitlesFrom(title: String): List<String> {
                    val page = pagesByTitle[title] ?: error("Missing page $title in fixture")
                    return buildList {
                        page.forEachLink { id ->
                            add(titlesById[id] ?: error("Missing linked page for id=$id"))
                        }
                    }
                }

                linkedTitlesFrom("Legwan").shouldContainAll("Jaszczurki", "Amerika", "USA", "Paragwaj")
                linkedTitlesFrom("Gdańsk").shouldContainAll("Polska", "Bałtycke Morze")
            }
        }
    }

    @Test
    fun `it handles xml page ids that do not fit into int`() {
        val wikiXml = """
            <mediawiki>
              <siteinfo>
                <sitename>TestWiki</sitename>
              </siteinfo>
              <page>
                <title>Alpha</title>
                <ns>0</ns>
                <id>5000000001</id>
                <revision>
                  <id>1</id>
                  <text xml:space="preserve">[[Beta]]</text>
                </revision>
              </page>
              <page>
                <title>Beta</title>
                <ns>0</ns>
                <id>5000000002</id>
                <revision>
                  <id>2</id>
                  <text xml:space="preserve">Plain text</text>
                </revision>
              </page>
              <page>
                <title>Gamma</title>
                <ns>0</ns>
                <id>5000000003</id>
                <revision>
                  <id>3</id>
                  <text xml:space="preserve">#REDIRECT [[Alpha]]</text>
                </revision>
              </page>
            </mediawiki>
        """.trimIndent().byteInputStream()

        val pages = WikiProcessor.readPages(wikiXml)
        val pagesByTitle = pages.associateBy { it.title }

        pages.map { it.id } shouldContainExactly listOf(0, 1, 2)
        pagesByTitle.shouldContainKeys("Alpha", "Beta", "Gamma")

        val alphaLinks = mutableListOf<Int>()
        pagesByTitle["Alpha"]!!.forEachLink(alphaLinks::add)
        alphaLinks shouldContainExactly listOf(pagesByTitle["Beta"]!!.id)

        val gammaLinks = mutableListOf<Int>()
        pagesByTitle["Gamma"]!!.forEachLink(gammaLinks::add)
        gammaLinks shouldContainExactly listOf(pagesByTitle["Alpha"]!!.id)
    }

}
