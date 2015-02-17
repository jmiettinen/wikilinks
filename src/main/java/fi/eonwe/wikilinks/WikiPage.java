package fi.eonwe.wikilinks;

import com.google.common.collect.ImmutableList;

/**
 */
public class WikiPage {

    private final String title;
    private final long id;
    private final long ns;
    private final ImmutableList<String> links;

    public WikiPage(String title, long id, long ns, ImmutableList<String> links) {
        this.title = title;
        this.id = id;
        this.ns = ns;
        this.links = links;
    }

    public long getId() { return id; }
    public long getNs() { return ns; }
    public String getTitle() { return title; }
    public ImmutableList<String> getLinks() { return links; }
}
