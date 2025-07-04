package fi.eonwe.wikilinks.fatpages;

/**
 *
 */
public record WikiPageData(String title, int id, PagePointer[] links) implements WikiPage {

    @Override
    public boolean isRedirect() {
        return false;
    }
}
