package fi.eonwe.wikilinks;

import com.google.common.collect.ImmutableList;
import fi.eonwe.wikilinks.jaxb.MediaWikiType;
import fi.eonwe.wikilinks.jaxb.PageType;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

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

    public static Unmarshaller getUnmarshaller() {
        try {
            return JAXB_CONTEXT_THREAD_LOCAL.get().createUnmarshaller();
        } catch (JAXBException e) {
            throw new AssertionError(e);
        }
    }

    public static Iterable<PageType> readFrom(InputStream stream) {
        Unmarshaller unmarshaller = getUnmarshaller();
        try {
            JAXBElement root = (JAXBElement) unmarshaller.unmarshal(stream);
            return ((MediaWikiType) root.getValue()).getPage();
        } catch (JAXBException e) {
            e.printStackTrace(System.err);
            return Collections.emptyList();
        }
    }

    private static Iterator<WikiPage> streamFrom(XMLStreamReader reader) {
        return new Iterator<WikiPage>() {

            private boolean advanced = false;
            private WikiPage page = null;

            @Override
            public boolean hasNext() {
                if (!advanced) advance();
                return page != null;
            }

            @Override
            public WikiPage next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("out of elements");
                }
                WikiPage toReturn = page;
                advance();
                return toReturn;
            }

            private void advance() {
                boolean pageStarted = false;
                String title = null;
                long id = -1;
                long ns = -1;
                ImmutableList<String> links = null;
                try {
                    while(reader.hasNext()) {
                        final int eventType = reader.next();
                        if (eventType == XMLStreamConstants.START_ELEMENT) {
                            switch (reader.getLocalName()) {
                                case "page": pageStarted = true; break;
                                case "title": title = reader.getElementText(); break;
                                case "id": id = Long.parseLong(reader.getElementText()); break;
                                case "ns": ns = Long.parseLong(reader.getElementText()); break;
                                case "text": links = getLinks(reader.getElementText()); break;
                            }
                        } else if (eventType == XMLStreamConstants.END_ELEMENT && "page".equals(reader.getLocalName())) {
                            assert title != null;
                            assert id != -1;
                            assert ns != -1;
                            page = new WikiPage(title, id, ns, links);
                            advanced = true;
                            break;
                        } else if (eventType == XMLStreamConstants.END_DOCUMENT) {
                            eof();
                            break;
                        }
                    }
                } catch (XMLStreamException e) {
                    eof();
                }
            }

            private void eof() {
                advanced = true;
                page = null;
            }

        };
    }

    private static ImmutableList<String> getLinks(String wikiText) {
        return ImmutableList.of();
    }

    public static Iterable<WikiPage> streamFrom(InputStream is) throws XMLStreamException {
        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(is);
        assert reader.getEventType() == XMLStreamConstants.START_DOCUMENT;
        return () -> streamFrom(reader);
    }

}
