package fi.eonwe.wikilinks.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import fi.eonwe.wikilinks.BadRouteException
import fi.eonwe.wikilinks.segmentgraph.SegmentWikiRoutes
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors

class WikiHttpServer(
    private val registry: ServerWikiRegistry,
    port: Int = 8080
) : AutoCloseable {
    private val server = HttpServer.create(InetSocketAddress(port), 0)
    private val executor = Executors.newVirtualThreadPerTaskExecutor()

    init {
        server.executor = executor
        server.createContext("/") { exchange ->
            handle(exchange) {
                if (exchange.requestMethod != "GET") {
                    json(exchange, 405, """{"error":"method not allowed"}""")
                    return@handle
                }
                val path = exchange.requestURI.path
                when {
                    path == "/" || path == "/index.html" -> serveStatic(exchange, "web/index.html")
                    path.startsWith("/static/") -> {
                        val rel = path.removePrefix("/static/")
                        if (rel.isBlank() || rel.contains("..")) {
                            text(exchange, 404, "Not found", "text/plain; charset=utf-8")
                        } else {
                            serveStatic(exchange, "web/$rel")
                        }
                    }
                    path.startsWith("/api/") -> {
                        // Routed by more specific /api/* handlers.
                        text(exchange, 404, "Not found", "text/plain; charset=utf-8")
                    }
                    else -> text(exchange, 404, "Not found", "text/plain; charset=utf-8")
                }
            }
        }
        server.createContext("/api/wikis") { exchange ->
            handle(exchange) {
                if (exchange.requestMethod != "GET") {
                    json(exchange, 405, """{"error":"method not allowed"}""")
                    return@handle
                }
                val namesJson = registry.wikiNames().joinToString(",") { "\"${escapeJson(it)}\"" }
                json(exchange, 200, """{"wikis":[$namesJson]}""")
            }
        }
        server.createContext("/api/") { exchange ->
            handle(exchange) {
                if (exchange.requestMethod != "GET") {
                    json(exchange, 405, """{"error":"method not allowed"}""")
                    return@handle
                }
                val path = exchange.requestURI.path.removePrefix("/api/")
                val parts = path.split('/').filter { it.isNotBlank() }
                if (parts.size != 2) {
                    json(exchange, 404, """{"error":"not found"}""")
                    return@handle
                }
                val wikiName = parts[0]
                val action = parts[1]
                val wiki = registry.wiki(wikiName)
                if (wiki == null) {
                    json(exchange, 404, """{"error":"unknown wiki"}""")
                    return@handle
                }
                when (action) {
                    "search" -> handleSearch(exchange, wiki, wikiName)
                    "route" -> handleRoute(exchange, wiki)
                    else -> json(exchange, 404, """{"error":"not found"}""")
                }
            }
        }
    }

    fun start() {
        server.start()
    }

    fun port(): Int = server.address.port

    override fun close() {
        server.stop(0)
        executor.shutdown()
    }

    private fun handleSearch(exchange: HttpExchange, wiki: ServerWikiRegistry.LoadedWiki, wikiName: String) {
        val query = parseQuery(exchange.requestURI)
        val q = query["q"]?.trim().orEmpty()
        if (q.isEmpty()) {
            json(exchange, 400, """{"error":"missing q"}""")
            return
        }
        val limit = query["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        val matches = wiki.searchIndex.search(q, limit)
        val titles = matches.joinToString(",") { "\"${escapeJson(it)}\"" }
        json(exchange, 200, """{"wiki":"${escapeJson(wikiName)}","results":[$titles]}""")
    }

    private fun handleRoute(exchange: HttpExchange, wiki: ServerWikiRegistry.LoadedWiki) {
        val query = parseQuery(exchange.requestURI)
        val from = query["from"]?.trim().orEmpty()
        val to = query["to"]?.trim().orEmpty()
        val neighborhoodLimit = query["neighborhood"]?.toIntOrNull()?.coerceIn(0, 1000) ?: 200
        if (from.isEmpty() || to.isEmpty()) {
            json(exchange, 400, """{"error":"missing from/to"}""")
            return
        }
        try {
            val path = wiki.routes.findRoute(from, to)
            val pathJson = path.joinToString(",") { "\"${escapeJson(it)}\"" }
            val pathIds = path.mapNotNull { wiki.store.findIdByTitle(it) }
            val pathIdSet = pathIds.toHashSet()
            val neighborIds = LinkedHashSet<Int>(neighborhoodLimit)
            val edgeJson = ArrayList<String>(pathIds.size + neighborhoodLimit)

            for (i in 0 until pathIds.size - 1) {
                edgeJson.add("""{"from":${pathIds[i]},"to":${pathIds[i + 1]},"kind":"path"}""")
            }

            if (neighborhoodLimit > 0) {
                for (pathId in pathIds) {
                    val cursor = wiki.store.outNeighbors(pathId)
                    while (cursor.hasNext()) {
                        val targetId = cursor.nextInt()
                        if (!pathIdSet.contains(targetId) && neighborIds.size < neighborhoodLimit) {
                            if (neighborIds.add(targetId)) {
                                edgeJson.add("""{"from":$pathId,"to":$targetId,"kind":"neighbor"}""")
                            }
                        }
                    }
                    if (neighborIds.size >= neighborhoodLimit) break
                }
            }

            val nodeJson = ArrayList<String>(pathIds.size + neighborIds.size)
            for (id in pathIds) {
                val title = wiki.store.titleOf(id)
                nodeJson.add("""{"id":$id,"title":"${escapeJson(title)}","kind":"path"}""")
            }
            for (id in neighborIds) {
                val title = wiki.store.titleOf(id)
                nodeJson.add("""{"id":$id,"title":"${escapeJson(title)}","kind":"neighbor"}""")
            }
            json(
                exchange,
                200,
                """{"path":[$pathJson],"nodes":[${nodeJson.joinToString(",")}],"edges":[${edgeJson.joinToString(",")}]}"""
            )
        } catch (e: BadRouteException) {
            val reason = when {
                !e.startExists() && !e.endExist() -> "start and end do not exist"
                !e.startExists() -> "start does not exist"
                !e.endExist() -> "end does not exist"
                else -> "no route found"
            }
            json(exchange, 404, """{"error":"$reason"}""")
        }
    }

    private fun json(exchange: HttpExchange, status: Int, body: String) {
        text(exchange, status, body, "application/json; charset=utf-8")
    }

    private fun text(exchange: HttpExchange, status: Int, body: String, contentType: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { out ->
            out.write(bytes)
        }
    }

    private fun serveStatic(exchange: HttpExchange, resourcePath: String) {
        val stream = WikiHttpServer::class.java.classLoader.getResourceAsStream(resourcePath)
        if (stream == null) {
            text(exchange, 404, "Not found", "text/plain; charset=utf-8")
            return
        }
        val bytes = stream.use { it.readAllBytes() }
        exchange.responseHeaders.add("Content-Type", contentType(resourcePath))
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { out ->
            out.write(bytes)
        }
    }

    private fun handle(exchange: HttpExchange, block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            json(exchange, 400, """{"error":"bad request"}""")
        } catch (_: Throwable) {
            json(exchange, 500, """{"error":"internal error"}""")
        } finally {
            exchange.close()
        }
    }

    companion object {
        private fun parseQuery(uri: URI): Map<String, String> {
            val query = uri.rawQuery ?: return emptyMap()
            if (query.isBlank()) return emptyMap()
            return query.split('&')
                .asSequence()
                .filter { it.isNotBlank() }
                .associate { part ->
                    val idx = part.indexOf('=')
                    if (idx < 0) {
                        decode(part) to ""
                    } else {
                        decode(part.substring(0, idx)) to decode(part.substring(idx + 1))
                    }
                }
        }

        private fun decode(value: String): String {
            return URLDecoder.decode(value, StandardCharsets.UTF_8)
        }

        private fun escapeJson(value: String): String {
            val out = StringBuilder(value.length + 8)
            for (c in value) {
                when (c) {
                    '\\' -> out.append("\\\\")
                    '"' -> out.append("\\\"")
                    '\n' -> out.append("\\n")
                    '\r' -> out.append("\\r")
                    '\t' -> out.append("\\t")
                    else -> out.append(c)
                }
            }
            return out.toString()
        }

        private fun contentType(path: String): String {
            return when (path.substringAfterLast('.').lowercase(Locale.ROOT)) {
                "html" -> "text/html; charset=utf-8"
                "css" -> "text/css; charset=utf-8"
                "js" -> "application/javascript; charset=utf-8"
                "json" -> "application/json; charset=utf-8"
                else -> "application/octet-stream"
            }
        }
    }
}
