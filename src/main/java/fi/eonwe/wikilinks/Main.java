package fi.eonwe.wikilinks;

import fi.eonwe.wikilinks.leanpages.LeanWikiPage;
import fi.eonwe.wikilinks.leanpages.BufferWikiPage;
import fi.eonwe.wikilinks.leanpages.BufferWikiSerialization;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Hello world!
 */
public class Main {

    private static final Options opts = new Options();
    private static final String XML_INPUT = "x";
    private static final String SERIALIZED_INPUT = "s";
    private static final String WRITE_OUTPUT = "o";
    private static final String DISPLAY_HELP = "h";
    private static final String INTERACTIVE_MODE = "i";
    private static final String BENCHMARK_MODE = "b";
    private static final String ENGLISH_WIKI_TEST = "t";

    static {
        opts.addOption(XML_INPUT, true, "Input WikiMedia XML file");
        opts.addOption(SERIALIZED_INPUT, true, "Input serialized graph file");
        opts.addOption(WRITE_OUTPUT, true, "Output file for serialized graph");
        opts.addOption(DISPLAY_HELP, false, "Print help");

        OptionGroup group = new OptionGroup();
        group.addOption(new Option(INTERACTIVE_MODE, "Use interactive mode"));
        group.addOption(new Option(BENCHMARK_MODE, "Run benchmarks"));
        group.addOption(new Option(ENGLISH_WIKI_TEST, "Run benchmarks and test results against known result in English Wikipedia"));
        opts.addOptionGroup(group);
    }

    private static enum Source { XML, SERIALIZED, STDIN }
    private static enum OperationMode { NONE, INTERACTIVE, BENCHMARK, WIKI_TEST }

    public static CommandLine parseOptions(String[] args) {
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine commandLine = parser.parse(opts, args);
            return commandLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            printHelpAndExit();
            return null;
        }
    }

    private static void printHelpAndExit() {
        for (Object tmp : opts.getOptions()) {
            Option opt = (Option) tmp;
            System.out.printf("  -%s        %s%n", opt.getOpt(), opt.getDescription());
        }
        System.exit(0);
    }

    public static void main(String[] args) {
        CommandLine commandLine = parseOptions(args);
        assert commandLine != null;
        if (commandLine.hasOption(DISPLAY_HELP) || commandLine.getOptions().length == 0) {
            printHelpAndExit();
        }
        File inputFile;
        Source source;
        if (commandLine.hasOption(XML_INPUT)) {
            inputFile = new File(commandLine.getOptionValue(XML_INPUT));
            source = Source.XML;
        } else if (commandLine.hasOption(SERIALIZED_INPUT)) {
            inputFile = new File(commandLine.getOptionValue(SERIALIZED_INPUT));
            source = Source.SERIALIZED;
        } else {
            inputFile = null;
            source = Source.STDIN;
        }
        FileInputStream inputStream = inputFile == null ? null : getInputStream(inputFile);
        File outputFile = null;
        if (commandLine.hasOption(WRITE_OUTPUT)) {
            outputFile = new File(commandLine.getOptionValue(WRITE_OUTPUT));
        }
        if (inputStream == null && source != Source.STDIN) {
            System.err.printf("Cannot read file \"%s\". Exiting%n", inputFile);
            System.exit(1);
        }
        OperationMode mode = OperationMode.NONE;
        if (commandLine.hasOption(INTERACTIVE_MODE)) mode = OperationMode.INTERACTIVE;
        if (commandLine.hasOption(BENCHMARK_MODE)) mode = OperationMode.BENCHMARK;
        if (commandLine.hasOption(ENGLISH_WIKI_TEST)) mode = OperationMode.WIKI_TEST;
        if (mode == OperationMode.INTERACTIVE && source == Source.STDIN) {
            System.err.println("Cannot have interactive mode when reading from STDIN");
            System.exit(1);
        }
        doRun(inputStream, inputFile, outputFile, source, mode);
    }

    private static FileInputStream getInputStream(File file) {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    private static FileOutputStream getOutputStream(File file) {
        try {
            return new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    private static List<BufferWikiPage> readXml(FileInputStream fis, boolean isBzipStream) {
        try (BufferedInputStream bis = new BufferedInputStream(fis)) {
            InputStream is = bis;
            if (isBzipStream) {
                is = new BufferedInputStream(new BZip2CompressorInputStream(is));
            }
            return WikiProcessor.readPages(is);
        } catch (IOException e) {
            return handleError(e);
        }
    }

    private static List<BufferWikiPage> readFromSerialized(FileInputStream fis) {
        try {
            return new BufferWikiSerialization().readFromSerialized(fis);
        } catch (IOException e) {
            return handleError(e);
        }
    }

    private static void doRun(FileInputStream input, File inputFile, File outputFile, Source source, OperationMode mode) {
        FileOutputStream fos = null;
        if (outputFile != null) {
            if (outputFile.exists()) {
                System.err.printf("File %s already exists. Exiting%n", outputFile);
                System.exit(1);
            }
            fos = getOutputStream(outputFile);
            if (fos == null) {
                System.err.printf("Cannot open file %s for writing. Exiting%n", outputFile);
                System.exit(1);
            }
        }
        long loadStart = System.currentTimeMillis();
        final String inputFileName = source == Source.STDIN ? "<stdin>" : inputFile.toString();
        System.out.printf("Starting to read %s%n", inputFileName);
        List<BufferWikiPage> pages;
        if (source == Source.XML) {
            pages = readXml(input, inputFile.getName().endsWith(".bz2"));
            Collections.sort(pages);
        } else if (source == Source.SERIALIZED) {
            pages = readFromSerialized(input);
        } else {
            pages = WikiProcessor.readPages(System.in);
            Collections.sort(pages);
        }
        System.out.printf("Read %s in %d ms%n", inputFileName, System.currentTimeMillis() - loadStart);
        if (fos != null) {
            long writeStart = System.currentTimeMillis();
            System.out.printf("Starting to write output to %s%n", outputFile);
            try {
                writeTo(fos, pages);
            } catch (IOException e) {
                System.err.printf("Encountered an error %s%n", e.getMessage());
                System.out.printf("Finished in %d ms%n", System.currentTimeMillis() - writeStart);
                System.exit(1);
            }
            System.out.printf("Finished in %d ms%n", System.currentTimeMillis() - writeStart);
        }
        if (mode == OperationMode.INTERACTIVE) {
            try (InputStreamReader ir = new InputStreamReader(System.in); BufferedReader br = new BufferedReader(ir)){
                doInteractive(pages, br);
            } catch (IOException e) {
                handleError(e);
            }
        } else if (mode == OperationMode.BENCHMARK) {
            Benchmarking.runBenchmarks(pages, 50);
        } else if (mode == OperationMode.WIKI_TEST) {
            Benchmarking.runBenchmarksAndTest(pages);
        }
    }

    private static void doInteractive(List<BufferWikiPage> pages, BufferedReader console) throws IOException {
        System.out.println("Starting interactive mode");

        long initStart = System.currentTimeMillis();
        WikiRoutes routes = new WikiRoutes(pages);
        System.out.printf("Initializing routes took %d ms%n", System.currentTimeMillis() - initStart);
        Interactive.doSearch(routes, console);
    }

    private static void printStatistics(Collection<? extends LeanWikiPage<?>> pages) {
        long[] statistics = reportStatistics(pages);
        System.out.printf("There are %d pages in total%n", pages.size());
        System.out.printf("The largest id found is %d%n", statistics[0]);
        System.out.printf("There are %d links in total%n", statistics[1]);
        System.out.printf("The largest amount of links found is %d%n", statistics[2]);
        System.out.printf("Total length of the titles is %d bytes%n", statistics[3]);
        System.out.printf("The longest title is %d bytes%n", statistics[4]);
    }

    public static long[] reportStatistics(Iterable<? extends LeanWikiPage<?>> pages) {
        long largestId = -1;
        long linkCount = 0;
        long titleTotal = 0;
        long longestTitle = -1;
        long largestLinkCount = -1;
        for (LeanWikiPage page : pages) {
            long pageId = page.getId();
            if (pageId > largestId) {
                largestId = pageId;
            }
            long thisLinkCount = page.getLinkCount();
            if (thisLinkCount > largestLinkCount) largestLinkCount = thisLinkCount;
            linkCount += thisLinkCount;
            long thisPageTitle = page.getTitleLength();
            if (thisPageTitle > longestTitle) longestTitle = thisPageTitle;
            titleTotal += thisPageTitle;
        }
        return new long[] { largestId, linkCount, largestLinkCount, titleTotal, longestTitle };
    }

    public static <T> T handleError(Throwable t) {
        System.err.printf("Encountered error %s. Exiting%n", t.getMessage());
        System.exit(1);
        return null;
    }

    private static void writeTo(FileOutputStream fos, List<BufferWikiPage> pages) throws IOException {
        if (!pages.isEmpty()) {
            BufferWikiSerialization serializer = new BufferWikiSerialization();
            serializer.serialize(pages, fos.getChannel());
        }
        fos.getFD().sync();
        fos.flush();
        fos.close();
    }
}
