package fi.eonwe.wikilinks;

import fi.eonwe.wikilinks.jaxb.PageType;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 */
public class WikiStreamer {

    public static InputStream fromBunzipStream(InputStream bunzipStream) throws IOException {
        BZip2CompressorInputStream stream = new BZip2CompressorInputStream(bunzipStream, true);
        return stream;
    }

    private static final ThreadLocal<JAXBContext> JAXB_CONTEXT_THREAD_LOCAL = new ThreadLocal<JAXBContext>() {
        @Override
        protected JAXBContext initialValue() {
            try {
                return JAXBContext.newInstance("fi.eonwe.wikilinks.jaxb");
            } catch (JAXBException e) {
                throw new AssertionError(e);
            }
        }
    };


    public static JAXBContext getJAXB() {
        return JAXB_CONTEXT_THREAD_LOCAL.get();
    }

    public Iterator<PageType> readFrom(InputStream stream) {
        return (1 < 2 ? null : null);
    }

}
