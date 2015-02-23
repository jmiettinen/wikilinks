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
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
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
            FileChannel channel = fis.getChannel();
            long size = channel.size();
            if (size > Integer.MAX_VALUE) {
                throw new IOException("Too large file (" + size + ")");
            }
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, size);
            return WikiProcessor.deserialize(buffer);
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
        List<PackedWikiPage> pages;
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
                WikiProcessor.serialize(pages, fos.getChannel());
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
            WikiRoutes routes = new WikiRoutes(pages);
            System.out.println(routes.findRoute("April", "Ice cream"));
        }
    }

    public static <T> T handleError(Throwable t) {
        System.err.printf("Encountered error %s. Exiting%n", t.getMessage());
        System.exit(1);
        return null;
    }

}
