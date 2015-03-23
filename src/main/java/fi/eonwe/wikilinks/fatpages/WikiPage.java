package fi.eonwe.wikilinks.fatpages;

/**
 */
public interface WikiPage {

    boolean isRedirect();
    int getId();
    String getTitle();

}
