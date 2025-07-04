package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import fi.eonwe.wikilinks.leanpages.BufferWikiSerialization
import fi.eonwe.wikilinks.leanpages.LeanWikiPage
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.CommandLineParser
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.OptionGroup
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Collections
import kotlin.system.exitProcess

/**
 */
object Main {
    private const val HELP_SHOWN = 1
    private const val GENERAL_ERROR = 2

    private val opts = Options()
    private const val XML_INPUT = "x"
    private const val SERIALIZED_INPUT = "s"
    private const val WRITE_OUTPUT = "o"
    private const val DISPLAY_HELP = "h"
    private const val INTERACTIVE_MODE = "i"
    private const val BENCHMARK_MODE = "b"
    private const val ENGLISH_WIKI_TEST = "t"

    init {
        opts.addOption(XML_INPUT, true, "Input WikiMedia XML file")
        opts.addOption(SERIALIZED_INPUT, true, "Input serialized graph file")
        opts.addOption(WRITE_OUTPUT, true, "Output file for serialized graph")
        opts.addOption(DISPLAY_HELP, false, "Print help")

        val group = OptionGroup()
        group.addOption(Option(INTERACTIVE_MODE, "Use interactive mode"))
        group.addOption(Option(BENCHMARK_MODE, "Run benchmarks"))
        group.addOption(
            Option(
                ENGLISH_WIKI_TEST,
                "Run benchmarks and test results against known result in English Wikipedia"
            )
        )
        opts.addOptionGroup(group)
    }

    fun parseOptions(args: Array<String>): CommandLine {
        val parser: CommandLineParser = DefaultParser()
        try {
            return parser.parse(opts, args)
        } catch (e: ParseException) {
            System.err.println(e.message)
            printHelp()
            exitProcess(HELP_SHOWN)
        }
    }

    private fun printHelp() {
        for (tmp in opts.getOptions()) {
            val opt = tmp as Option
            System.out.printf("  -%s        %s%n", opt.getOpt(), opt.getDescription())
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val commandLine = checkNotNull(parseOptions(args))
        if (commandLine.hasOption(DISPLAY_HELP) || commandLine.getOptions().size == 0) {
            printHelp()
            System.exit(HELP_SHOWN)
        }
        val inputFile: File?
        val source: Source?
        if (commandLine.hasOption(XML_INPUT)) {
            inputFile = File(commandLine.getOptionValue(XML_INPUT))
            source = Source.XML
        } else if (commandLine.hasOption(SERIALIZED_INPUT)) {
            inputFile = File(commandLine.getOptionValue(SERIALIZED_INPUT))
            source = Source.SERIALIZED
        } else {
            inputFile = null
            source = Source.STDIN
        }
        val inputStream = if (inputFile == null) null else getInputStream(inputFile)
        var outputFile: File? = null
        if (commandLine.hasOption(WRITE_OUTPUT)) {
            outputFile = File(commandLine.getOptionValue(WRITE_OUTPUT))
        }
        if (inputStream == null && source != Source.STDIN) {
            System.err.printf("Cannot read file \"%s\". Exiting%n", inputFile)
            System.exit(1)
        }
        var mode = OperationMode.NONE
        if (commandLine.hasOption(INTERACTIVE_MODE)) mode = OperationMode.INTERACTIVE
        if (commandLine.hasOption(BENCHMARK_MODE)) mode = OperationMode.BENCHMARK
        if (commandLine.hasOption(ENGLISH_WIKI_TEST)) mode = OperationMode.WIKI_TEST
        if (mode == OperationMode.INTERACTIVE && source == Source.STDIN) {
            System.err.println("Cannot have interactive mode when reading from STDIN")
            System.exit(1)
        }
        val exitValue = doRun(inputStream, inputFile, outputFile, source, mode)
        System.exit(exitValue)
    }

    private fun getInputStream(file: File): FileInputStream? {
        try {
            return FileInputStream(file)
        } catch (e: FileNotFoundException) {
            return null
        }
    }

    private fun getOutputStream(file: File): FileOutputStream? {
        try {
            return FileOutputStream(file)
        } catch (e: FileNotFoundException) {
            return null
        }
    }

    private fun readXml(fis: FileInputStream, isBzipStream: Boolean): MutableList<BufferWikiPage> {
        try {
            BufferedInputStream(fis).use { bis ->
                val inputStream = if (isBzipStream) {
                    BufferedInputStream(BZip2CompressorInputStream(bis, true))
                } else {
                    bis
                }
                return WikiProcessor.readPages(inputStream)
            }
        } catch (e: IOException) {
            reportErrorAndExit(e)
        }
    }

    private fun readFromSerialized(fis: FileInputStream): MutableList<BufferWikiPage> {
        try {
            return BufferWikiSerialization().readFromSerialized(fis.getChannel())
        } catch (e: IOException) {
            reportErrorAndExit(e)
        }
    }

    private fun doRun(
        input: FileInputStream?,
        inputFile: File?,
        outputFile: File?,
        source: Source?,
        mode: OperationMode?
    ): Int {
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
        val inputFileName: String? = if (source == Source.STDIN) "<stdin>" else inputFile.toString()
        System.out.printf("Starting to read %s%n", inputFileName)
        val pages = if (source == Source.XML) {
            readXml(input!!, inputFile!!.getName().endsWith(".bz2"))
                .also {
                    it.sort()
                }
        } else if (source == Source.SERIALIZED) {
            readFromSerialized(input!!)
        } else {
            WikiProcessor.readPages(System.`in`)
                .also {
                    it.sort()
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
        if (mode == OperationMode.INTERACTIVE) {
            try {
                InputStreamReader(System.`in`).use { ir ->
                    BufferedReader(ir).use { br ->
                        doInteractive(pages, br)
                    }
                }
            } catch (e: IOException) {
                reportErrorAndExit(e)
            }
        } else if (mode == OperationMode.BENCHMARK) {
            Benchmarking.runBenchmarks(pages, 50)
        } else if (mode == OperationMode.WIKI_TEST) {
            Benchmarking.runBenchmarksAndTest(pages)
        }
        return exitValue
    }

    @Throws(IOException::class)
    private fun doInteractive(pages: List<BufferWikiPage>, console: BufferedReader) {
        println("Starting interactive mode")

        val initStart = System.currentTimeMillis()
        val routes = WikiRoutes(pages)
        System.out.printf("Initializing routes took %d ms%n", System.currentTimeMillis() - initStart)
        Interactive.doSearch(routes, console)
    }

    private fun printStatistics(pages: MutableCollection<out LeanWikiPage<*>>) {
        val statistics = reportStatistics(pages)
        System.out.printf("There are %d pages in total%n", pages.size)
        System.out.printf("The largest id found is %d%n", statistics[0])
        System.out.printf("There are %d links in total%n", statistics[1])
        System.out.printf("The largest amount of links found is %d%n", statistics[2])
        System.out.printf("Total length of the titles is %d bytes%n", statistics[3])
        System.out.printf("The longest title is %d bytes%n", statistics[4])
    }

    fun reportStatistics(pages: Iterable<LeanWikiPage<*>>): LongArray {
        var largestId: Long = -1
        var linkCount: Long = 0
        var titleTotal: Long = 0
        var longestTitle: Long = -1
        var largestLinkCount: Long = -1
        for (page in pages) {
            val pageId = page.getId().toLong()
            if (pageId > largestId) {
                largestId = pageId
            }
            val thisLinkCount = page.getLinkCount().toLong()
            if (thisLinkCount > largestLinkCount) largestLinkCount = thisLinkCount
            linkCount += thisLinkCount
            val thisPageTitle = page.getTitleLength().toLong()
            if (thisPageTitle > longestTitle) longestTitle = thisPageTitle
            titleTotal += thisPageTitle
        }
        return longArrayOf(largestId, linkCount, largestLinkCount, titleTotal, longestTitle)
    }

    fun reportErrorAndExit(t: Throwable): Nothing {
        System.err.printf("Encountered error %s. Exiting%n", t.message)
        t.printStackTrace()
        exitProcess(1)
    }

    @Throws(IOException::class)
    private fun writeTo(fos: FileOutputStream, pages: MutableList<BufferWikiPage>) {
        if (!pages.isEmpty()) {
            val serializer = BufferWikiSerialization()
            serializer.serialize(pages, fos.getChannel())
        }
        fos.getFD().sync()
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