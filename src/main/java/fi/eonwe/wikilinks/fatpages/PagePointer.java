package fi.eonwe.wikilinks.fatpages;

import javax.annotation.Nullable;

/**
*/
public class PagePointer {

    private static int counter = 0;

    @Nullable
    public WikiPage page;
    public final int id = counter++;

    public PagePointer(@Nullable WikiPage page) {
        this.page = page;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof PagePointer && ((PagePointer) obj).id == id);
    }
}
