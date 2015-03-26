package fi.eonwe.wikilinks.leanpages;

import fi.eonwe.wikilinks.utils.Functions;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongToIntFunction;

/**
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
    public boolean isRedirect() { return false; }
    public void forEachLinkIndex(IntConsumer c) {
        for (int i : targetIndices) c.accept(i);
    }
    public int[] getTargetIndices() { return targetIndices; }

    public static OrderedPage convert(BufferWikiPage page, Functions.IntInt mapping) {
        int linkCount = page.getLinkCount();
        int[] arr = new int[linkCount];
        int[] index = {0};
        page.forEachLink(i -> arr[index[0]++] = mapping.apply(i));
        Arrays.sort(arr);
        return new OrderedPage(page, arr);
    }

}
