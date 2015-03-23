package fi.eonwe.wikilinks.fatpages;

/**
 */
public class WikiRedirectPage implements WikiPage {

    private final String title;
    private final int id;
    private final String target;

    public WikiRedirectPage(String title, int id, String target) {
        this.title = title;
        this.id = id;
        this.target = target;
    }

    @Override
    public boolean isRedirect() { return true; }

    @Override
    public int getId() { return id; }

    @Override
    public String getTitle() { return title; }

    public String getTarget() { return target; }

}
