package fi.eonwe.wikilinks;

import java.util.function.LongConsumer;

/**
 */
public interface LeanWikiPage<T extends LeanWikiPage<T>> {

    int getTitleLength();
    String getTitle();
    void forEachLink(LongConsumer procedure);
    int getLinkCount();
    long getId();

    int compareTitle(T other);
}
