package fi.eonwe.wikilinks.fatpages;

/**
*/
public class PagePointer {

    private static int counter = 0;

    public WikiPage page;
    public final int id = counter++;

    public PagePointer(WikiPage page) {
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
