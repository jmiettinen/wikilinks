package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.TestHelper.usingTestDump
import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import fi.eonwe.wikilinks.leanpages.BufferWikiSerialization
import fi.eonwe.wikilinks.leanpages.FlatBufferWikiSerialization
import io.kotest.matchers.collections.shouldContainExactly
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class SerializationFlowIntegrationTest {
    enum class SerializerKind {
        BUFFER_V1,
        FLATBUFFERS_V1
    }

    @ParameterizedTest
    @EnumSource(SerializerKind::class)
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `ingest serialize deserialize and route query works`(serializerKind: SerializerKind) {
        val tempSerialized = Files.createTempFile("wikilinks-flow-", ".bin")
        try {
            val pages = usingTestDump(TestData.Sileasin) { input ->
                BZip2CompressorInputStream(input, true).use { wikiInput ->
                    WikiProcessor.readPages(wikiInput)
                }
            }

            FileOutputStream(tempSerialized.toFile()).use { fos ->
                when (serializerKind) {
                    SerializerKind.BUFFER_V1 -> BufferWikiSerialization().serialize(pages, fos.channel)
                    SerializerKind.FLATBUFFERS_V1 -> FlatBufferWikiSerialization().serialize(pages, fos.channel)
                }
            }

            val deserialized = FileInputStream(tempSerialized.toFile()).use { fin ->
                when (serializerKind) {
                    SerializerKind.BUFFER_V1 -> BufferWikiSerialization().readFromSerialized(fin.channel)
                    SerializerKind.FLATBUFFERS_V1 -> FlatBufferWikiSerialization().readFromSerialized(fin.channel)
                }
            }

            val routes = WikiRoutes(deserialized)
            routes.findRoute("Gdańsk", "Polska").getRoute().map { it.title } shouldContainExactly
                listOf("Gdańsk", "Polska")
            routes.findRoute("Polska", "Bałtycke Morze").getRoute().map { it.title } shouldContainExactly
                listOf("Polska", "Polsko", "Bałtycke Morze")
        } finally {
            Files.deleteIfExists(tempSerialized)
        }
    }

    @ParameterizedTest
    @EnumSource(SerializerKind::class)
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `flatbuffers and buffer formats deserialize to equivalent routes`(serializerKind: SerializerKind) {
        val pages = usingTestDump(TestData.Sileasin) { input ->
            BZip2CompressorInputStream(input, true).use { wikiInput ->
                WikiProcessor.readPages(wikiInput)
            }
        }

        val serializer = when (serializerKind) {
            SerializerKind.BUFFER_V1 -> SerializerAdapter.Buffer
            SerializerKind.FLATBUFFERS_V1 -> SerializerAdapter.Flat
        }
        val baseline = SerializerAdapter.Buffer

        val startWrite = System.nanoTime()
        val encoded = serializer.serializeToBytes(pages)
        val writeMillis = (System.nanoTime() - startWrite) / 1_000_000

        val startRead = System.nanoTime()
        val decoded = serializer.readFromBytes(encoded)
        val readMillis = (System.nanoTime() - startRead) / 1_000_000

        val baselineBytes = baseline.serializeToBytes(pages)
        println(
            "serializer=$serializerKind bytes=${encoded.size} " +
                "vs buffer=${baselineBytes.size} writeMs=$writeMillis readMs=$readMillis"
        )

        val actualRoutes = WikiRoutes(decoded)
        val expectedRoutes = WikiRoutes(baseline.readFromBytes(baselineBytes))
        actualRoutes.findRoute("Gdańsk", "Polska").getRoute().map { it.title } shouldContainExactly
            expectedRoutes.findRoute("Gdańsk", "Polska").getRoute().map { it.title }
        actualRoutes.findRoute("Polska", "Bałtycke Morze").getRoute().map { it.title } shouldContainExactly
            expectedRoutes.findRoute("Polska", "Bałtycke Morze").getRoute().map { it.title }
    }

    private sealed interface SerializerAdapter {
        fun serializeToBytes(pages: Collection<BufferWikiPage>): ByteArray
        fun readFromBytes(payload: ByteArray): MutableList<BufferWikiPage>

        data object Buffer : SerializerAdapter {
            private val serializer = BufferWikiSerialization()
            override fun serializeToBytes(pages: Collection<BufferWikiPage>): ByteArray {
                val channel = ByteArrayOutputStream().let { out ->
                    object : WritableByteChannel {
                        private var open = true
                        override fun write(src: ByteBuffer): Int {
                            val copy = src.duplicate()
                            val data = ByteArray(copy.remaining())
                            copy.get(data)
                            out.write(data)
                            src.position(src.limit())
                            return data.size
                        }

                        override fun isOpen(): Boolean = open
                        override fun close() {
                            open = false
                        }
                    } to out
                }
                serializer.serialize(pages, channel.first)
                return channel.second.toByteArray()
            }

            override fun readFromBytes(payload: ByteArray): MutableList<BufferWikiPage> {
                return serializer.readFromSerialized(ByteBuffer.wrap(payload))
            }
        }

        data object Flat : SerializerAdapter {
            private val serializer = FlatBufferWikiSerialization()
            override fun serializeToBytes(pages: Collection<BufferWikiPage>): ByteArray {
                val channel = ByteArrayOutputStream().let { out ->
                    object : WritableByteChannel {
                        private var open = true
                        override fun write(src: ByteBuffer): Int {
                            val copy = src.duplicate()
                            val data = ByteArray(copy.remaining())
                            copy.get(data)
                            out.write(data)
                            src.position(src.limit())
                            return data.size
                        }

                        override fun isOpen(): Boolean = open
                        override fun close() {
                            open = false
                        }
                    } to out
                }
                serializer.serialize(pages, channel.first)
                return channel.second.toByteArray()
            }

            override fun readFromBytes(payload: ByteArray): MutableList<BufferWikiPage> {
                return serializer.readFromSerialized(ByteBuffer.wrap(payload))
            }
        }
    }
}
