package fi.eonwe.wikilinks.leanpages

import com.google.flatbuffers.FlatBufferBuilder
import com.google.flatbuffers.Table
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.WritableByteChannel

class FlatBufferWikiSerialization {

    fun serialize(graph: Collection<BufferWikiPage>, channel: WritableByteChannel) {
        val builder = FlatBufferBuilder(initialSize(graph.size))
        val pageOffsets = IntArray(graph.size)

        var i = 0
        for (page in graph) {
            val links = mutableListOf<Int>()
            page.forEachLink { links.add(it) }
            val linksOffset = FbPage.createLinksVector(builder, links.toIntArray())
            val titleOffset = builder.createString(page.title)
            pageOffsets[i++] = FbPage.createFbPage(
                builder = builder,
                id = page.id,
                isRedirect = page.isRedirect,
                titleOffset = titleOffset,
                linksOffset = linksOffset
            )
        }
        val pagesOffset = FbGraph.createPagesVector(builder, pageOffsets)
        val graphOffset = FbGraph.createFbGraph(builder, pagesOffset)
        builder.finish(graphOffset)
        writeAll(channel, ByteBuffer.wrap(builder.sizedByteArray()))
    }

    fun readFromSerialized(channel: FileChannel): MutableList<BufferWikiPage> {
        channel.position(0)
        val size = channel.size()
        require(size <= Int.MAX_VALUE) {
            "Serialized payload too large for current implementation: $size bytes"
        }
        val data = ByteBuffer.allocate(size.toInt())
        while (data.hasRemaining()) {
            val read = channel.read(data)
            if (read < 0) break
        }
        data.flip()
        return readFromSerialized(data)
    }

    fun readFromSerialized(buffer: ByteBuffer): MutableList<BufferWikiPage> {
        val graph = FbGraph.getRootAsFbGraph(buffer)
        val pages = mutableListOf<BufferWikiPage>()
        for (i in 0 until graph.pagesLength()) {
            val page = graph.pages(i) ?: continue
            val links = IntArray(page.linksLength()) { j -> page.links(j) }
            pages.add(
                BufferWikiPage.createFrom(
                    page.id(),
                    links,
                    page.title() ?: "",
                    page.isRedirect()
                )
            )
        }
        return pages
    }

    private fun writeAll(channel: WritableByteChannel, dataBuffer: ByteBuffer) {
        val copy = dataBuffer.asReadOnlyBuffer()
        while (copy.hasRemaining()) {
            val written = channel.write(copy)
            if (written <= 0) {
                throw IOException("Failed to write FlatBuffer payload")
            }
        }
    }

    private fun initialSize(pageCount: Int): Int {
        return maxOf(1024, pageCount * 128)
    }
}

private class FbPage : Table() {
    fun __init(i: Int, bb: ByteBuffer): FbPage {
        __reset(i, bb)
        return this
    }

    fun id(): Int {
        val o = __offset(4)
        return if (o != 0) bb.getInt(o + bb_pos) else 0
    }

    fun isRedirect(): Boolean {
        val o = __offset(6)
        return o != 0 && bb.get(o + bb_pos).toInt() != 0
    }

    fun title(): String? {
        val o = __offset(8)
        return if (o != 0) __string(o + bb_pos) else null
    }

    fun links(j: Int): Int {
        val o = __offset(10)
        return if (o != 0) bb.getInt(__vector(o) + j * 4) else 0
    }

    fun linksLength(): Int {
        val o = __offset(10)
        return if (o != 0) __vector_len(o) else 0
    }

    companion object {
        fun createFbPage(
            builder: FlatBufferBuilder,
            id: Int,
            isRedirect: Boolean,
            titleOffset: Int,
            linksOffset: Int
        ): Int {
            builder.startTable(4)
            builder.addInt(0, id, 0)
            builder.addBoolean(1, isRedirect, false)
            builder.addOffset(2, titleOffset, 0)
            builder.addOffset(3, linksOffset, 0)
            return builder.endTable()
        }

        fun createLinksVector(builder: FlatBufferBuilder, data: IntArray): Int {
            builder.startVector(4, data.size, 4)
            for (i in data.indices.reversed()) {
                builder.addInt(data[i])
            }
            return builder.endVector()
        }
    }
}

private class FbGraph : Table() {
    fun __init(i: Int, bb: ByteBuffer): FbGraph {
        __reset(i, bb)
        return this
    }

    fun pages(j: Int): FbPage? {
        val o = __offset(4)
        return if (o != 0) {
            FbPage().__init(__indirect(__vector(o) + j * 4), bb)
        } else {
            null
        }
    }

    fun pagesLength(): Int {
        val o = __offset(4)
        return if (o != 0) __vector_len(o) else 0
    }

    companion object {
        fun getRootAsFbGraph(bb: ByteBuffer): FbGraph {
            val copy = bb.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
            return FbGraph().__init(copy.getInt(copy.position()) + copy.position(), copy)
        }

        fun createFbGraph(builder: FlatBufferBuilder, pagesOffset: Int): Int {
            builder.startTable(1)
            builder.addOffset(0, pagesOffset, 0)
            return builder.endTable()
        }

        fun createPagesVector(builder: FlatBufferBuilder, data: IntArray): Int {
            builder.startVector(4, data.size, 4)
            for (i in data.indices.reversed()) {
                builder.addOffset(data[i])
            }
            return builder.endVector()
        }
    }
}
