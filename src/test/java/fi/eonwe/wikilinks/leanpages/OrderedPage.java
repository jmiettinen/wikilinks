package fi.eonwe.wikilinks.leanpages;

import java.util.function.IntConsumer;

/**
 * Old page-format that is only used in tests anymore.
 */
public class OrderedPage {

    private final BufferWikiPage page;
    private final int[] targetIndices;

    protected OrderedPage(BufferWikiPage page, int[] targetIndices) {
        this.page = page;
        this.targetIndices = targetIndices;
    }

    public int getId() { return page.getId(); }
    public BufferWikiPage getPage() { return page; }

    public void forEachLinkIndex(IntConsumer c) {
        for (int i : targetIndices) c.accept(i);
    }

}
