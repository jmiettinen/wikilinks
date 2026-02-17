package fi.eonwe.wikilinks.fatpages;

import java.util.List;

/**
 *
 */
public record WikiPageData(String title, int id, List<PagePointer> links) implements WikiPage {

    @Override
    public boolean isRedirect() {
        return false;
    }
}
