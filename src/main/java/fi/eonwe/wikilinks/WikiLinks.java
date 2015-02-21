package fi.eonwe.wikilinks;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Hello world!
 */
public class WikiLinks {
    public static void main(String[] args) throws IOException, XMLStreamException, SAXException {
        String filePath = "src/data/simplewiki-20141222-pages-meta-current.xml";
        if (args.length == 1) {
            filePath = args[0];
        }
        InputStream in = new FileInputStream(filePath);
        if (filePath.endsWith(".bz2")) {
            in = new BZip2CompressorInputStream(in);
        }
        BufferedInputStream bis = new BufferedInputStream(in, 1 << 15);
//        JAXBReadFrom(bis);
//        streamFrom(bis);
        streamFromXml(bis);
    }

    private static void streamFrom(InputStream stream) throws XMLStreamException {
        int[] iters = new int[] { 0 };
        long startTime = System.currentTimeMillis();
        WikiStreamer.streamFrom(stream).forEach(page -> {
            if ((++iters[0] & 32767) == 0) {
                System.out.printf("%d articles so far%n", iters[0]);
            }
        });
        System.out.printf("Read %d articles in %d ms%n", iters[0], System.currentTimeMillis() - startTime);
    }

    private static void streamFromXml(InputStream stream) throws SAXException, IOException {
        WikiProcessor processor = new WikiProcessor();
        Map<String, PagePointer> pages = processor.preProcess(stream);
        WikiProcessor.printStatistics(pages);
        WikiProcessor.resolveRedirects(pages);
        WikiProcessor.printStatistics(pages);
        List<PackedWikiPage> packedPages = WikiProcessor.packPages(pages);

    }
}
