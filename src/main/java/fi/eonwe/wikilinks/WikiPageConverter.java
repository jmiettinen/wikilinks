package fi.eonwe.wikilinks;

import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

/**
 */
public class WikiPageConverter {


    public ConvertedWikiPages convertPacked(List<PackedWikiPage> list) {
        Collections.sort(list, PackedWikiPage::compareTitle);
        final int[] titleTotalLength = {0};
        final int[] linkTotalCount = {0};
        list.forEach(p -> {
            linkTotalCount[0] += p.getLinkCount();
            titleTotalLength[0] += p.getTitleLength();
        });
        ByteBuffer linksBuffer = ByteBuffer.allocate(Integer.BYTES * linkTotalCount[0]);
        ByteBuffer titlesBuffer = ByteBuffer.allocate(Byte.BYTES * titleTotalLength[0]);
        ByteBuffer pageObjectBuffer = ByteBuffer.allocate(ConvertedWikiPages.getPageSize() * list.size());
        list.forEach(p -> {
            final int id = Ints.checkedCast(p.getId());
            final int linkOffset = linksBuffer.position();
            final int titlesOffset = titlesBuffer.position();
            final short[] linkCount = {0};
            p.forEachLink(l -> {
                linkCount[0]++;
                linksBuffer.putInt((int) l);
            });
            byte[] bytes = p.getTitle().getBytes(Charsets.UTF_8);
            final short titleLen = Shorts.checkedCast(bytes.length);
            titlesBuffer.put(bytes);
            writePage(pageObjectBuffer, id, linkOffset, linkCount[0], titlesOffset, titleLen);
        });
        return new ConvertedWikiPages(pageObjectBuffer, linksBuffer, titlesBuffer, list.size());
    }

    public static void writePage(ByteBuffer buffer, int id, int linkOffset, short linkCount, int titleOffset, short titleLen) {
        buffer.putInt(id);
        buffer.putInt(linkOffset);
        buffer.putShort(linkCount);
        buffer.putInt(titleOffset);
        buffer.putShort(titleLen);
    }

}
