package fi.eonwe.wikilinks

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import fi.eonwe.wikilinks.leanpages.BufferWikiSerialization
import fi.eonwe.wikilinks.segmentgraph.SegmentWikiGraphSerialization
import fi.eonwe.wikilinks.segmentgraph.SegmentWikiRoutes
import fi.eonwe.wikilinks.utils.Helpers
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.system.exitProcess

object Main {
    private const val HELP_SHOWN = 1
    private const val GENERAL_ERROR = 2
    private const val DEFAULT_BENCHMARK_MEASUREMENTS = 50

    private enum class GraphFormat {
        BUFFER,
        SEGMENT
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val root = WikilinksCommand()
        if (args.isEmpty()) {
            root.main(arrayOf("--help"))
            exitProcess(HELP_SHOWN)
        }
        root.main(args)
    }

    private class WikilinksCommand : CliktCommand(name = "wikilinks") {
        init {
            subcommands(ConvertCommand(), QueryCommand())
        }

        override fun run() = Unit
    }

    private class ConvertCommand : CliktCommand(name = "convert") {
        private val xmlInput by option("-x", "--xml", help = "Input WikiMedia XML file")
            .file(canBeFile = true, canBeDir = false, mustExist = true, mustBeReadable = true, mustBeWritable = false)
        private val writeOutput by option("-o", "--output", help = "Output serialized graph file")
            .file(canBeFile = true, canBeDir = false, mustBeWritable = false)
        private val indexInput by option("--index", help = "Input multistream index file (.txt.bz2)")
            .file(canBeFile = true, canBeDir = false, mustExist = true, mustBeReadable = true, mustBeWritable = false)
        private val noIndex by option("--no-index", help = "Disable index usage for .bz2 XML input")
            .flag(default = false)
        private val formatName by option(
            "--format",
            help = "Output format: buffer | segment"
        ).default("buffer")

        override fun run() {
            val inputFile = xmlInput ?: throw ProgramResult(GENERAL_ERROR)
            val outputFile = writeOutput ?: throw ProgramResult(GENERAL_ERROR)
            if (indexInput != null && noIndex) {
                System.err.println("Cannot use --index and --no-index together")
                throw ProgramResult(GENERAL_ERROR)
            }
            if ((indexInput != null || noIndex) && !inputFile.name.endsWith(".bz2")) {
                System.err.println("--index and --no-index are only valid for .bz2 XML input")
                throw ProgramResult(GENERAL_ERROR)
            }
            if (outputFile.exists()) {
                System.err.printf("File %s already exists. Exiting%n", outputFile)
                throw ProgramResult(GENERAL_ERROR)
            }

            val format = parseFormat(formatName)
            val loadStart = System.currentTimeMillis()
            System.out.printf("Starting to read %s%n", inputFile)
            val pages = readXml(inputFile, inputFile.name.endsWith(".bz2"), indexInput, noIndex).also { it.sort() }
            System.out.printf("Read %s in %d ms%n", inputFile, System.currentTimeMillis() - loadStart)

            val writeStart = System.currentTimeMillis()
            System.out.printf("Starting to write output to %s (%s)%n", outputFile, format.name.lowercase())
            writeTo(outputFile, pages, format)
            System.out.printf("Finished in %d ms%n", System.currentTimeMillis() - writeStart)
        }
    }

    private class QueryCommand : CliktCommand(name = "query") {
        private val serializedInput by option("-s", "--serialized", help = "Input serialized graph file")
            .file(canBeFile = true, canBeDir = false, mustExist = true, mustBeReadable = true, mustBeWritable = false)
        private val benchmarkMode by option("-b", "--benchmark", help = "Run benchmark mode").flag(default = false)
        private val formatName by option(
            "--format",
            help = "Input format: buffer | segment"
        ).default("buffer")

        override fun run() {
            val inputFile = serializedInput ?: throw ProgramResult(GENERAL_ERROR)
            val format = parseFormat(formatName)
            when (format) {
                GraphFormat.BUFFER -> {
                    val pages = readBufferSerialized(inputFile)
                    runQueryModeForPages(pages, benchmarkMode)
                }

                GraphFormat.SEGMENT -> {
                    SegmentWikiGraphSerialization.open(inputFile.toPath()).use { store ->
                        val routes = SegmentWikiRoutes(store)
                        runQueryModeForSegment(routes, benchmarkMode)
                    }
                }
            }
        }
    }

    private fun parseFormat(name: String): GraphFormat {
        return when (name.lowercase()) {
            "buffer" -> GraphFormat.BUFFER
            "segment" -> GraphFormat.SEGMENT
            else -> {
                System.err.println("Unknown format '$name'. Expected one of: buffer, segment")
                throw ProgramResult(GENERAL_ERROR)
            }
        }
    }

    private fun runQueryModeForPages(pages: MutableList<BufferWikiPage>, benchmarkMode: Boolean) {
        if (benchmarkMode) {
            Benchmarking.runBenchmarks(pages, DEFAULT_BENCHMARK_MEASUREMENTS)
            return
        }
        InputStreamReader(System.`in`).use { ir ->
            BufferedReader(ir).use { br ->
                doInteractive(pages, br)
            }
        }
    }

    private fun runQueryModeForSegment(routes: SegmentWikiRoutes, benchmarkMode: Boolean) {
        if (benchmarkMode) {
            runSegmentBenchmarks(routes, DEFAULT_BENCHMARK_MEASUREMENTS)
            return
        }
        InputStreamReader(System.`in`).use { ir ->
            BufferedReader(ir).use { br ->
                doInteractiveSegment(routes, br)
            }
        }
    }

    private fun printReadStats(result: WikiProcessor.ReadPagesResult) {
        println("Before redirect cleanup:")
        WikiProcessor.printStatistics(result.beforeRedirectCleanup)
        println("After redirect cleanup:")
        WikiProcessor.printStatistics(result.afterRedirectCleanup)
    }

    private fun readXml(
        inputFile: File,
        isBzipStream: Boolean,
        indexInput: File?,
        noIndex: Boolean
    ): MutableList<BufferWikiPage> {
        try {
            val result = if (isBzipStream) {
                val indexSelection = when {
                    noIndex -> WikiReader.IndexSelection.DISABLED
                    indexInput != null -> WikiReader.IndexSelection.EXPLICIT
                    else -> WikiReader.IndexSelection.AUTO
                }
                WikiReader.readPagesWithStats(
                    source = FileCompressedSource(inputFile.toPath()),
                    indexSelection = indexSelection,
                    explicitIndexPath = indexInput?.toPath()
                )
            } else {
                inputFile.inputStream().use { fis ->
                    BufferedInputStream(fis).use { bis ->
                        WikiProcessor.readPagesWithStats(bis)
                    }
                }
            }
            printReadStats(result)
            return result.pages
        } catch (e: IOException) {
            reportErrorAndExit(e)
        }
    }

    private fun readBufferSerialized(inputFile: File): MutableList<BufferWikiPage> {
        try {
            inputFile.inputStream().use { fis ->
                return BufferWikiSerialization().readFromSerialized(fis.channel)
            }
        } catch (e: IOException) {
            reportErrorAndExit(e)
        }
    }

    @Throws(IOException::class)
    private fun writeTo(outputFile: File, pages: MutableList<BufferWikiPage>, format: GraphFormat) {
        when (format) {
            GraphFormat.BUFFER -> FileOutputStream(outputFile).use { fos ->
                BufferWikiSerialization().serialize(pages, fos.channel)
                fos.fd.sync()
            }

            GraphFormat.SEGMENT -> FileChannel.open(
                outputFile.toPath(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            ).use { fc ->
                SegmentWikiGraphSerialization().serialize(pages, fc)
            }
        }
    }

    @Throws(IOException::class)
    private fun doInteractive(pages: List<BufferWikiPage>, console: BufferedReader) {
        println("Starting interactive mode")
        val initStart = System.currentTimeMillis()
        val routes = WikiRoutes(pages)
        System.out.printf("Initializing routes took %d ms%n", System.currentTimeMillis() - initStart)
        Interactive.doSearch(routes, console)
    }

    @Throws(IOException::class)
    private fun doInteractiveSegment(routes: SegmentWikiRoutes, console: BufferedReader) {
        println("Starting interactive mode")
        while (true) {
            val start = findTargetSegment(routes, console, true) ?: return
            val end = findTargetSegment(routes, console, false) ?: return
            try {
                val startTime = System.currentTimeMillis()
                val route = routes.findRoute(start, end)
                val elapsed = System.currentTimeMillis() - startTime
                val routeString = if (route.isEmpty()) "No route found" else "Route: " + route.joinToString(" -> ") { Helpers.quote(it) }
                System.out.printf("%s (in %d ms)%n", routeString, elapsed)
            } catch (e: BadRouteException) {
                val message = when {
                    !e.startExists() && !e.endExist() -> "Neither start point ${e.startName} or end point ${e.endName} do exist"
                    !e.startExists() -> "Starting point ${e.startName} does not exists"
                    !e.endExist() -> "End point ${e.endName} does not exists"
                    else -> "No route found between ${e.startName} and ${e.endName}"
                }
                System.out.printf("%s%n", message)
            } catch (e: RuntimeException) {
                System.out.printf("<ERROR>: %s%n", e.message)
            }
        }
    }

    @Throws(IOException::class)
    private fun findTargetSegment(routes: SegmentWikiRoutes, reader: BufferedReader, startPoint: Boolean): String? {
        val wildcard = "#"
        val randomPage = "<"
        System.out.printf(
            "Please type the %s article ('<' for random article and '#' for wildcard)",
            if (startPoint) "starting" else "end"
        )
        while (true) {
            print("> ")
            val trimmed = (reader.readLine() ?: "").trim()
            if (trimmed == wildcard) {
                System.out.printf("Must have at last one char before the wildcards%n")
            } else if (trimmed.endsWith(wildcard)) {
                val prefix = trimmed.dropLast(1)
                val matches = routes.findWildcards(prefix, 10)
                if (matches.isEmpty()) {
                    System.out.printf("No articles start with %s%n", Helpers.quote(prefix))
                } else {
                    System.out.printf("At least these articles start with %s: %s%n", Helpers.quote(prefix), matches.joinToString())
                }
            } else if (trimmed == randomPage) {
                val page = routes.randomPage()
                System.out.printf("Selected \"%s\" as %s page%n", page, if (startPoint) "starting" else "end")
                return page
            } else if (trimmed.isNotEmpty() && routes.hasPage(trimmed)) {
                return trimmed
            } else {
                System.out.printf("No page with name %s found. Try wildcards?%n", Helpers.quote(trimmed))
            }
        }
    }

    private fun runSegmentBenchmarks(routes: SegmentWikiRoutes, measurements: Int) {
        val runtimes = LongArray(measurements)
        System.out.printf("Running %d random measurements%n", measurements)
        for (i in 0 until measurements) {
            val p1 = routes.randomPage() ?: break
            val p2 = routes.randomPage() ?: break
            val startTime = System.currentTimeMillis()
            try {
                System.out.printf("Finding route %s -> %s%n", Helpers.quote(p1), Helpers.quote(p2))
                val route = routes.findRoute(p1, p2)
                val totalTime = System.currentTimeMillis() - startTime
                System.out.printf("%s (%d ms)%n", if (route.isEmpty()) "Found no route" else "Found route ${route.joinToString(" -> ")}", totalTime)
                runtimes[i] = totalTime
            } catch (_: BadRouteException) {
            }
        }
        printBenchmarkStats(runtimes)
    }

    private fun printBenchmarkStats(runtimes: LongArray) {
        var n = 0
        var mean = 0.0
        var m2 = 0.0
        var min = Double.POSITIVE_INFINITY
        var max = Double.NEGATIVE_INFINITY
        var sum = 0.0
        for (xLong in runtimes) {
            val x = xLong.toDouble()
            n++
            if (x < min) min = x
            if (x > max) max = x
            sum += x
            val delta = x - mean
            mean += delta / n
            m2 += delta * (x - mean)
        }
        val stddev = if (n < 2) 0.0 else kotlin.math.sqrt(m2 / (n - 1))
        System.out.printf("Runs      : %d%n", n)
        System.out.printf("Min       : %010.2f%n", min)
        System.out.printf("Max       : %010.2f%n", max)
        System.out.printf("Mean      : %010.2f%n", mean)
        System.out.printf("Std. dev. : %010.2f%n", stddev)
        System.out.printf("Sum       : %010.2f%n", sum)
    }

    fun reportErrorAndExit(t: Throwable): Nothing {
        System.err.printf("Encountered error %s. Exiting%n", t.message)
        t.printStackTrace()
        exitProcess(1)
    }
}
