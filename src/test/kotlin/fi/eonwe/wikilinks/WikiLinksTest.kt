package fi.eonwe.wikilinks

import com.google.common.collect.Lists
import com.google.common.collect.Maps
import fi.eonwe.wikilinks.fatpages.PagePointer
import fi.eonwe.wikilinks.fatpages.WikiPageData
import fi.eonwe.wikilinks.fatpages.WikiRedirectPage
import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import fi.eonwe.wikilinks.leanpages.BufferWikiSerialization
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import net.openhft.koloboke.collect.map.hash.HashObjObjMap
import net.openhft.koloboke.collect.map.hash.HashObjObjMaps
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import java.util.Arrays
import java.util.Collections
import java.util.function.Consumer
import java.util.function.IntConsumer
import kotlin.math.min

class WikiLinksTest {
    private class ByteArrayListChannel : WritableByteChannel {
        private var open = true
        val bos = ByteArrayOutputStream()

        override fun write(src: ByteBuffer): Int {
            val buffer = src.duplicate()
            val tmp = ByteArray(4096)
            var written = 0
            var readLen = 0
            do {
                val left = buffer.limit() - buffer.position()
                readLen = min(left, tmp.size)
                buffer.get(tmp, 0, readLen)
                bos.write(tmp, 0, readLen)
                written += readLen
            } while (readLen > 0)
            return written
        }

        override fun isOpen(): Boolean {
            return open
        }

        override fun close() {
            open = false
        }
    }

    @Disabled("Rethinking redirects")
    @Test
    fun itResolvesRedirects() {
        val map: MutableMap<String?, PagePointer?> = Maps.newHashMap()
        val fooDir = WikiRedirectPage("foo-redir", 0, "foo")
        val foofooDir = WikiRedirectPage("foo-foo-redir", 1, "foo-redir")
        val fooPage = WikiPageData("foo", 2, arrayOfNulls<PagePointer>(0))
        for (page in arrayOf(fooDir, foofooDir, fooPage)) {
            map.put(page.getTitle(), PagePointer(page))
        }
        WikiProcessor.dropRedirectLoops(convert(map))

        listOf(fooDir, foofooDir).map {
            map[it.title]?.page!!
        } shouldBeEqual listOf(fooPage, fooPage)
    }

    @Test
    @Timeout(1000L)
    fun itResolvesInfiniteRedirects() {
        val map: MutableMap<String?, PagePointer?> = Maps.newHashMap()
        val fooDir = WikiRedirectPage("foo-redir", 0, "foo-foo-foo-redir")
        val foofooDir = WikiRedirectPage("foo-foo-redir", 1, "foo-redir")
        val foofoofooDir = WikiRedirectPage("foo-foo-foo-redir", 2, "foo-redir")
        val fooPage = WikiPageData("foo", 3, arrayOfNulls<PagePointer>(0))
        for (page in arrayOf(fooDir, foofooDir, foofoofooDir, fooPage)) {
            map.put(page.getTitle(), PagePointer(page))
        }
        WikiProcessor.dropRedirectLoops(convert(map))
        val tmp = listOf(fooDir, foofooDir, foofoofooDir).fold(0) { acc, p ->
            if (map.get(p.title)?.page != null) {
                acc + 1
            } else {
                acc
            }
        }
        tmp shouldBe 2
    }

    @Test
    fun itPacksPagesCorrectly() {
        val map = createSimpleDenseGraph(4, "title_")
        map.size shouldBe 4
        val packedWikiPages = WikiProcessor.packPages(convert(map))
        for (i in 0..<map.size) {
            val page = packedWikiPages.get(i)
            page.id shouldBeEqual i
            page.title shouldBeEqual "title_$i"
        }
    }

    @Test
    @Throws(IOException::class)
    fun sortByTitle() {
        val prefix = "foo_title_"
        val originals = WikiProcessor.packPages(convert(createSimpleDenseGraph(512, prefix)))
        val titles = originals.map { it.title }.toTypedArray()
        Arrays.sort(titles)
        titles.forEach { t ->
            t shouldStartWith prefix
        }
        val deserialized = serializeAndDeserialize(originals)
        Collections.sort<BufferWikiPage?>(deserialized)
        for (i in titles.indices) {
            deserialized[i]!!.title shouldBeEqual titles[i]
        }
    }

    @Test
    fun packingRemovesDuplicates() {
        val prefix = "foo_title_"
        val readFromXml = WikiProcessor.packPages(convert(createSimpleDenseGraph(4, prefix, true)))
        readFromXml.forEach(Consumer { p ->
            val set = mutableSetOf<Int>()
            p.forEachLink(IntConsumer { e: Int -> set.add(e) })
            p.linkCount shouldBeEqual set.size
        })
    }

    @Test
    @Throws(IOException::class)
    fun deserializeEqualsUnserialized() {
        val prefix = "foo_title_"
        val originals = WikiProcessor.packPages(convert(createSimpleDenseGraph(512, prefix)))
        var read = originals
        val serializer = BufferWikiSerialization()
        for (i in 0..4) {
            val channel = ByteArrayListChannel()
            serializer.serialize(read, channel)
            val input = ByteBuffer.wrap(channel.bos.toByteArray())
            read = serializer.readFromSerialized(input)
        }

        read.size shouldBe originals.size
        for (i in read.indices) {
            val fromXml = originals.get(i)
            val deserialized = read.get(i)

            deserialized.title shouldStartWith prefix
            deserialized.title shouldBe fromXml.title
            deserialized shouldBeEqual fromXml
        }
    }

    @Test
    @Throws(IOException::class)
    fun deserializeEqualsUnserializedDisk() {
        val tmpFile = File.createTempFile("disk-serialization-test", "tmp")
        tmpFile.deleteOnExit()
        val prefix = "foo_title_"
        val readFromXml = WikiProcessor.packPages(convert(createSimpleDenseGraph(512, prefix)))
        val serializer = BufferWikiSerialization()
        FileOutputStream(tmpFile).use { fos ->
            fos.getChannel().use { fc ->
                serializer.serialize(readFromXml, fc)
            }
        }
        FileInputStream(tmpFile).use { fin ->
            fin.getChannel().use { fc ->
                val readFromFile = serializer.readFromSerialized(fc)
                readFromFile.size shouldBe readFromXml.size
                for (i in readFromXml.indices) {
                    val fromXml = readFromXml.get(i)
                    val deserialized = readFromFile.get(i)

                    deserialized.title shouldStartWith prefix
                    deserialized.title shouldBe fromXml.title
                    deserialized shouldBeEqual fromXml
                }
            }
        }
    }

    companion object {
        private fun convert(map: MutableMap<String?, PagePointer?>): HashObjObjMap<String?, PagePointer?> {
            return HashObjObjMaps.newImmutableMap<String?, PagePointer?>(map)
        }

        private fun createSimpleDenseGraph(
            size: Int,
            titlePrefix: String,
            duplicates: Boolean = false
        ): HashObjObjMap<String?, PagePointer?> {
            val pointers = arrayOfNulls<PagePointer>(size)
            for (i in pointers.indices) {
                pointers[i] = PagePointer(null)
            }
            val map = HashObjObjMaps.newMutableMap<String?, PagePointer?>()
            for (i in pointers.indices) {
                val pagePointers: MutableList<PagePointer?> = Lists.newArrayList()
                for (repeat in 0..<(if (duplicates) 2 else 1)) {
                    for (j in pointers.indices.reversed()) {
                        if (i == j) continue
                        pagePointers.add(pointers[j])
                    }
                }
                val title = titlePrefix + i
                pointers[i]!!.page = WikiPageData(title, i, pagePointers.toTypedArray<PagePointer?>())
                map.put(title, pointers[i])
            }
            return map
        }

        @Throws(IOException::class)
        private fun serializeAndDeserialize(pages: MutableList<BufferWikiPage>): MutableList<BufferWikiPage?> {
            val serializer = BufferWikiSerialization()
            val channel = ByteArrayListChannel()
            serializer.serialize(pages, channel)
            val input = ByteBuffer.wrap(channel.bos.toByteArray())
            return serializer.readFromSerialized(input)
        }
    }
}