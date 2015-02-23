package fi.eonwe.wikilinks;

import com.carrotsearch.hppc.ByteArrayList;
import com.carrotsearch.hppc.EmptyArrays;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

/**
 */
public class WikiPageData implements WikiPage {

    private final String title;
    private final long id;
    private final PagePointer[] links;
//    private final byte[] links;

    public WikiPageData(String title, long id, PagePointer[] links) {
        this.title = title;
        this.id = id;
        this.links = links;
//        this.links = packLinks(links);
    }

    private static byte[] packLinks(List<String> links) {
        if (links.isEmpty()) return EmptyArrays.EMPTY_BYTE_ARRAY;
        ByteArrayList bytes = new ByteArrayList(links.size() * 8);
        for (String link : links) {
            byte[] linkBytes = link.getBytes(Charsets.UTF_8);
            int len = linkBytes.length;
            if (len > Short.MAX_VALUE) throw new IllegalArgumentException("Too long string (" + len + " bytes)");
            bytes.add((byte) (len & 0xFF));
            bytes.add((byte) ((len & 0xFF00) >> 8));
            bytes.add(linkBytes);
        }
        return bytes.toArray();
    }

    private static List<String> unpackLinks(byte[] linksData) {
        if (linksData.length < 2) return Collections.emptyList();
        int estimate = linksData.length / 8;
        List<String> list = Lists.newArrayListWithCapacity(estimate);
        int i = 0;
        while (i < linksData.length) {
            byte b1 = linksData[i++];
            byte b2 = linksData[i++];
            int len = b1 | (b2 << 8);
            list.add(new String(linksData, i, len, Charsets.UTF_8));
            i += len;
        }
        return list;
    }

    @Override
    public boolean isRedirect() {
        return false;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public PagePointer[] getLinks() { return links; }
}
