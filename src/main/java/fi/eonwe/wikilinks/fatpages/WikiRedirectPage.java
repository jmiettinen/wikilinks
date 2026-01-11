package fi.eonwe.wikilinks.fatpages;

/**
 *
 */
public record WikiRedirectPage(String title, int id, String target) implements WikiPage {

    @Override
    public boolean isRedirect() {
        return true;
    }

}
