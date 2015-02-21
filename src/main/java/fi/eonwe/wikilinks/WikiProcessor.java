package fi.eonwe.wikilinks;

import com.carrotsearch.hppc.EmptyArrays;
import com.carrotsearch.hppc.LongArrayList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;
import info.bliki.wiki.dump.WikiPatternMatcher;
import info.bliki.wiki.dump.WikiXMLParser;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 */
public class WikiProcessor {

    private static final int VERSION_NUMBER = 0x52ea2a00 | 1;

    public static List<PackedWikiPage> readPages(InputStream input) {
        WikiProcessor processor = new WikiProcessor();
        Map<String, PagePointer> pages = processor.preProcess(input);
        WikiProcessor.resolveRedirects(pages);
        List<PackedWikiPage> packedPages = WikiProcessor.packPages(pages);
        return packedPages;
    }

    public Map<String, PagePointer> preProcess(InputStream input) {
        try {
            final HashMap<String, PagePointer> titleToPage = Maps.newHashMap();
            WikiXMLParser parser = new WikiXMLParser(input, new IArticleFilter() {
                @Override
                public void process(WikiArticle article, Siteinfo siteinfo) throws SAXException {
                    if (article.isMain()) {
                        WikiPatternMatcher matcher = new WikiPatternMatcher(article.getText());
                        long id = Long.parseLong(article.getId());
                        WikiPage page;
                        if (matcher.isRedirect()) {
                            page = new WikiRedirectPage(article.getTitle(), id, matcher.getRedirectText());
                            PagePointer pointer = titleToPage.get(page.getTitle());
                            if (pointer != null) {
                                pointer.page = page;
                            } else {
                                pointer = new PagePointer(page);
                                titleToPage.put(page.getTitle(), pointer);
                            }
                        } else {
                            List<String> links = matcher.getLinks();
                            List<PagePointer> pointerLinks = Lists.newArrayList();
                            for (String link : links) {
                                PagePointer ptr = titleToPage.get(link);
                                if (ptr == null) {
                                    ptr = new PagePointer(null);
                                    titleToPage.put(link, ptr);
                                }
                                pointerLinks.add(ptr);
                            }
                            page = new WikiPageData(article.getTitle(), id, pointerLinks);
                            PagePointer pointer = titleToPage.get(page.getTitle());
                            if (pointer != null) {
                                pointer.page = page;
                            } else {
                                pointer = new PagePointer(page);
                                titleToPage.put(page.getTitle(), pointer);
                            }
                        }
                    }
                }
            });
            parser.parse();
            return titleToPage;
        } catch (SAXException | IOException e) {
            return Collections.emptyMap();
        }
    }

    public static void resolveRedirects(Map<String, PagePointer> map) {
        map.values().stream().filter(p -> p.page != null && p.page.isRedirect()).forEach(p -> p.page = resolveUltimateTarget(p, map));
    }

    private static WikiPage resolveUltimateTarget(PagePointer redirect, Map<String, PagePointer> map) {
        WikiPage immediateTarget = redirect.page;
        if (immediateTarget == null || !(immediateTarget instanceof WikiRedirectPage)) return immediateTarget;
        WikiRedirectPage redirectPage = (WikiRedirectPage) immediateTarget;
        PagePointer redirectPointer = map.get(redirectPage.getTarget());
        WikiPage ultimateTarget;
        if (redirectPointer == null) {
            ultimateTarget = null;
        } else {
            ultimateTarget = resolveUltimateTarget(redirectPointer, map);
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

    public static List<PackedWikiPage> packPages(Map<String, PagePointer> map) {
        List<PackedWikiPage> list = Lists.newArrayListWithCapacity(map.size());
        Iterator<Map.Entry<String, PagePointer>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PagePointer> entry = iterator.next();
            WikiPageData page = (WikiPageData) entry.getValue().page;
            if (page != null) {
                long[] links = page.getLinks().stream().filter(p -> p.page != null).mapToLong(p -> p.page.getId()).toArray();
                Arrays.sort(links);
                if (links.length == 0) links = EMPTY_ARRAY;
                PackedWikiPage packedPage = new PackedWikiPage(page.getId(), links, entry.getKey());
                list.add(packedPage);
            }
            iterator.remove();
        }
        list.sort((a,b) -> Long.compare(a.getId(), b.getId()));
        return list;
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
