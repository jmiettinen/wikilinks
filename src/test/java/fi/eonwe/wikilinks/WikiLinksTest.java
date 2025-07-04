package fi.eonwe.wikilinks;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import fi.eonwe.wikilinks.fatpages.PagePointer;
import fi.eonwe.wikilinks.fatpages.WikiPage;
import fi.eonwe.wikilinks.fatpages.WikiPageData;
import fi.eonwe.wikilinks.fatpages.WikiRedirectPage;
import fi.eonwe.wikilinks.leanpages.BufferWikiPage;
import fi.eonwe.wikilinks.leanpages.BufferWikiSerialization;
import net.openhft.koloboke.collect.map.hash.HashObjObjMap;
import net.openhft.koloboke.collect.map.hash.HashObjObjMaps;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

public class WikiLinksTest {

    private static class ByteArrayListChannel implements WritableByteChannel {

        private boolean open = true;
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        @Override
        public int write(ByteBuffer src) {
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

    @Disabled("Rethinking redirects")
    @Test
    public void itResolvesRedirects() {
        Map<String, PagePointer> map = Maps.newHashMap();
        WikiRedirectPage fooDir = new WikiRedirectPage("foo-redir", 0, "foo");
        WikiRedirectPage foofooDir = new WikiRedirectPage("foo-foo-redir", 1, "foo-redir");
        WikiPageData fooPage = new WikiPageData("foo", 2, new PagePointer[0]);
        for (WikiPage page : new WikiPage[] { fooDir, foofooDir, fooPage}) {
            map.put(page.getTitle(), new PagePointer(page));
        }
        WikiProcessor.dropRedirectLoops(convert(map));
        assertThat(map.get(fooDir.getTitle()).page, is(equalTo(fooPage)));
        assertThat(map.get(foofooDir.getTitle()).page, is(equalTo(fooPage)));
    }

    @Test
    @Timeout(1000L)
    public void itResolvesInfiniteRedirects() {
        Map<String, PagePointer> map = Maps.newHashMap();
        WikiRedirectPage fooDir = new WikiRedirectPage("foo-redir", 0, "foo-foo-foo-redir");
        WikiRedirectPage foofooDir = new WikiRedirectPage("foo-foo-redir", 1, "foo-redir");
        WikiRedirectPage foofoofooDir = new WikiRedirectPage("foo-foo-foo-redir", 2, "foo-redir");
        WikiPageData fooPage = new WikiPageData("foo", 3, new PagePointer[0]);
        for (WikiPage page : new WikiPage[] { fooDir, foofooDir, foofoofooDir, fooPage}) {
            map.put(page.getTitle(), new PagePointer(page));
        }
        WikiProcessor.dropRedirectLoops(convert(map));
        final int[] nonNullCount = {0};
        Arrays.asList(fooDir, foofooDir, foofoofooDir).forEach(new Consumer<WikiRedirectPage>() {
            @Override
            public void accept(WikiRedirectPage wikiRedirectPage) {
                if (map.get(wikiRedirectPage.getTitle()).page != null) nonNullCount[0]++;
            }
        });
        assertThat(nonNullCount[0], is(2));
    }

    private static HashObjObjMap<String, PagePointer> convert(Map<String, PagePointer> map) {
        return HashObjObjMaps.newImmutableMap(map);
    }

    @Test
    public void itPacksPagesCorrectly() {
        Map<String, PagePointer> map = createSimpleDenseGraph(4, "title_");
        assertThat(map.size(), is(equalTo(4)));
        List<BufferWikiPage> packedWikiPages = WikiProcessor.packPages(convert(map));
        for (int i = 0; i < map.size(); i++) {
            BufferWikiPage page = packedWikiPages.get(i);
            assertThat(page.getId(), is(equalTo(i)));
            assertThat(page.getTitle(), is(equalTo("title_" + i)));
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
            pointers[i].page = new WikiPageData(title, i, pagePointers.toArray(new PagePointer[0]));
            map.put(title, pointers[i]);
        }
        return map;
    }

    @Test
    public void sortByTitle() throws IOException {
        final String prefix = "foo_title_";
        List<BufferWikiPage> originals = WikiProcessor.packPages(convert(createSimpleDenseGraph(512, prefix)));
        String[] titles = originals.stream().map(BufferWikiPage::getTitle).toArray(String[]::new);
        Arrays.sort(titles);
        Arrays.stream(titles).forEach(t -> assertThat(t, startsWith(prefix)));
        List<BufferWikiPage> deserialized = serializeAndDeserialize(originals);
        Collections.sort(deserialized);
        for (int i = 0; i < titles.length; i++) {
            assertThat(deserialized.get(i).getTitle(), is(equalTo(titles[i])));
        }
    }

    @Test
    public void packingRemovesDuplicates() {
        final String prefix = "foo_title_";
        List<BufferWikiPage> readFromXml = WikiProcessor.packPages(convert(createSimpleDenseGraph(4, prefix, true)));
        readFromXml.forEach(p -> {
            Set<Integer> set = Sets.newHashSet();
            p.forEachLink(set::add);
            assertThat(p.getLinkCount(), is(equalTo(set.size())));
        });
    }

    @Test
    public void deserializeEqualsUnserialized() throws IOException {
        final String prefix = "foo_title_";
        List<BufferWikiPage> originals = WikiProcessor.packPages(convert(createSimpleDenseGraph(512, prefix)));
        List<BufferWikiPage> read = originals;
        BufferWikiSerialization serializer = new BufferWikiSerialization();
        for (int i = 0; i < 5; i++) {
            ByteArrayListChannel channel = new ByteArrayListChannel();
            serializer.serialize(read, channel);
            ByteBuffer input = ByteBuffer.wrap(channel.bos.toByteArray());
            read = serializer.readFromSerialized(input);
        }

        assertThat(read.size(), is(originals.size()));
        for (int i = 0; i < read.size(); i++) {
            BufferWikiPage fromXml = originals.get(i);
            BufferWikiPage deserialized = read.get(i);
            assertThat(deserialized.getTitle(), is(equalTo(fromXml.getTitle())));
            assertThat(deserialized.getTitle(), startsWith(prefix));
            assertThat(deserialized, is(equalTo(fromXml)));
        }
    }

    private static List<BufferWikiPage> serializeAndDeserialize(List<BufferWikiPage> pages) throws IOException {
        BufferWikiSerialization serializer = new BufferWikiSerialization();
        ByteArrayListChannel channel = new ByteArrayListChannel();
        serializer.serialize(pages, channel);
        ByteBuffer input = ByteBuffer.wrap(channel.bos.toByteArray());
        return serializer.readFromSerialized(input);
    }

    @Test
    public void deserializeEqualsUnserializedDisk() throws IOException {
        File tmpFile = File.createTempFile("disk-serialization-test", "tmp");
        tmpFile.deleteOnExit();
        final String prefix = "foo_title_";
        List<BufferWikiPage> readFromXml = WikiProcessor.packPages(convert(createSimpleDenseGraph(512, prefix)));
        BufferWikiSerialization serializer = new BufferWikiSerialization();
        try (FileOutputStream fos = new FileOutputStream(tmpFile);
             FileChannel fc = fos.getChannel()) {
            serializer.serialize(readFromXml, fc);
        }
        try (
            FileInputStream fin = new FileInputStream(tmpFile);
            FileChannel fc = fin.getChannel())
        {
            List<BufferWikiPage> readFromFile = serializer.readFromSerialized(fc);

            assertThat(readFromFile.size(), is(readFromXml.size()));
            for (int i = 0; i < readFromXml.size(); i++) {
                BufferWikiPage fromXml = readFromXml.get(i);
                BufferWikiPage deserialized = readFromFile.get(i);
                assertThat(deserialized.getTitle(), is(equalTo(fromXml.getTitle())));
                assertThat(deserialized.getTitle(), startsWith(prefix));
                assertThat(deserialized, is(equalTo(fromXml)));
            }
        }
    }

}
