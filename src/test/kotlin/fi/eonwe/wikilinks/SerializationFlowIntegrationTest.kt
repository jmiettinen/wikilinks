package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.TestHelper.usingTestDump
import fi.eonwe.wikilinks.leanpages.BufferWikiSerialization
import fi.eonwe.wikilinks.leanpages.KotlinxProtoWikiSerialization
import io.kotest.matchers.collections.shouldContainExactly
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class SerializationFlowIntegrationTest {
    enum class SerializerKind {
        BUFFER_V1,
        PROTOBUF_V1
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
                    SerializerKind.PROTOBUF_V1 -> KotlinxProtoWikiSerialization().serialize(pages, fos.channel)
                }
            }

            val deserialized = FileInputStream(tempSerialized.toFile()).use { fin ->
                when (serializerKind) {
                    SerializerKind.BUFFER_V1 -> BufferWikiSerialization().readFromSerialized(fin.channel)
                    SerializerKind.PROTOBUF_V1 -> KotlinxProtoWikiSerialization().readFromSerialized(fin.channel)
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
}
