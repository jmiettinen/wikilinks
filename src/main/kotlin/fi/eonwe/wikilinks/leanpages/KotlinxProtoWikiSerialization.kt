package fi.eonwe.wikilinks.leanpages

import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.WritableByteChannel

@OptIn(ExperimentalSerializationApi::class)
class KotlinxProtoWikiSerialization {

    fun serialize(graph: Collection<BufferWikiPage>, channel: WritableByteChannel) {
        val pages = graph.map { page ->
            val links = mutableListOf<Int>()
            page.forEachLink { links.add(it) }
            SerializedPage(
                id = page.id,
                title = page.title,
                isRedirect = page.isRedirect,
                links = links
            )
        }
        val payload = ProtoBuf.encodeToByteArray(
            SerializedGraphV1.serializer(),
            SerializedGraphV1(pages)
        )
        writeAll(channel, ByteBuffer.wrap(payload))
    }

    fun readFromSerialized(channel: FileChannel): MutableList<BufferWikiPage> {
        val payload = readAll(channel)
        val graph = ProtoBuf.decodeFromByteArray(
            SerializedGraphV1.serializer(),
            payload
        )
        return graph.pages.mapTo(mutableListOf()) { page ->
            BufferWikiPage.createFrom(
                page.id,
                page.links.toIntArray(),
                page.title,
                page.isRedirect
            )
        }
    }

    fun readFromSerialized(buffer: ByteBuffer): MutableList<BufferWikiPage> {
        val copy = buffer.asReadOnlyBuffer()
        val payload = ByteArray(copy.remaining())
        copy.get(payload)
        val graph = ProtoBuf.decodeFromByteArray(
            SerializedGraphV1.serializer(),
            payload
        )
        return graph.pages.mapTo(mutableListOf()) { page ->
            BufferWikiPage.createFrom(
                page.id,
                page.links.toIntArray(),
                page.title,
                page.isRedirect
            )
        }
    }

    private fun readAll(channel: FileChannel): ByteArray {
        channel.position(0)
        val size = channel.size()
        require(size <= Int.MAX_VALUE) {
            "Serialized payload too large for current implementation: $size bytes"
        }
        val data = ByteArray(size.toInt())
        val bb = ByteBuffer.wrap(data)
        while (bb.hasRemaining()) {
            val read = channel.read(bb)
            if (read < 0) break
        }
        return data
    }

    private fun writeAll(channel: WritableByteChannel, buffer: ByteBuffer) {
        while (buffer.hasRemaining()) {
            val written = channel.write(buffer)
            if (written <= 0) {
                throw IOException("Failed to write serialized payload")
            }
        }
    }

    @Serializable
    private data class SerializedGraphV1(
        val pages: List<SerializedPage>
    )

    @Serializable
    private data class SerializedPage(
        val id: Int,
        val title: String,
        val isRedirect: Boolean,
        val links: List<Int>
    )
}
