package fi.eonwe.wikilinks;

/**
 */
public interface WikiPage {

    boolean isRedirect();
    long getId();
    String getTitle();

}
