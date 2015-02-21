package fi.eonwe.wikilinks;

import com.carrotsearch.hppc.ByteArrayList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.junit.Test;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.List;
import java.util.Map;

import static java.util.function.Predicate.isEqual;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;

public class WikiLinksTest {

    @Test
    public void marshalledAndUnmarshalledEqualsParsed() throws IOException {
        String filePath = "src/data/simplewiki-20141222-pages-meta-current.xml";
        InputStream in = new FileInputStream(filePath);
        BufferedInputStream bis = new BufferedInputStream(in, 1 << 15);
        List<PackedWikiPage> readFromXml = WikiProcessor.readPages(bis);
        ByteArrayListChannel channel = new ByteArrayListChannel();
        WikiProcessor.serialize(readFromXml, channel);
        ByteBuffer input = ByteBuffer.wrap(channel.byteArrayList.buffer, 0, channel.byteArrayList.elementsCount);
        List<PackedWikiPage> readFromSerialized = WikiProcessor.deserialize(input);

        assertThat(readFromSerialized.size(), is(readFromXml.size()));
        for (int i = 0; i < readFromSerialized.size(); i++) {
            PackedWikiPage fromXml = readFromXml.get(i);
            PackedWikiPage deserialized = readFromSerialized.get(i);
            assertThat(deserialized, is(equalTo(fromXml)));
        }
    }

    private static class ByteArrayListChannel implements WritableByteChannel {

        private boolean open = true;
        private final ByteArrayList byteArrayList = new ByteArrayList();

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
                byteArrayList.add(tmp, 0, readLen);
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
        WikiPageData fooPage = new WikiPageData("foo", 2, Lists.newArrayList());
        for (WikiPage page : new WikiPage[] { fooDir, foofooDir, fooPage}) {
            map.put(page.getTitle(), new PagePointer(page));
        }
        WikiProcessor.resolveRedirects(map);
        assertThat(map.get(fooDir.getTitle()).page, is(equalTo(fooPage)));
        assertThat(map.get(foofooDir.getTitle()).page, is(equalTo(fooPage)));
    }

    @Test
    public void itPacksPagesCorrectly() throws Exception {
        PagePointer[] pointers = new PagePointer[4];
        for (int i = 0; i < pointers.length; i++) {
            pointers[i] = new PagePointer(null);
        }
        Map<String, PagePointer> map = Maps.newHashMap();
        for (int i = 0; i < pointers.length; i++) {
            List<PagePointer> pagePointers = Lists.newArrayList();
            for (int j = pointers.length - 1; j >= 0; j--) {
                if (i == j) continue;
                pagePointers.add(pointers[j]);
            }
            String title = "title_" + i;
            pointers[i].page = new WikiPageData(title, i, pagePointers);
            map.put(title, pointers[i]);
        }
        assertThat(map.size(), is(equalTo(4)));
        List<PackedWikiPage> packedWikiPages = WikiProcessor.packPages(map);
        for (int i = 0; i < pointers.length; i++) {
            PackedWikiPage page = packedWikiPages.get(i);
            assertThat(page.getId(), is(equalTo(Long.valueOf(i))));
            assertThat(page.getTitle(), is(equalTo("title_" + i)));
            assertThat(page.getLength(), is(equalTo(getLength(page))));
        }
    }

    private int getLength(PackedWikiPage page) throws NoSuchFieldException, IllegalAccessException {
        Field f = page.getClass().getDeclaredField("data");
        f.setAccessible(true);
        return ((ByteBuffer) f.get(page)).capacity();
    }

}
