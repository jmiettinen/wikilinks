package fi.eonwe.wikilinks;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 */
public class WikiStreamer {

    private static String[] EMPTY = new String[0];

    public static InputStream fromBunzipStream(InputStream bunzipStream) throws IOException {
        BZip2CompressorInputStream stream = new BZip2CompressorInputStream(bunzipStream, true);
        return stream;
    }

    private static Iterator<WikiPageData> streamFrom(XMLStreamReader reader) {
        return new Iterator<WikiPageData>() {

            private boolean advanced = false;
            private WikiPageData page = null;

            @Override
            public boolean hasNext() {
                if (!advanced) advance();
                return page != null;
            }

            @Override
            public WikiPageData next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("out of elements");
                }
                WikiPageData toReturn = page;
                advance();
                return toReturn;
            }

            private void advance() {
                boolean pageStarted = false;
                String title = null;
                long id = -1;
                long ns = -1;
                String[] links = null;
                LinkExtractor extractor = new LinkExtractor();

                try {
                    while(reader.hasNext()) {
                        final int eventType = reader.next();
                        if (eventType == XMLStreamConstants.START_ELEMENT) {
                            switch (reader.getLocalName()) {
                                case "page": pageStarted = true; break;
                                case "title": title = reader.getElementText(); break;
                                case "id": id = Long.parseLong(reader.getElementText()); break;
                                case "ns": ns = Long.parseLong(reader.getElementText()); break;
                                case "text": links = getLinks(extractor, reader.getElementText()); break;
                            }
                        } else if (eventType == XMLStreamConstants.END_ELEMENT && "page".equals(reader.getLocalName())) {
                            assert title != null;
                            assert id != -1;
                            assert ns != -1;
//                            page = new WikiPageData(title, id, links);
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

    private static String[] getLinks(LinkExtractor le, String wikiText) {
        le.resetTo(wikiText);
        HashSet<String> links = null;
        while (le.advance()) {
            String target = le.getTitle();
            String namespace = le.getNamespace();
            if (namespace == null || "Wikipedia".equals(namespace)) {
                if (links == null) links = new HashSet<>();
                links.add(target);
            }
        }
        if (links == null) return EMPTY;
        return links.toArray(new String[links.size()]);
    }

    public static Iterable<WikiPageData> streamFrom(InputStream is) throws XMLStreamException {
        XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(is);
        assert reader.getEventType() == XMLStreamConstants.START_DOCUMENT;
        return () -> streamFrom(reader);
    }

}
