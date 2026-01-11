package fi.eonwe.wikilinks

import java.io.InputStream
import java.nio.ByteBuffer

object TestHelper {

    inline fun <T> usingTestDump(consumer: (InputStream) -> T): T {
        return javaClass.getResourceAsStream("/szlwiki-20190801-pages-articles-multistream.xml.bz2").use {
            consumer(it!!)
        }
    }

    inline fun <T> usingTestDumpBB(consumer: (ByteBuffer) -> T): T {
        val bb = usingTestDump { it.readAllBytes() }.let { ByteBuffer.wrap(it) }
        return consumer(bb)
    }

}