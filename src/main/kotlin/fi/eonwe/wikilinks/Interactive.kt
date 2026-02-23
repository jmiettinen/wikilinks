package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.utils.Helpers
import java.io.BufferedReader
import java.io.IOException
import java.util.stream.Collectors

/**
 */
object Interactive {
    @Throws(IOException::class)
    private fun findTarget(routes: WikiRoutes, reader: BufferedReader, startPoint: Boolean): String? {
        val wildcard = "#"
        val randomPage = "<"
        System.out.printf(
            "Please type the %s article ('<' for random article and '#' for wildcard)",
            if (startPoint) "starting" else "end"
        )
        while (true) {
            print("> ")
            val read = reader.readLine()
            val trimmed = read?.trim { it <= ' ' } ?: ""
            if (wildcard == trimmed) {
                System.out.printf("Must have at last one char before the wildcards%n")
            } else if (trimmed.endsWith(wildcard)) {
                val prefix = trimmed.substring(0, trimmed.length - 1)
                val matches = routes.findWildcards(prefix, 10)
                if (matches.isEmpty()) {
                    System.out.printf("No articles start with %s%n", Helpers.quote(prefix))
                } else {
                    System.out.printf(
                        "At least these articles start with %s: %s%n",
                        Helpers.quote(prefix),
                        matches.stream().map { str: String? -> Helpers.quote(str) }.collect(
                            Collectors.toList()
                        )
                    )
                }
            } else if (trimmed == randomPage) {
                val page = routes.randomPage
                System.out.printf("Selected \"%s\" as %s page%n", page, if (startPoint) "starting" else "end")
                return page
            } else if (!trimmed.isEmpty() && routes.hasPage(trimmed)) {
                return trimmed
            } else {
                System.out.printf("No page with name %s found. Try wildcards?%n", Helpers.quote(trimmed))
            }
        }
    }

    @Throws(IOException::class)
    fun doSearch(routes: WikiRoutes, console: BufferedReader) {
        while (true) {
            val start = findTarget(routes, console, true)
            if (start == null) return
            val end = findTarget(routes, console, false)
            if (end == null) return
            doSearch(routes, start, end)
        }
    }

    private fun doSearch(routes: WikiRoutes, start: String, end: String) {
        var result: String?
        try {
            val route = routes.findRoute(start, end)
            val routeString: String?
            if (route.getRoute().isEmpty()) {
                routeString = "No route found"
            } else {
                routeString = "Route: $route"
            }
            result = String.format("%s (in %d ms)", routeString, route.runtime)
        } catch (e: BadRouteException) {
            if (e.endExist()) {
                result = if (e.startExists()) {
                    String.format("No route found between %s and %s", e.startName, e.endName)
                } else {
                    String.format("Starting point %s does not exists", e.startName)
                }
            } else {
                if (e.startExists()) {
                    result = String.format("End point %s does not exists", e.endName)
                } else {
                    result = String.format(
                        "Neither start point %s or end point %s do exist",
                        e.startName,
                        e.endName
                    )
                }
            }
        } catch (e: RuntimeException) {
            result = "<ERROR>: " + e.message
        }
        System.out.printf("%s%n", result)
    }
}