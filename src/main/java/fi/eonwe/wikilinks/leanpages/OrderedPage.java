package fi.eonwe.wikilinks.leanpages;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 */
public abstract class OrderedPage {

    private final BufferWikiPage page;

    protected OrderedPage(BufferWikiPage page) {
        this.page = page;
    }

    public int getId() { return page.getId(); }
    public BufferWikiPage getPage() { return page; }
    public boolean isRedirect() { return false; }
    public abstract void forEachLinkIndex(IntConsumer c);

    private static class RedirectPage extends OrderedPage {

        private final int targetId;

        protected RedirectPage(BufferWikiPage page, int targetId) {
            super(page);
            this.targetId = targetId;
        }

        @Override
        public boolean isRedirect() {
            return true;
        }

        @Override
        public void forEachLinkIndex(IntConsumer c) {
            c.accept(targetId);
        }
    }

    private static class NormalPage extends OrderedPage {

        private final int[] targetIndices;

        protected NormalPage(BufferWikiPage page, int[] targetIndices) {
            super(page);
            this.targetIndices = targetIndices;
        }

        @Override
        public void forEachLinkIndex(IntConsumer c) {
            for (int i : targetIndices) c.accept(i);
        }
    }

    public static OrderedPage convert(BufferWikiPage page, IntFunction<Integer> mapping) {
        int linkCount = page.getLinkCount();
        if (page.isRedirect()) {
            if (linkCount == 1) {
                int[] targetId = {0};
                page.forEachLink(i -> targetId[0] = i);
                return new RedirectPage(page, mapping.apply(targetId[0]));
            } else {
                throw new AssertionError("Link count is " + page.getLinkCount() + " when it should be 1");
            }
        } else {
            int[] arr = new int[linkCount];
            int[] index = {0};
            page.forEachLink(i -> arr[index[0]++] = mapping.apply(i));
            Arrays.sort(arr);
            return new NormalPage(page, arr);
        }
    }

}
