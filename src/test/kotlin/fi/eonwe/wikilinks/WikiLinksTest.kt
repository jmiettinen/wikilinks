package fi.eonwe.wikilinks

import com.google.common.collect.Lists
import fi.eonwe.wikilinks.fatpages.PagePointer
import fi.eonwe.wikilinks.fatpages.WikiPageData
import fi.eonwe.wikilinks.fatpages.WikiRedirectPage
import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import fi.eonwe.wikilinks.leanpages.BufferWikiSerialization
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
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
import java.util.HashMap
import java.util.function.Consumer
import kotlin.math.min

class WikiLinksTest {
    private class ByteArrayListChannel : WritableByteChannel {
        private var open = true
        val bos = ByteArrayOutputStream()

        override fun write(src: ByteBuffer): Int {
            val buffer = src.duplicate()
            val tmp = ByteArray(4096)
            var written = 0
            var readLen: Int
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
        val fooDir = WikiRedirectPage("foo-redir", 0, "foo")
        val foofooDir = WikiRedirectPage("foo-foo-redir", 1, "foo-redir")
        val fooPage = WikiPageData("foo", 2, emptyList())
        val map = buildMap {
            for (page in listOf(fooDir, foofooDir, fooPage)) {
                this.put(page.title(), PagePointer(page))
            }
        }
        WikiProcessor.dropRedirectLoops(convert(map))

        listOf(fooDir, foofooDir).map {
            map[it.title]?.page!!
        } shouldBeEqual listOf(fooPage, fooPage)
    }

    @Test
    @Timeout(1000L)
    fun itResolvesInfiniteRedirects() {
        val fooDir = WikiRedirectPage("foo-redir", 0, "foo-foo-foo-redir")
        val foofooDir = WikiRedirectPage("foo-foo-redir", 1, "foo-redir")
        val foofoofooDir = WikiRedirectPage("foo-foo-foo-redir", 2, "foo-redir")
        val fooPage = WikiPageData("foo", 3, emptyList())
        val map = buildMap {
            listOf(fooDir, foofooDir, foofoofooDir, fooPage).forEach { page ->
                put(page.title(), PagePointer(page))
            }
        }
        WikiProcessor.dropRedirectLoops(convert(map))
        val tmp = listOf(fooDir, foofooDir, foofoofooDir).fold(0) { acc, p ->
            if (map[p.title]?.page != null) {
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
            val page = packedWikiPages[i]
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
        Collections.sort(deserialized)
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
            p.forEachLink { e: Int -> set.add(e) }
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
            val fromXml = originals[i]
            val deserialized = read[i]

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
                    val fromXml = readFromXml[i]
                    val deserialized = readFromFile[i]

                    deserialized.title shouldStartWith prefix
                    deserialized.title shouldBe fromXml.title
                    deserialized shouldBeEqual fromXml
                }
            }
        }
    }

    companion object {
        private fun convert(map: Map<String, PagePointer>): MutableMap<String, PagePointer> {
            return HashMap(map)
        }

        private fun createSimpleDenseGraph(
            size: Int,
            titlePrefix: String,
            duplicates: Boolean = false
        ): MutableMap<String, PagePointer> {
            val pointers = MutableList(size) { PagePointer(null) }
            val map = HashMap<String, PagePointer>()
            for (i in pointers.indices) {
                val pagePointers: MutableList<PagePointer> = Lists.newArrayList()
                for (repeat in 0..<(if (duplicates) 2 else 1))
                    for (j in pointers.indices.reversed()) {
                        if (i == j) continue
                        pagePointers.add(pointers[j])
                    }
                val title = titlePrefix + i
                pointers[i].page = WikiPageData(title, i, pagePointers)
                map[title] = pointers[i]
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
