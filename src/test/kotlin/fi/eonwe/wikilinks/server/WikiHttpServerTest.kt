package fi.eonwe.wikilinks.server

import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import fi.eonwe.wikilinks.segmentgraph.SegmentWikiGraphSerialization
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class WikiHttpServerTest {
    @Test
    fun `ui root serves html`() {
        withServer { baseUrl ->
            val response = get("$baseUrl/")
            response.statusCode() shouldBe 200
            response.body().contains("WikiLinks Route Explorer") shouldBe true
            response.body().contains("find-route-btn") shouldBe true
        }
    }

    @Test
    fun `ui static assets are served`() {
        withServer { baseUrl ->
            val js = get("$baseUrl/static/app.js")
            js.statusCode() shouldBe 200
            js.body().contains("loadWikis") shouldBe true

            val css = get("$baseUrl/static/app.css")
            css.statusCode() shouldBe 200
            css.body().contains(".layout") shouldBe true
        }
    }

    @Test
    fun `api wikis returns configured names`() {
        withServer { baseUrl ->
            val response = get("$baseUrl/api/wikis")
            response.statusCode() shouldBe 200
            response.body().contains("\"wikis\":[\"test\"]") shouldBe true
        }
    }

    @Test
    fun `api search returns prefix matches`() {
        withServer { baseUrl ->
            val response = get("$baseUrl/api/test/search?q=Gda")
            response.statusCode() shouldBe 200
            response.body().contains("Gdańsk") shouldBe true
        }
    }

    @Test
    fun `api search is diacritic insensitive`() {
        withServer { baseUrl ->
            val response = get("$baseUrl/api/test/search?q=Gdansk")
            response.statusCode() shouldBe 200
            response.body().contains("Gdańsk") shouldBe true
        }
    }

    @Test
    fun `api search supports typo tolerant fallback`() {
        withServer { baseUrl ->
            val response = get("$baseUrl/api/test/search?q=Polksa")
            response.statusCode() shouldBe 200
            response.body().contains("Polska") shouldBe true
        }
    }

    @Test
    fun `api search prefers non-redirect page over redirect`() {
        withServer { baseUrl ->
            val response = get("$baseUrl/api/test/search?q=finlanD&limit=5")
            response.statusCode() shouldBe 200
            val results = extractResults(response.body())
            results.isNotEmpty() shouldBe true
            results.first() shouldBe "Finland"
        }
    }

    @Test
    fun `api route returns path`() {
        withServer { baseUrl ->
            val response = get("$baseUrl/api/test/route?from=Gda%C5%84sk&to=Polska")
            response.statusCode() shouldBe 200
            response.body().contains("\"path\":[\"Gdańsk\",\"Polska\"]") shouldBe true
            response.body().contains("\"nodes\"") shouldBe true
            response.body().contains("\"edges\"") shouldBe true
        }
    }

    @Test
    fun `api route includes outgoing neighborhood`() {
        withServer { baseUrl ->
            val response = get("$baseUrl/api/test/route?from=Gda%C5%84sk&to=Polska&neighborhood=10")
            response.statusCode() shouldBe 200
            response.body().contains("\"title\":\"Germany\"") shouldBe true
            response.body().contains("\"kind\":\"neighbor\"") shouldBe true
        }
    }

    @Test
    fun `api route can disable neighborhood`() {
        withServer { baseUrl ->
            val response = get("$baseUrl/api/test/route?from=Gda%C5%84sk&to=Polska&neighborhood=0")
            response.statusCode() shouldBe 200
            response.body().contains("\"title\":\"Germany\"") shouldBe false
        }
    }

    @Test
    fun `api route returns 404 for missing page`() {
        withServer { baseUrl ->
            val response = get("$baseUrl/api/test/route?from=Nope&to=Polska")
            response.statusCode() shouldBe 404
        }
    }

    @Test
    fun `api returns 404 for unknown wiki`() {
        withServer { baseUrl ->
            val response = get("$baseUrl/api/unknown/search?q=x")
            response.statusCode() shouldBe 404
        }
    }

    @Test
    fun `api search missing q returns 400`() {
        withServer { baseUrl ->
            val response = get("$baseUrl/api/test/search")
            response.statusCode() shouldBe 400
        }
    }

    @Test
    fun `api route missing params returns 400`() {
        withServer { baseUrl ->
            val response = get("$baseUrl/api/test/route?from=Gda%C5%84sk")
            response.statusCode() shouldBe 400
        }
    }

    @Test
    fun `api rejects unsupported method`() {
        withServer { baseUrl ->
            val client = HttpClient.newBuilder().build()
            val request = HttpRequest.newBuilder()
                .uri(URI("$baseUrl/api/wikis"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.statusCode() shouldBe 405
        }
    }

    private fun withServer(block: (String) -> Unit) {
        val path = Files.createTempFile("wikilinks-server-api-", ".segment")
        path.toFile().deleteOnExit()
        FileChannel.open(path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
            SegmentWikiGraphSerialization().serialize(
                listOf(
                    BufferWikiPage.createFrom(1, intArrayOf(2), "Gdańsk", false),
                    BufferWikiPage.createFrom(2, intArrayOf(3), "Polska", false),
                    BufferWikiPage.createFrom(3, intArrayOf(), "Germany", false),
                    BufferWikiPage.createFrom(4, intArrayOf(), "Finland", false),
                    BufferWikiPage.createFrom(5, intArrayOf(), "FinlanD", true)
                ),
                channel
            )
        }
        val registry = ServerWikiRegistry.open(listOf(WikiInput("test", path)))
        val server = WikiHttpServer(registry, 0)
        try {
            server.start()
            block("http://127.0.0.1:${server.port()}")
        } finally {
            server.close()
            registry.close()
            Files.deleteIfExists(path)
        }
    }

    private fun get(url: String): HttpResponse<String> {
        val client = HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder().uri(URI(url)).GET().build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun extractResults(body: String): List<String> {
        val section = Regex("\"results\"\\s*:\\s*\\[(.*?)]").find(body)?.groupValues?.get(1) ?: return emptyList()
        if (section.isBlank()) return emptyList()
        return section.split(",")
            .map { it.trim() }
            .filter { it.startsWith("\"") && it.endsWith("\"") }
            .map { it.substring(1, it.length - 1) }
    }
}
