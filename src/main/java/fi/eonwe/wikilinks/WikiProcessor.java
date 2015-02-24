package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;
import info.bliki.wiki.dump.WikiPatternMatcher;
import info.bliki.wiki.dump.WikiXMLParser;
import net.openhft.koloboke.collect.map.hash.HashObjObjMap;
import net.openhft.koloboke.collect.map.hash.HashObjObjMaps;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 */
public class WikiProcessor {

    public static List<PackedWikiPage> readPages(InputStream input) {
        WikiProcessor processor = new WikiProcessor();
        HashObjObjMap<String, PagePointer> pages = processor.preProcess(input);
//        printStatistics(pages);
        WikiProcessor.resolveRedirects(pages);
//        printStatistics(pages);
        List<PackedWikiPage> packedPages = WikiProcessor.packPages(pages);
        return packedPages;
    }

    public HashObjObjMap<String, PagePointer> preProcess(InputStream input) {
        final HashObjObjMap<String, PagePointer> titleToPage = HashObjObjMaps.newMutableMap(12_000_000);
        try {
            WikiXMLParser parser = new WikiXMLParser(input, new IArticleFilter() {
                @Override
                public void process(WikiArticle article, Siteinfo siteinfo) throws SAXException {
                    if (article.isMain()) {
                        String text = article.getText();
                        if (text == null) text = "";
                        WikiPatternMatcher matcher = new WikiPatternMatcher(text);
                        long id = Long.parseLong(article.getId());
                        WikiPage page;
                        if (matcher.isRedirect()) {
                            page = new WikiRedirectPage(article.getTitle().intern(), id, matcher.getRedirectText().intern());
                            fixPagePointers(titleToPage, page);
                        } else {
                            String[] links = matcher.getLinks().stream().filter(l -> !l.isEmpty()).map(WikiProcessor::possiblyCapitalize).distinct().toArray(String[]::new);
                            PagePointer[] pointerLinks = new PagePointer[links.length];
                            for (int i = 0; i < links.length; i++) {
                                String link = links[i];
                                PagePointer ptr = titleToPage.get(link);
                                if (ptr == null) {
                                    ptr = new PagePointer(null);
                                    titleToPage.put(link.intern(), ptr);
                                }
                                pointerLinks[i] = ptr;
                            }
                            page = new WikiPageData(article.getTitle().intern(), id, pointerLinks);
                            fixPagePointers(titleToPage, page);
                        }
                    }
                }
            });
            parser.parse();
            return titleToPage;
        } catch (SAXException | IOException e) {
            return titleToPage;
        }
    }

    private static String possiblyCapitalize(String linkName) {
        if (linkName.length() != 0 && !Character.isUpperCase(linkName.charAt(0))) {
            char[] chars = linkName.toCharArray();
            chars[0] = Character.toUpperCase(chars[0]);
            return new String(chars);
        }
        return linkName;
    }

    private static void fixPagePointers(HashObjObjMap<String, PagePointer> titleToPage, WikiPage page) {
        PagePointer pointer = titleToPage.get(page.getTitle());
        if (pointer != null) {
            pointer.page = page;
        } else {
            pointer = new PagePointer(page);
            titleToPage.put(page.getTitle(), pointer);
        }
    }

    public static void resolveRedirects(HashObjObjMap<String, PagePointer> map) {
        map.values().stream().filter(p -> p.page != null && p.page.isRedirect()).forEach(p -> p.page = resolveUltimateTarget(p, map, null));
    }

    private static WikiPage resolveUltimateTarget(PagePointer redirect, HashObjObjMap<String, PagePointer> map, IdentityHashMap<WikiPage, Boolean> visited) {
        WikiPage immediateTarget = redirect.page;
        if (immediateTarget == null || !(immediateTarget instanceof WikiRedirectPage)) return immediateTarget;
        if (visited == null) {
            visited = new IdentityHashMap<>();
        }
        if (visited.containsKey(immediateTarget)) {
            // We've already been here, thus we have a cycle.
            return null;
        } else {
            visited.put(immediateTarget, Boolean.TRUE);
        }
        WikiRedirectPage redirectPage = (WikiRedirectPage) immediateTarget;
        PagePointer redirectPointer = map.get(redirectPage.getTarget());
        WikiPage ultimateTarget;
        if (redirectPointer == null) {
            ultimateTarget = null;
        } else {
            ultimateTarget = resolveUltimateTarget(redirectPointer, map, visited);
            redirectPointer.page = ultimateTarget;
        }
        return ultimateTarget;
    }

    public static void printStatistics(Map<String, PagePointer> map) {
        int articleCount = 0;
        int redirectCount = 0;
        int linkCount = 0;
        int nullLinkCount = 0;
        for (PagePointer ptr : map.values()) {
            WikiPage page = ptr.page;
            if (page == null) {
                nullLinkCount++;
                continue;
            }
            if (page instanceof WikiRedirectPage) {
                redirectCount++;
            } else if (page instanceof WikiPageData) {
                articleCount++;
                WikiPageData p = (WikiPageData) page;
                for (PagePointer linkPointer : p.getLinks()) {
                    WikiPage linkedPage = linkPointer.page;
                    if (linkedPage == null) {
                        nullLinkCount++;
                    } else {
                        linkCount++;
                    }
                }
            }
        }
        System.out.printf(
                "Articles: %d%n" +
                "Redirects: %d%n" +
                "Links: %d (/ article: %.2f)%n" +
                "Null links %d%n",
                articleCount, redirectCount,
                linkCount, linkCount / (double) articleCount,
                nullLinkCount);
    }

    private static final long[] EMPTY_ARRAY = new long[0];

    public static List<PackedWikiPage> packPages(HashObjObjMap<String, PagePointer> map) {
        List<PackedWikiPage> list = Lists.newArrayListWithCapacity(map.size());
        map.forEach((title, ptr) -> {
            WikiPageData page = (WikiPageData) ptr.page;
            if (page != null) {
                long[] links = Arrays.stream(page.getLinks()).filter(p -> p.page != null).mapToLong(p -> p.page.getId()).distinct().toArray();
                Arrays.sort(links);
                if (links.length == 0) links = EMPTY_ARRAY;
                PackedWikiPage packedPage = new PackedWikiPage(page.getId(), links, title);
                list.add(packedPage);
            }
        });
        list.sort((a,b) -> Long.compare(a.getId(), b.getId()));
        return list;
    }
}
