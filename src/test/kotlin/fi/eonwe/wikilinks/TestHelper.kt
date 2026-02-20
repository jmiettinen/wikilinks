package fi.eonwe.wikilinks

import java.io.InputStream
import java.nio.ByteBuffer

object TestHelper {

    inline fun <T> usingTestDump(testData: TestData = TestData.Sileasin, consumer: (InputStream) -> T): T {
        return javaClass.getResourceAsStream(testData.name).use {
            consumer(it!!)
        }
    }

    inline fun <T> usingTestDumpBB(testData: TestData = TestData.Sileasin, consumer: (ByteBuffer) -> T): T {
        val bb = usingTestDump(testData = testData){ it.readAllBytes() }.let { ByteBuffer.wrap(it) }
        return consumer(bb)
    }

}

private const val SILESIAN = "/szlwiki-20190801-pages-articles-multistream.xml.bz2"
private const val FAROESE = "/fowiki-20260201-pages-articles-multistream.xml.bz2"

private const val NEW_SILESIAN = "/szlwiki-20260201-pages-articles-multistream.xml.bz2"
private const val NEW_SILESIAN_INDEX = "/szlwiki-20260201-pages-articles-multistream-index.txt.bz2"

sealed interface TestData {
    val name: String

    data object Sileasin: TestData {
        override val name: String
            get() = SILESIAN
    }

    data object Faroese: TestData {
        override val name: String
            get() = FAROESE

    }

    data object NewSilesian: TestData {
        override val name: String
            get() = NEW_SILESIAN

    }

    data object NewSilesianIndex: TestData {
        override val name: String
            get() = NEW_SILESIAN_INDEX

    }

}