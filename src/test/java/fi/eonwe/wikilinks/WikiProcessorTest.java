package fi.eonwe.wikilinks;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import fi.eonwe.wikilinks.leanpages.BufferWikiPage;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "RUN_SLOW_TESTS", matches = "TRUE")
public class WikiProcessorTest {

    @Test
    public void itReadSilesionWikiPageNames() throws IOException {
        try(InputStream is = getClass().getResourceAsStream("/szlwiki-20190801-pages-articles-multistream.xml.bz2");
            InputStream wikiInput = new BZip2CompressorInputStream(is, true)) {
            List<BufferWikiPage> pages = WikiProcessor.readPages(wikiInput);
            Set<String> pageNames = pages.stream().map(BufferWikiPage::getTitle).collect(Collectors.toSet());
            for (String pageToExist : Arrays.asList("Gůrny Ślůnsk", "Gdańsk", "Legwan", "Wikipedyjo")) {
                boolean exists = pageNames.contains(pageToExist);
                assertTrue(exists, String.format("'%s' does not exist", pageToExist));
            }

        }
    }

}
