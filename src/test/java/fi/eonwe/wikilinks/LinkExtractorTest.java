package fi.eonwe.wikilinks;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 */
public class LinkExtractorTest {

    @Test
    public void findsNamespace() {
        assertThat(getTitle("[[cardboard sandwiches]]"), is("cardboard sandwiches"));
        assertThat(getTitle("[[Wiktionary:Hello]]"), is("Hello"));
        assertThat(getTitle("[[Wikipedia:Manual of Style]]"), is("Manual of Style"));
    }

    private static String getTitle(String wikiText) {
        LinkExtractor le = extractorFor(wikiText);
        if (!le.advance()) {
            return null;
        }
        return le.getTitle();
    }

    private static LinkExtractor extractorFor(String wikiText) {
        LinkExtractor le = new LinkExtractor();
        le.resetTo(wikiText);
        return le;
    }

}
