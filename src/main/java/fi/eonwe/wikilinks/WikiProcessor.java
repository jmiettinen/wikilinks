package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import fi.eonwe.wikilinks.fatpages.PagePointer;
import fi.eonwe.wikilinks.fatpages.WikiPage;
import fi.eonwe.wikilinks.fatpages.WikiPageData;
import fi.eonwe.wikilinks.fatpages.WikiRedirectPage;
import fi.eonwe.wikilinks.leanpages.BufferWikiPage;
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
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 */
public class WikiProcessor {

    public static List<BufferWikiPage> readPages(InputStream input) {
        WikiProcessor processor = new WikiProcessor();
        HashObjObjMap<String, PagePointer> pages = processor.preProcess(input);
//        printStatistics(pages);
        WikiProcessor.dropRedirectLoops(pages);
//        printStatistics(pages);
        List<BufferWikiPage> packedPages = WikiProcessor.packPages(pages);
        return packedPages;
    }

    public HashObjObjMap<String, PagePointer> preProcess(InputStream input) {
        final HashObjObjMap<String, PagePointer> titleToPage = HashObjObjMaps.newMutableMap(12_000_000);
        try {
            WikiXMLParser parser = new WikiXMLParser(input, new IArticleFilter() {
                @Override
                public void process(WikiArticle article, Siteinfo siteinfo) {
                    if (article.isMain()) {
                        String text = article.getText();
                        if (text == null) text = "";
                        WikiPatternMatcher matcher = new WikiPatternMatcher(text);
                        int id = Integer.parseInt(article.getId());
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

    public static void dropRedirectLoops(HashObjObjMap<String, PagePointer> map) {
        map.values().stream().filter(p -> p.page != null && p.page.isRedirect()).forEach(p -> p.page = endSomewhere(p, map, null) ? p.page : null);
    }

    private static boolean endSomewhere(PagePointer redirect, HashObjObjMap<String, PagePointer> map, IdentityHashMap<WikiPage, Boolean> visited) {
        WikiPage immediateTarget = redirect.page;
        if (immediateTarget instanceof WikiRedirectPage) {
            if (visited == null) {
                visited = new IdentityHashMap<>();
            }
            if (visited.containsKey(immediateTarget)) {
                return false;
            } else {
                visited.put(immediateTarget, Boolean.TRUE);
            }
            WikiRedirectPage redirectPage = (WikiRedirectPage) immediateTarget;
            PagePointer redirectPointer = map.get(redirectPage.getTarget());
            if (redirectPointer == null) {
                return false;
            } else {
                return endSomewhere(redirectPointer, map, visited);
            }
        } else {
            return true;
        }
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

    private static final int[] EMPTY_ARRAY = new int[0];

    public static List<BufferWikiPage> packPages(HashObjObjMap<String, PagePointer> map) {
        List<BufferWikiPage> list = Lists.newArrayListWithCapacity(map.size());
        map.forEach((title, ptr) -> {
            WikiPage page = ptr.page;
            if (page != null) {
                int[] links;
                boolean isRedirect;
                if (page instanceof WikiRedirectPage) {
                    WikiRedirectPage redirectPage = (WikiRedirectPage) page;
                    String target = redirectPage.getTarget();
                    PagePointer pagePointer = map.get(target);
                    if (pagePointer == null || pagePointer.page == null) {
                        links = EMPTY_ARRAY;
                    } else {
                        links = new int[] { pagePointer.page.getId() };
                    }
                    isRedirect = true;
                } else {
                    WikiPageData pageData = (WikiPageData) page;
                    links = Arrays.stream(pageData.getLinks()).filter(p -> p.page != null).mapToInt(p -> p.page.getId()).distinct().toArray();
                    if (links.length == 0) links = EMPTY_ARRAY;
                    Arrays.sort(links);
                    isRedirect = false;
                }
                BufferWikiPage packedPage = BufferWikiPage.createFrom(page.getId(), links, title, isRedirect);
                list.add(packedPage);
            }
        });
        list.sort(Comparator.comparingLong(BufferWikiPage::getId));
        return list;
    }
}
