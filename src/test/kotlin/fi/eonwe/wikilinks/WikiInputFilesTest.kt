package fi.eonwe.wikilinks

import io.kotest.matchers.collections.shouldContainExactly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files

class WikiInputFilesTest {
    @Test
    fun `single file resolves to itself`() {
        val file = Files.createTempFile("wikinput-single-", ".xml.bz2")
        try {
            WikiInputFiles.resolveXmlInputs(file.toFile()) shouldContainExactly listOf(file.toFile())
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `directory resolves xml bzip parts in numeric content range order`() {
        val dir = Files.createTempDirectory("wikiinput-parts-")
        val p10 = dir.resolve("enwiki-2026-06-01-p10p1130124.xml.bz2")
        val p1130125 = dir.resolve("enwiki-2026-06-01-p1130125p3931711.xml.bz2")
        val p3931712 = dir.resolve("enwiki-2026-06-01-p3931712p8100363.xml.bz2")
        try {
            Files.writeString(dir.resolve("SHA256SUMS"), "checksum data")
            Files.writeString(dir.resolve("_SUCCESS"), "")
            Files.writeString(p3931712, "")
            Files.writeString(p10, "")
            Files.writeString(p1130125, "")

            WikiInputFiles.resolveXmlInputs(dir.toFile()) shouldContainExactly listOf(
                p10.toFile(),
                p1130125.toFile(),
                p3931712.toFile()
            )
        } finally {
            Files.deleteIfExists(dir.resolve("_SUCCESS"))
            Files.deleteIfExists(dir.resolve("SHA256SUMS"))
            Files.deleteIfExists(p3931712)
            Files.deleteIfExists(p1130125)
            Files.deleteIfExists(p10)
            Files.deleteIfExists(dir)
        }
    }

    @Test
    fun `directory without xml bzip parts is rejected`() {
        val dir = Files.createTempDirectory("wikiinput-empty-")
        try {
            assertThrows<IllegalArgumentException> {
                WikiInputFiles.resolveXmlInputs(dir.toFile())
            }
        } finally {
            Files.deleteIfExists(dir)
        }
    }
}
