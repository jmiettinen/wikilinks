package fi.eonwe.wikilinks;

import com.google.common.base.Joiner;
import fi.eonwe.wikilinks.fibonacciheap.Helpers;
import fi.eonwe.wikilinks.leanpages.LeanWikiPage;
import fi.eonwe.wikilinks.leanpages.BufferWikiPage;
import fi.eonwe.wikilinks.leanpages.BufferWikiSerialization;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static fi.eonwe.wikilinks.fibonacciheap.Helpers.quote;

/**
 * Hello world!
 */
public class WikiLinks {

    private static final Options opts = new Options();
    static {
        opts.addOption("x", true, "Input WikiMedia XML file");
        opts.addOption("s", true, "Input serialized graph file");
        opts.addOption("o", true, "Output file for serialized graph");
        opts.addOption("i", false, "Use interactive mode");
        opts.addOption("h", false, "Print help");
    }

    private static enum Source { XML, SERIALIZED, STDIN }

    public static CommandLine parseOptions(String[] args) {
        CommandLineParser parser = new GnuParser();
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
        if (commandLine.hasOption('h') || commandLine.getOptions().length == 0) {
            printHelpAndExit();
        }
        File inputFile = null;
        Source source = Source.STDIN;
        if (commandLine.hasOption('x')) {
            inputFile = new File(commandLine.getOptionValue('x'));
            source = Source.XML;
        } else if (commandLine.hasOption('s')) {
            inputFile = new File(commandLine.getOptionValue('s'));
            source = Source.SERIALIZED;
        } else {
            inputFile = null;
            source = Source.STDIN;
        }
        FileInputStream inputStream = inputFile == null ? null : getInputStream(inputFile);
        File outputFile = null;
        if (commandLine.hasOption('o')) {
            outputFile = new File(commandLine.getOptionValue('o'));
        }
        if (inputStream == null && source != Source.STDIN) {
            System.err.printf("Cannot read file \"%s\". Exiting%n", inputFile);
            System.exit(1);
        }
        boolean interactive = commandLine.hasOption('i');
        if (interactive && source == Source.STDIN) {
            System.err.println("Cannot have interactive mode when reading from STDIN");
            System.exit(1);
        }
        doRun(inputStream, inputFile, outputFile, source, interactive);
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

    private static void doRun(FileInputStream input, File inputFile, File outputFile, Source source, boolean interactive) {
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
        System.out.printf("Staring to read %s%n", inputFileName);
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
        if (interactive) {
            try {
                doInteractive(pages, new BufferedReader(new InputStreamReader(System.in)));
            } catch (IOException e) {
                handleError(e);
            }
        }
    }

    private static void doInteractive(List<BufferWikiPage> pages, BufferedReader console) throws IOException {
        System.out.println("Staring interactive mode");
        printStatistics(pages);

        long initStart = System.currentTimeMillis();
        WikiRoutes routes = new WikiRoutes(pages);
        System.out.printf("Initializing routes took %d ms%n", System.currentTimeMillis() - initStart);
        doInteractiveSearch(routes, console);
//        doSearch(routes, "Jää", "Vuori");
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

    private static String findTarget(WikiRoutes routes, BufferedReader reader, boolean startPoint) throws IOException {
        final String wildcard = "#";
        final String randomPage = "<";
        System.out.printf("Please insert the %s article (Enter empty to both to quit)%n", startPoint ? "starting" : "end");
        while (true) {
            System.out.print("> ");
            String read = reader.readLine();
            String trimmed = read == null ? "" : read.trim();
            if (wildcard.equals(trimmed)) {
                System.out.printf("Must have at last one char before the wildcards%n");
            } else if (trimmed.endsWith(wildcard)) {
                String prefix = trimmed.substring(0, trimmed.length() - 1);
                List<String> matches = routes.findWildcards(prefix, 10);
                if (matches.isEmpty()) {
                    System.out.printf("No articles start with %s%n", quote(prefix));
                } else {
                    System.out.printf("At least these articles start with %s: %s%n", quote(prefix), Arrays.asList(matches.stream().map(Helpers::quote).toArray()));
                }
            } else if (trimmed.equals(randomPage)) {
                String page = routes.getRandomPage();
                System.out.printf("Selected \"%s\" as %s page%n", page, startPoint ? "starting" : "end");
                return page;
            } else if (!trimmed.isEmpty() && routes.hasPage(trimmed)) {
                return trimmed;
            } else {
                System.out.printf("No page with name %s found. Try wildcards?%n", quote(trimmed));

            }
        }
    }

    private static void doInteractiveSearch(WikiRoutes routes, BufferedReader console) throws IOException {
        while (true) {
            String start = findTarget(routes, console, true);
            if (start == null) return;
            String end = findTarget(routes, console, false);
            if (end == null) return;
            doSearch(routes, start, end);
        }
    }

    private static void doSearch(WikiRoutes routes, String start, String end) {
        String result;
        try {
            WikiRoutes.Result route = routes.findRoute(start, end);
            String routeString;
            if (route.getRoute().isEmpty()) {
                routeString = "No route found";
            } else {
                routeString = "Route: " + Joiner.on(" -> ").join(route.getRoute().stream().map(p -> quote(p.getTitle())).toArray());
            }
            result = String.format("%s (in %d ms)", routeString, route.getRuntime());
        } catch (WikiRoutes.BadRouteException e) {
            if (e.endExist()) {
                if (e.startExists()) {
                    result = String.format("No route found between %s and %s", e.getStartName(), e.getEndName());
                } else {
                    result = String.format("Starting point %s does not exists", e.getStartName());
                }
            } else {
                if (e.startExists()) {
                    result = String.format("End point %s does not exists", e.getEndName());
                } else {
                    result = String.format("Neither start point %s or end point %s do exist", e.getStartName(), e.getEndName());
                }
            }
        }
        System.out.printf("%s%n", result);
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
