package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.openhft.koloboke.collect.map.hash.HashObjObjMap;
import net.openhft.koloboke.collect.map.hash.HashObjObjMaps;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

public class WikiLinksTest {

    private static class ByteArrayListChannel implements WritableByteChannel {

        private boolean open = true;
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        @Override
        public int write(ByteBuffer src) throws IOException {
            ByteBuffer buffer = src.duplicate();
            byte[] tmp = new byte[4096];
            int written = 0;
            int readLen = 0;
            do {
                int left = buffer.limit() - buffer.position();
                readLen = Math.min(left, tmp.length);
                buffer.get(tmp, 0, readLen);
                bos.write(tmp, 0, readLen);
                written += readLen;
            } while (readLen > 0);
            return written;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }

    @Test
    public void itResolvesRedirects() {
        Map<String, PagePointer> map = Maps.newHashMap();
        WikiRedirectPage fooDir = new WikiRedirectPage("foo-redir", 0, "foo");
        WikiRedirectPage foofooDir = new WikiRedirectPage("foo-foo-redir", 1, "foo-redir");
        WikiPageData fooPage = new WikiPageData("foo", 2, new PagePointer[0]);
        for (WikiPage page : new WikiPage[] { fooDir, foofooDir, fooPage}) {
            map.put(page.getTitle(), new PagePointer(page));
        }
        WikiProcessor.resolveRedirects(convert(map));
        assertThat(map.get(fooDir.getTitle()).page, is(equalTo(fooPage)));
        assertThat(map.get(foofooDir.getTitle()).page, is(equalTo(fooPage)));
    }

    @Test(timeout = 1000L)
    public void itResolvesInfinityRedirects() {
        Map<String, PagePointer> map = Maps.newHashMap();
        WikiRedirectPage fooDir = new WikiRedirectPage("foo-redir", 0, "foo-foo-foo-redir");
        WikiRedirectPage foofooDir = new WikiRedirectPage("foo-foo-redir", 1, "foo-redir");
        WikiRedirectPage foofoofooDir = new WikiRedirectPage("foo-foo-foo-redir", 2, "foo-redir");
        WikiPageData fooPage = new WikiPageData("foo", 3, new PagePointer[0]);
        for (WikiPage page : new WikiPage[] { fooDir, foofooDir, foofoofooDir, fooPage}) {
            map.put(page.getTitle(), new PagePointer(page));
        }
        WikiProcessor.resolveRedirects(convert(map));
        assertThat(map.get(fooDir.getTitle()).page, is(nullValue()));
        assertThat(map.get(foofooDir.getTitle()).page, is(nullValue()));
        assertThat(map.get(foofoofooDir.getTitle()).page, is(nullValue()));
    }

    private HashObjObjMap<String, PagePointer> convert(Map<String, PagePointer> map) {
        return HashObjObjMaps.newImmutableMap(map);
    }

    @Test
    public void itPacksPagesCorrectly() throws Exception {
        Map<String, PagePointer> map = createSimpleDenseGraph(4, "title_");
        assertThat(map.size(), is(equalTo(4)));
        List<PackedWikiPage> packedWikiPages = WikiProcessor.packPages(convert(map));
        for (int i = 0; i < map.size(); i++) {
            PackedWikiPage page = packedWikiPages.get(i);
            assertThat(page.getId(), is(equalTo(Long.valueOf(i))));
            assertThat(page.getTitle(), is(equalTo("title_" + i)));
            assertThat(page.getLength(), is(equalTo(getLength(page))));
        }
    }

    private static HashObjObjMap<String, PagePointer> createSimpleDenseGraph(int size, String titlePrefix) {
        return createSimpleDenseGraph(size, titlePrefix, false);
    }

    private static HashObjObjMap<String, PagePointer> createSimpleDenseGraph(int size, String titlePrefix, boolean duplicates) {
        PagePointer[] pointers = new PagePointer[size];
        for (int i = 0; i < pointers.length; i++) {
            pointers[i] = new PagePointer(null);
        }
        HashObjObjMap<String, PagePointer> map = HashObjObjMaps.newMutableMap();
        for (int i = 0; i < pointers.length; i++) {
            List<PagePointer> pagePointers = Lists.newArrayList();
            for (int repeat = 0; repeat < (duplicates ? 2 : 1); repeat++) {
                for (int j = pointers.length - 1; j >= 0; j--) {
                    if (i == j) continue;
                    pagePointers.add(pointers[j]);
                }
            }
            String title = titlePrefix + i;
            pointers[i].page = new WikiPageData(title, i, pagePointers.toArray(new PagePointer[pagePointers.size()]));
            map.put(title, pointers[i]);
        }
        return map;
    }

    @Test
    public void packingRemovesDuplicates() {
        final String prefix = "foo_title_";
        List<PackedWikiPage> readFromXml = WikiProcessor.packPages(convert(createSimpleDenseGraph(4, prefix, true)));
        readFromXml.forEach(p -> {
            long[] links = p.getLinks();
            Set<Long> set = Sets.newHashSet();
            Arrays.stream(links).forEach(set::add);
            assertThat(links.length, is(equalTo(set.size())));
        });
    }

    @Test
    public void deserializeEqualsUnserialized() throws IOException {
        final String prefix = "foo_title_";
        List<PackedWikiPage> readFromXml = WikiProcessor.packPages(convert(createSimpleDenseGraph(512, prefix)));
        ByteArrayListChannel channel = new ByteArrayListChannel();
        List<PackedWikiPage> read = readFromXml;
        for (int i = 0; i < 5; i++) {
            WikiSerialization.serialize(read, channel);
            ByteBuffer input = ByteBuffer.wrap(channel.bos.toByteArray());
            read = WikiSerialization.deserialize(input);
        }

        assertThat(read.size(), is(readFromXml.size()));
        for (int i = 0; i < read.size(); i++) {
            PackedWikiPage fromXml = readFromXml.get(i);
            PackedWikiPage deserialized = read.get(i);
            assertThat(deserialized.getTitle(), is(equalTo(fromXml.getTitle())));
            assertThat(deserialized.getTitle(), startsWith(prefix));
            assertThat(deserialized, is(equalTo(fromXml)));
        }
    }

    @Test
    public void deserializeEqualsUnserializedDisk() throws IOException {
        File tmpFile = File.createTempFile("disk-serialization-test", "tmp");
        tmpFile.deleteOnExit();
        final String prefix = "foo_title_";
        List<PackedWikiPage> readFromXml = WikiProcessor.packPages(convert(createSimpleDenseGraph(512, prefix)));
        FileOutputStream fos = new FileOutputStream(tmpFile);
        WikiSerialization.serialize(readFromXml, fos.getChannel());
        fos.close();
        FileInputStream fin = new FileInputStream(tmpFile);
        FileChannel fc = fin.getChannel();
        long size = fc.size();
        List<PackedWikiPage> readFromFile = WikiSerialization.readFromSerialized(fc);

        assertThat(readFromFile.size(), is(readFromXml.size()));
        for (int i = 0; i < readFromXml.size(); i++) {
            PackedWikiPage fromXml = readFromXml.get(i);
            PackedWikiPage deserialized = readFromFile.get(i);
            assertThat(deserialized.getTitle(), is(equalTo(fromXml.getTitle())));
            assertThat(deserialized.getTitle(), startsWith(prefix));
            assertThat(deserialized, is(equalTo(fromXml)));
        }
    }


    private int getLength(PackedWikiPage page) throws NoSuchFieldException, IllegalAccessException {
        Field f = page.getClass().getDeclaredField("data");
        f.setAccessible(true);
        return ((ByteBuffer) f.get(page)).capacity();
    }

}
