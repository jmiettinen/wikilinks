package fi.eonwe.wikilinks;

import java.util.List;

/**
 */
public class WikiRedirectPage implements WikiPage {

    private final String title;
    private final long id;
    private final String target;

    public WikiRedirectPage(String title, long id, String target) {
        this.title = title;
        this.id = id;
        this.target = target;
    }

    @Override
    public boolean isRedirect() { return true; }

    @Override
    public long getId() { return id; }

    @Override
    public String getTitle() { return title; }

    public String getTarget() { return target; }

}
