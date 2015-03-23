package fi.eonwe.wikilinks.fatpages;

/**
 */
public class WikiPageData implements WikiPage {

    private final String title;
    private final int id;
    private final PagePointer[] links;
//    private final byte[] links;

    public WikiPageData(String title, int id, PagePointer[] links) {
        this.title = title;
        this.id = id;
        this.links = links;
//        this.links = packLinks(links);
    }

    @Override
    public boolean isRedirect() {
        return false;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public PagePointer[] getLinks() { return links; }
}
