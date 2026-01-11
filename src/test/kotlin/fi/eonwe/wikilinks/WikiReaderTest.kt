package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.TestHelper.usingTestDumpBB
import fi.eonwe.wikilinks.Utils.asInputStream
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class WikiReaderTest {

    @Test
    fun `all parts decompress properly bb`() {
        val partCount = usingTestDumpBB { bb ->
            var readCount = 0
            WikiReader.generateSubstreams(bb).forEach { bzip2bb ->
                BZip2CompressorInputStream(bzip2bb.asInputStream(), false).use { bzip2 ->
                    val bytes = bzip2.readAllBytes()
                    bytes.size shouldBeGreaterThan 0
                    readCount++
                }
            }
            readCount

        }
        partCount shouldBe 166
    }


    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `parallel stuff does not block forever`() {
        usingTestDumpBB { bb ->
            WikiReader.processWiki(bb, ProcessingConfig(
                parallelism = 10U,
                maxBlocksWaiting = 4U
            ))
        }
    }

}