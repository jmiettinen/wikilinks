package fi.eonwe.wikilinks;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Hello world!
 */
public class WikiLinks {

    private static final Options opts = new Options();
    static {
        opts.addOption("w", true, "Input WikiMedia XML file");
        opts.addOption("s", true, "Input serialized graph file");
        opts.addOption("o", true, "Output file for serialized graph");
        opts.addOption("i", false, "Use interactive mode");
        opts.addOption("h", false, "Print help");
    }


    public static CommandLine parseOptions(String[] args) {
        CommandLineParser parser = new GnuParser();
        try {
            CommandLine commandLine = parser.parse(opts, args);
            return commandLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            System.exit(1);
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
        if (!commandLine.hasOption('w') && !commandLine.hasOption('s')) {
            System.out.println("Need some input file");
            printHelpAndExit();
        }
        if (commandLine.hasOption('w') && commandLine.hasOption('s')) {
            System.out.println("Use only one input file");
            printHelpAndExit();
        }
        File inputFile;
        boolean loadXml = false;
        if (commandLine.hasOption('w')) {
            inputFile = new File(commandLine.getOptionValue('w'));
            loadXml = true;
        } else {
            inputFile = new File(commandLine.getOptionValue('s'));
        }
        FileInputStream inputStream = getInputStream(inputFile);
        File outputFile = null;
        if (commandLine.hasOption('o')) {
            outputFile = new File(commandLine.getOptionValue('o'));
        }
        if (inputStream == null) {
            System.err.printf("Cannot read file \"%s\". Exiting%n", inputFile);
            System.exit(1);
        }
        boolean interactive = commandLine.hasOption('i');
        doRun(inputStream, inputFile, outputFile, loadXml, interactive);
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

    private static List<PackedWikiPage> readXml(FileInputStream fis, boolean isBzipStream) {
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

    private static List<PackedWikiPage> readFromSerialized(FileInputStream fis) {
        try {
            return WikiSerialization.readFromSerialized(fis);
        } catch (IOException e) {
            return handleError(e);
        }
    }

    private static void doRun(FileInputStream input, File inputFile, File outputFile, boolean loadXml, boolean interactive) {
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
        System.out.printf("Staring to read %s%n", inputFile);
        List<? extends LeanWikiPage<?>> pages;
        if (loadXml) {
            pages = readXml(input, inputFile.getName().endsWith(".bz2"));
        } else {
            pages = readFromSerialized(input);
        }
        System.out.printf("Read %s in %d ms%n", inputFile, System.currentTimeMillis() - loadStart);
        if (fos != null) {
            long writeStart = System.currentTimeMillis();
            System.out.printf("Starting to write output to %s%n", outputFile);
            try {
                WikiSerialization.serialize((List<PackedWikiPage>) pages, fos.getChannel());
                fos.getFD().sync();
                fos.flush();
                fos.close();
            } catch (IOException e) {
                System.err.printf("Encountered an error %s%n", e.getMessage());
                System.out.printf("Finished in %d ms%n", System.currentTimeMillis() - writeStart);
                System.exit(1);
            }
            System.out.printf("Finished in %d ms%n", System.currentTimeMillis() - writeStart);
        }
        if (interactive) {
            System.out.println("Staring interactive mode");
            long convertStart = System.currentTimeMillis();
            pages = WikiPageConverter.convertPacked(pages).getPages();
            System.out.printf("Conversion took %s ms%n", System.currentTimeMillis() - convertStart);
            long[] statistics = reportStatistics(pages);
            System.out.printf("There are %d pages in total%n", pages.size());
            System.out.printf("The largest id found is %d%n", statistics[0]);
            System.out.printf("There are %d links in total%n", statistics[1]);
            System.out.printf("The largest amount of links found is %d%n", statistics[2]);
            System.out.printf("Total length of the titles is %d bytes%n", statistics[3]);
            System.out.printf("The longest title is %d bytes%n", statistics[4]);


            long initStart = System.currentTimeMillis();
            WikiRoutes<?> routes = new WikiRoutes(pages);
            System.out.printf("Initializing routes took %d ms%n", System.currentTimeMillis() - initStart);
            System.out.println(routes.findRoute("April", "Ice cream"));
        }
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

}
