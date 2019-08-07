package fi.eonwe.wikilinks.fatpages;

/**
 */
public interface WikiPage {

    /**
     * Is this a redirect to another page or an actual page?
     * @return
     */
    boolean isRedirect();

    /**
     * @return a unique identifier for this page
     */
    int getId();

    /**
     * @return
     */
    String getTitle();

}
