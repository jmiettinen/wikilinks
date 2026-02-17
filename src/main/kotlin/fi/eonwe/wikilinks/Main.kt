package fi.eonwe.wikilinks

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import fi.eonwe.wikilinks.leanpages.BufferWikiSerialization
import fi.eonwe.wikilinks.leanpages.LeanWikiPage
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.system.exitProcess

object Main {
    private const val HELP_SHOWN = 1
    private const val GENERAL_ERROR = 2

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            WikilinksCommand().main(arrayOf("--help"))
            exitProcess(HELP_SHOWN)
        }
        WikilinksCommand().main(args)
    }

    private class WikilinksCommand : CliktCommand(name = "wikilinks") {
        private val xmlInput by option("-x", "--xml", help = "Input WikiMedia XML file")
            .file(canBeFile = true, canBeDir = false, mustExist = true, mustBeReadable = true)
        private val serializedInput by option("-s", "--serialized", help = "Input serialized graph file")
            .file(canBeFile = true, canBeDir = false, mustExist = true, mustBeReadable = true)
        private val writeOutput by option("-o", "--output", help = "Output file for serialized graph")
            .file(canBeFile = true, canBeDir = false)
        private val interactiveMode by option("-i", "--interactive", help = "Use interactive mode").flag(default = false)
        private val benchmarkMode by option("-b", "--benchmark", help = "Run benchmarks").flag(default = false)
        private val englishWikiTest by option(
            "-t",
            "--wiki-test",
            help = "Run benchmarks and test results against known result in English Wikipedia"
        ).flag(default = false)

        override fun run() {
            if (xmlInput != null && serializedInput != null) {
                throw ProgramResult(GENERAL_ERROR)
            }

            val selectedModeCount = listOf(interactiveMode, benchmarkMode, englishWikiTest).count { it }
            if (selectedModeCount > 1) {
                throw ProgramResult(GENERAL_ERROR)
            }

            val source = when {
                xmlInput != null -> Source.XML
                serializedInput != null -> Source.SERIALIZED
                else -> Source.STDIN
            }

            val inputFile = xmlInput ?: serializedInput
            val mode = when {
                interactiveMode -> OperationMode.INTERACTIVE
                benchmarkMode -> OperationMode.BENCHMARK
                englishWikiTest -> OperationMode.WIKI_TEST
                else -> OperationMode.NONE
            }

            if (mode == OperationMode.INTERACTIVE && source == Source.STDIN) {
                System.err.println("Cannot have interactive mode when reading from STDIN")
                throw ProgramResult(1)
            }

            val exitValue = doRun(inputFile, writeOutput, source, mode)
            if (exitValue != 0) {
                throw ProgramResult(exitValue)
            }
        }
    }

    private fun readXml(inputFile: File, isBzipStream: Boolean): MutableList<BufferWikiPage> {
        try {
            inputFile.inputStream().use { fis ->
                BufferedInputStream(fis).use { bis ->
                    val inputStream = if (isBzipStream) {
                        BufferedInputStream(BZip2CompressorInputStream(bis, true))
                    } else {
                        bis
                    }
                    return WikiProcessor.readPages(inputStream)
                }
            }
        } catch (e: IOException) {
            reportErrorAndExit(e)
        }
    }

    private fun readFromSerialized(inputFile: File): MutableList<BufferWikiPage> {
        try {
            inputFile.inputStream().use { fis ->
                return BufferWikiSerialization().readFromSerialized(fis.channel)
            }
        } catch (e: IOException) {
            reportErrorAndExit(e)
        }
    }

    private fun doRun(
        inputFile: File?,
        outputFile: File?,
        source: Source,
        mode: OperationMode
    ): Int {
        val stdin = System.`in`
        var fos: FileOutputStream? = null
        var exitValue = 0
        if (outputFile != null) {
            if (outputFile.exists()) {
                System.err.printf("File %s already exists. Exiting%n", outputFile)
                exitValue = GENERAL_ERROR
            }
            fos = getOutputStream(outputFile)
            if (fos == null) {
                System.err.printf("Cannot open file %s for writing. Exiting%n", outputFile)
                exitValue = GENERAL_ERROR
            }
        }
        if (exitValue != 0) return exitValue

        val loadStart = System.currentTimeMillis()
        val inputFileName = if (source == Source.STDIN) "<stdin>" else inputFile.toString()
        System.out.printf("Starting to read %s%n", inputFileName)
        val pages = when (source) {
            Source.XML -> {
                readXml(checkNotNull(inputFile), inputFile.name.endsWith(".bz2"))
                    .also { it.sort() }
            }

            Source.SERIALIZED -> {
                readFromSerialized(checkNotNull(inputFile))
            }

            Source.STDIN -> {
                WikiProcessor.readPages(stdin)
                    .also { it.sort() }
            }
        }
        System.out.printf("Read %s in %d ms%n", inputFileName, System.currentTimeMillis() - loadStart)

        if (fos != null) {
            val writeStart = System.currentTimeMillis()
            System.out.printf("Starting to write output to %s%n", outputFile)
            try {
                writeTo(fos, pages)
            } catch (e: IOException) {
                System.err.println("Encountered an error:")
                e.printStackTrace()
                exitValue = GENERAL_ERROR
            }
            System.out.printf("Finished in %d ms%n", System.currentTimeMillis() - writeStart)
        }
        if (exitValue != 0) return exitValue

        when (mode) {
            OperationMode.INTERACTIVE -> try {
                InputStreamReader(stdin).use { ir ->
                    BufferedReader(ir).use { br ->
                        doInteractive(pages, br)
                    }
                }
            } catch (e: IOException) {
                reportErrorAndExit(e)
            }

            OperationMode.BENCHMARK -> Benchmarking.runBenchmarks(pages, 50)
            OperationMode.WIKI_TEST -> Benchmarking.runBenchmarksAndTest(pages)
            OperationMode.NONE -> {}
        }
        return exitValue
    }

    private fun getOutputStream(file: File): FileOutputStream? {
        return try {
            FileOutputStream(file)
        } catch (_: IOException) {
            null
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

    private fun printStatistics(pages: Iterable<LeanWikiPage<*>>) {
        val statistics = reportStatistics(pages)
        System.out.printf("There are %d pages in total%n", statistics.totalPageCount)
        System.out.printf("The largest id found is %d%n", statistics.largestId)
        System.out.printf("There are %d links in total%n", statistics.totalLinkCount)
        System.out.printf("The largest amount of links found is %d%n", statistics.largestLinkCount)
        System.out.printf("Total length of the titles is %d bytes%n", statistics.titleTotal)
        System.out.printf("The longest title is %d bytes%n", statistics.longestTitle)
    }

    data class Statistics(
        val largestId: Long,
        val totalLinkCount: Long,
        val largestLinkCount: Long,
        val titleTotal: Long,
        val longestTitle: Long,
        val totalPageCount: Long,
    )

    fun reportStatistics(pages: Iterable<LeanWikiPage<*>>): Statistics {
        var largestId: Long = -1
        var linkCount: Long = 0
        var titleTotal: Long = 0
        var longestTitle: Long = -1
        var largestLinkCount: Long = -1
        var pageCount: Long = 0
        for (page in pages) {
            pageCount++
            largestId = max(largestId, page.getId().toLong())
            val thisLinkCount = page.getLinkCount().toLong()
            largestLinkCount = max(largestLinkCount, thisLinkCount)
            linkCount += thisLinkCount
            val thisPageTitleLength = page.getTitleLength().toLong()
            longestTitle = max(longestTitle, thisPageTitleLength)
            titleTotal += thisPageTitleLength
        }
        return Statistics(
            totalPageCount = pageCount,
            largestId = largestId,
            totalLinkCount = linkCount,
            largestLinkCount = largestLinkCount,
            titleTotal = titleTotal,
            longestTitle = longestTitle
        )
    }

    fun reportErrorAndExit(t: Throwable): Nothing {
        System.err.printf("Encountered error %s. Exiting%n", t.message)
        t.printStackTrace()
        exitProcess(1)
    }

    @Throws(IOException::class)
    private fun writeTo(fos: FileOutputStream, pages: MutableList<BufferWikiPage>) {
        if (pages.isNotEmpty()) {
            val serializer = BufferWikiSerialization()
            serializer.serialize(pages, fos.channel)
        }
        fos.fd.sync()
        fos.flush()
        fos.close()
    }

    private enum class Source {
        XML, SERIALIZED, STDIN
    }

    private enum class OperationMode {
        NONE, INTERACTIVE, BENCHMARK, WIKI_TEST
    }
}
