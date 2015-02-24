package fi.eonwe.wikilinks;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

/**
 */
public class WikiPageData implements WikiPage {

    private final String title;
    private final long id;
    private final PagePointer[] links;
//    private final byte[] links;

    public WikiPageData(String title, long id, PagePointer[] links) {
        this.title = title;
        this.id = id;
        this.links = links;
//        this.links = packLinks(links);
    }

    @Override
    public boolean isRedirect() {
        return false;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public PagePointer[] getLinks() { return links; }
}
