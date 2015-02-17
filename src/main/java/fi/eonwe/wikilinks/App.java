package fi.eonwe.wikilinks;

import fi.eonwe.wikilinks.jaxb.PageType;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import javax.xml.stream.XMLStreamException;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Hello world!
 */
public class App {
    public static void main(String[] args) throws IOException, XMLStreamException {
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
        streamFrom(bis);
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

    private static void JAXBReadFrom(InputStream stream) {
        int[] iters = new int[] { 0 };
        long startTime = System.currentTimeMillis();

        WikiStreamer.readFrom(stream).forEach(new Consumer<PageType>() {
            @Override
            public void accept(PageType page) {
                if ((++iters[0] & 32767) == 0) {
                    System.out.printf("%d articles so far%n", iters[0]);
                }
//                if (page.getRedirect() != null) {
//                    System.out.printf("%s -> %s%n", page.getTitle(), page.getRedirect().getTitle());
//                } else {
//                    System.out.printf("%s (ns = %d, id = %d)%n", page.getTitle(),
//                            page.getNs(),
//                            page.getId());
//                }
            }
        });
        System.out.printf("Read %d articles in %d ms%n", iters[0], System.currentTimeMillis() - startTime);

    }
}
