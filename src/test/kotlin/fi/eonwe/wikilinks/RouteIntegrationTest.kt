package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.TestHelper.usingTestDump
import fi.eonwe.wikilinks.TestHelper.usingTestDumpBB
import io.kotest.matchers.collections.shouldContainExactly
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RouteIntegrationTest {
    private lateinit var processorRoutes: WikiRoutes
    private lateinit var readerRoutes: WikiRoutes

    @BeforeAll
    fun setUp() {
        val processorPages = usingTestDump(TestData.Sileasin) { input ->
            BZip2CompressorInputStream(input, true).use { wikiInput ->
                WikiProcessor.readPages(wikiInput)
            }
        }
        processorRoutes = WikiRoutes(processorPages)

        val readerPages = usingTestDumpBB(TestData.Sileasin) { bb ->
            WikiReader.readPages(ByteBufferCompressedSource(bb))
        }
        readerRoutes = WikiRoutes(readerPages)
    }

    private fun routeTitles(routes: WikiRoutes, start: String, end: String): List<String> {
        return routes.findRoute(start, end).getRoute().map { it.title }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `silesian dump routes via wikiprocessor`() {
        val titles = routeTitles(processorRoutes, "Gdańsk", "Polska")
        titles shouldContainExactly listOf("Gdańsk", "Polska")
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `silesian dump routes via wikireader`() {
        val titles = routeTitles(readerRoutes, "Gdańsk", "Polska")
        titles shouldContainExactly listOf("Gdańsk", "Polska")
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `silesian dump route from polska via wikiprocessor`() {
        val titles = routeTitles(processorRoutes, "Polska", "Bałtycke Morze")
        titles shouldContainExactly listOf("Polska", "Polsko", "Bałtycke Morze")
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `silesian dump route from polska via wikireader`() {
        val titles = routeTitles(readerRoutes, "Polska", "Bałtycke Morze")
        titles shouldContainExactly listOf("Polska", "Polsko", "Bałtycke Morze")
    }

}
