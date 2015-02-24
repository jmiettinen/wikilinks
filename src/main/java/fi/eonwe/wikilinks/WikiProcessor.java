package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;
import com.googlecode.concurrenttrees.common.KeyValuePair;
import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.SmartArrayBasedNodeFactory;
import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;
import info.bliki.wiki.dump.WikiPatternMatcher;
import info.bliki.wiki.dump.WikiXMLParser;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 */
public class WikiProcessor {

    private static final int VERSION_NUMBER = 0x52ea2a00 | 1;

    public static List<PackedWikiPage> readPages(InputStream input) {
        WikiProcessor processor = new WikiProcessor();
        ConcurrentRadixTree<PagePointer> pages = processor.preProcess(input);
//        printStatistics(pages);
        WikiProcessor.resolveRedirects(pages);
//        printStatistics(pages);
        List<PackedWikiPage> packedPages = WikiProcessor.packPages(pages);
        return packedPages;
    }

    public ConcurrentRadixTree<PagePointer> preProcess(InputStream input) {
        final ConcurrentRadixTree<PagePointer> titleToPage = new ConcurrentRadixTree<>(new SmartArrayBasedNodeFactory());
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
                                PagePointer ptr = titleToPage.getValueForExactKey(link);
                                if (ptr == null) {
                                    ptr = new PagePointer(null);
                                    titleToPage.put(link, ptr);
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

    private static void fixPagePointers(ConcurrentRadixTree<PagePointer> titleToPage, WikiPage page) {
        PagePointer pointer = titleToPage.getValueForExactKey(page.getTitle());
        if (pointer != null) {
            pointer.page = page;
        } else {
            pointer = new PagePointer(page);
            titleToPage.put(page.getTitle(), pointer);
        }
    }

    public static void resolveRedirects(ConcurrentRadixTree<PagePointer> map) {
        map.getValuesForKeysStartingWith("").forEach(p -> {
            if (p.page != null && p.page.isRedirect()) {
                p.page = resolveUltimateTarget(p, map, null);
            }
        });
//        map.values().stream().filter(p -> p.page != null && p.page.isRedirect()).forEach(p -> p.page = resolveUltimateTarget(p, map));
    }

    private static WikiPage resolveUltimateTarget(PagePointer redirect, ConcurrentRadixTree<PagePointer> map, IdentityHashMap<WikiPage, Boolean> visited) {
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
        PagePointer redirectPointer = map.getValueForExactKey(redirectPage.getTarget());
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

    public static List<PackedWikiPage> packPages(ConcurrentRadixTree<PagePointer> map) {
        List<PackedWikiPage> list = Lists.newArrayListWithCapacity(map.size());
        Iterable<KeyValuePair<PagePointer>> iter = getContent(map);
//        Iterator<Map.Entry<String, PagePointer>> iterator = map.entrySet().iterator();
        Iterator<KeyValuePair<PagePointer>> iterator = iter.iterator();
        while (iterator.hasNext()) {
            KeyValuePair<PagePointer> entry = iterator.next();
            WikiPageData page = (WikiPageData) entry.getValue().page;
            if (page != null) {
                long[] links = Arrays.stream(page.getLinks()).filter(p -> p.page != null).mapToLong(p -> p.page.getId()).distinct().toArray();
                Arrays.sort(links);
                if (links.length == 0) links = EMPTY_ARRAY;
                PackedWikiPage packedPage = new PackedWikiPage(page.getId(), links, entry.getKey().toString());
                list.add(packedPage);
            }
//            iterator.remove();
        }
        list.sort((a,b) -> Long.compare(a.getId(), b.getId()));
        return list;
    }

    private static Iterable<KeyValuePair<PagePointer>> getContent(ConcurrentRadixTree<PagePointer> tree) {
        try {
            Method m = ConcurrentRadixTree.class.getDeclaredMethod("getKeyValuePairsForKeysStartingWith", CharSequence.class);
            m.setAccessible(true);
            return (Iterable<KeyValuePair<PagePointer>>) m.invoke(tree, "");
        } catch (NoSuchMethodException|InvocationTargetException|IllegalAccessException e) {
            if (e instanceof InvocationTargetException) {
                Throwable cause = ((InvocationTargetException) e).getCause();
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                throw new RuntimeException(cause);
            }
            throw new AssertionError(e);
        }

    }

    public static List<PackedWikiPage> deserialize(ByteBuffer input) {
        ByteBuffer buffer = input.duplicate();
        int versionNumber = buffer.getInt();
        if (versionNumber != VERSION_NUMBER) {
            throw new IllegalArgumentException(
                    String.format("Magic cookie %d did not match the expected %d", versionNumber, VERSION_NUMBER));
        }
        int count = Ints.checkedCast(buffer.getLong());
        List<PackedWikiPage> pages = Lists.newArrayListWithCapacity(count);
        for (int i = 0, offset = buffer.position(); i < count; i++) {
            PackedWikiPage newPage = new PackedWikiPage(buffer, offset);
            pages.add(newPage);
            offset += newPage.getLength();
        }
        return pages;
    }


    public static void serialize(List<PackedWikiPage> graph, ByteBuffer output) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES);
        buffer.putInt(VERSION_NUMBER);
        buffer.putLong(graph.size());
        buffer.flip();
        output.put(buffer);
        for (PackedWikiPage packedWikiPage : graph) {
            packedWikiPage.writeTo(output);
        }
    }

    public static void serialize(List<PackedWikiPage> graph, WritableByteChannel channel) throws IOException {
        // Format:
        // i32: magic bytes
        // i64: article_count
        // article_count repeats:
        //   i64 article_id
        //   i32 link_count
        //   link_count repeats:
        //     i64 link_target_id
        //   i32 title_byte_size
        //     title_byte_size repeats:
        //     i8 title_byte
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Long.BYTES);
        buffer.putInt(VERSION_NUMBER);
        buffer.putLong(graph.size());
        buffer.flip();
        channel.write(buffer);
        for (PackedWikiPage packedWikiPage : graph) {
            packedWikiPage.writeTo(channel);
        }
    }


}
