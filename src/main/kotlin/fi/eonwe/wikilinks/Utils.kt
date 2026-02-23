package fi.eonwe.wikilinks

import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.min

object Utils {

    fun ByteBuffer.asInputStream(): InputStream {

        val bb = this

        return object : InputStream() {
            override fun read(): Int {
                if (bb.hasRemaining()) {
                    return bb.get().toInt() and 0xFF
                } else {
                    return -1
                }
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!bb.hasRemaining()) return -1
                val toRead = min(len, bb.remaining())
                bb.get(b, off, toRead)
                return toRead
            }
        }

    }
}