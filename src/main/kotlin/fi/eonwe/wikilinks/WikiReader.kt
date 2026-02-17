package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.ranges.OpenEndRange

object WikiReader {

    fun processWiki(data: MappedByteBuffer, config: ProcessingConfig) {
        readPages(data, config)
    }

    fun readPages(data: MappedByteBuffer, config: ProcessingConfig = defaultProcessingConfig): MutableList<BufferWikiPage> {
        return readPages(ByteBufferCompressedSource(data), config)
    }

    fun readPages(source: CompressedSource, config: ProcessingConfig = defaultProcessingConfig): MutableList<BufferWikiPage> {
        val substreams = generateSubstreams(source).toList()
        if (substreams.isEmpty()) {
            return byteArrayOf().inputStream().use { WikiProcessor.readPages(it) }
        }
        ParallelBzip2InputStream(source, substreams, config).use {
            return WikiProcessor.readPages(it)
        }
    }

    fun generateSubstreams(source: CompressedSource): Sequence<OpenEndRange<Long>> = sequence {
        val magic0 = 0x42 // B
        val magic1 = 0x5A // Z
        val magic2 = 0x68 // h
        var currentStart = -1L
        var pos = 0L

        source.openSequential().use { input ->
            val rolling = IntArray(4) { -1 }
            while (true) {
                val b = input.read()
                if (b < 0) break

                rolling[0] = rolling[1]
                rolling[1] = rolling[2]
                rolling[2] = rolling[3]
                rolling[3] = b
                pos++

                if (
                    rolling[0] == magic0 &&
                    rolling[1] == magic1 &&
                    rolling[2] == magic2 &&
                    rolling[3] in '1'.code..'9'.code
                ) {
                    val streamStart = pos - 4
                    if (currentStart >= 0) {
                        yield(currentStart until streamStart)
                    }
                    currentStart = streamStart
                }
            }
        }

        if (currentStart >= 0 && currentStart < source.size) {
            yield(currentStart until source.size)
        }
    }

    val defaultProcessingConfig = ProcessingConfig(
        parallelism = max(1, Runtime.getRuntime().availableProcessors()).toUInt(),
        maxBlocksWaiting = (max(1, Runtime.getRuntime().availableProcessors()) + 2).toUInt()
    )
}

data class ProcessingConfig(
    val parallelism: UInt,
    val maxBlocksWaiting: UInt
)

interface CompressedSource {
    val size: Long
    fun openSequential(): InputStream
    fun openRange(range: OpenEndRange<Long>): InputStream
}

class ByteBufferCompressedSource(private val buffer: ByteBuffer) : CompressedSource {
    override val size: Long
        get() = buffer.limit().toLong()

    override fun openSequential(): InputStream = openRange(0L..<size)

    override fun openRange(range: OpenEndRange<Long>): InputStream {
        val start = range.start
        val endExclusive = range.endExclusive
        require(start >= 0 && endExclusive >= start && endExclusive <= size) {
            "Invalid range [$start, $endExclusive) for size $size"
        }
        val copy = buffer.asReadOnlyBuffer()
        copy.position(start.toInt())
        copy.limit(endExclusive.toInt())
        return ByteBufferRangeInputStream(copy.slice())
    }
}

class FileCompressedSource(private val path: Path) : CompressedSource {
    override val size: Long
        get() = FileChannel.open(path, StandardOpenOption.READ).use { it.size() }

    override fun openSequential(): InputStream = openRange(0L..<size)

    override fun openRange(range: OpenEndRange<Long>): InputStream {
        val start = range.start
        val endExclusive = range.endExclusive
        require(start >= 0 && endExclusive >= start) { "Invalid range [$start, $endExclusive)" }
        val channel = FileChannel.open(path, StandardOpenOption.READ)
        try {
            val fileSize = channel.size()
            require(endExclusive <= fileSize) {
                "Range [$start, $endExclusive) exceeds file size $fileSize"
            }
        } catch (t: Throwable) {
            channel.close()
            throw t
        }
        return BufferedInputStream(FileChannelRangeInputStream(channel, start, endExclusive - start))
    }
}

private class ByteBufferRangeInputStream(private val buffer: ByteBuffer) : InputStream() {
    override fun read(): Int {
        if (!buffer.hasRemaining()) return -1
        return buffer.get().toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (!buffer.hasRemaining()) return -1
        val toRead = minOf(len, buffer.remaining())
        buffer.get(b, off, toRead)
        return toRead
    }
}

private class FileChannelRangeInputStream(
    private val channel: FileChannel,
    start: Long,
    size: Long
) : InputStream() {
    private val delegate: InputStream
    private var remaining = size
    private var closed = false

    init {
        channel.position(start)
        delegate = Channels.newInputStream(channel)
    }

    override fun read(): Int {
        if (remaining <= 0) return -1
        val value = delegate.read()
        if (value < 0) return -1
        remaining--
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (remaining <= 0) return -1
        val toRead = minOf(len.toLong(), remaining).toInt()
        val read = delegate.read(b, off, toRead)
        if (read <= 0) return -1
        remaining -= read.toLong()
        return read
    }

    override fun close() {
        if (closed) return
        closed = true
        channel.close()
    }
}

private class ParallelBzip2InputStream(
    private val source: CompressedSource,
    private val substreams: List<OpenEndRange<Long>>,
    config: ProcessingConfig
) : InputStream() {
    private val windowSize = max(1, config.maxBlocksWaiting.toInt())
    private val workerCount = minOf(max(1, config.parallelism.toInt()), substreams.size)
    private val jobs = ArrayBlockingQueue<Int>(windowSize)
    private val chunkBuffer = BlockingOrderedChunkBuffer(windowSize)
    private val workers: List<Thread>

    private var nextToDispatch = 0
    private var nextToRead = 0
    private var currentChunk: ByteArray? = null
    private var currentOffset = 0
    @Volatile
    private var closed = false
    @Volatile
    private var failure: Throwable? = null

    init {
        workers = (0..<workerCount).map {
            Thread.ofVirtual().unstarted {
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        val index = jobs.take()
                        val range = substreams[index]
                        try {
                            val bytes = source.openRange(range).use { raw ->
                                BZip2CompressorInputStream(raw, false).use { it.readAllBytes() }
                            }
                            if (!chunkBuffer.put(index, bytes)) return@unstarted
                        } catch (_: InterruptedException) {
                            return@unstarted
                        } catch (t: Throwable) {
                            if (failure == null) {
                                failure = t
                            }
                            chunkBuffer.close()
                            return@unstarted
                        }
                    }
                } catch (_: InterruptedException) {
                    return@unstarted
                }
            }
        }
        workers.forEach { it.start() }
        dispatchMore()
    }

    override fun read(): Int {
        if (!ensureChunk()) return -1
        val b = currentChunk!![currentOffset].toInt() and 0xFF
        currentOffset++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (!ensureChunk()) return -1

        var bytesRead = 0
        var offset = off
        var remaining = len
        while (remaining > 0) {
            if (!ensureChunk()) break
            val chunk = currentChunk!!
            val available = chunk.size - currentOffset
            val toCopy = minOf(available, remaining)
            chunk.copyInto(b, destinationOffset = offset, startIndex = currentOffset, endIndex = currentOffset + toCopy)
            currentOffset += toCopy
            offset += toCopy
            remaining -= toCopy
            bytesRead += toCopy
        }
        return bytesRead
    }

    override fun close() {
        if (closed) return
        closed = true
        chunkBuffer.close()
        workers.forEach { it.interrupt() }
        workers.forEach { it.join() }
    }

    private fun ensureChunk(): Boolean {
        while (true) {
            if (closed) return false
            failure?.let { throw IOException("Parallel bzip2 decompression failed", it) }

            val chunk = currentChunk
            if (chunk != null && currentOffset < chunk.size) {
                return true
            }
            currentChunk = null
            currentOffset = 0

            if (nextToRead >= substreams.size) {
                return false
            }

            dispatchMore()
            val next = chunkBuffer.take(nextToRead)
            if (next == null) {
                if (closed) return false
                failure?.let { throw IOException("Parallel bzip2 decompression failed", it) }
                continue
            }
            currentChunk = next
            nextToRead++
            dispatchMore()
            return true
        }
    }

    private fun dispatchMore() {
        while (nextToDispatch < substreams.size && nextToDispatch - nextToRead < windowSize) {
            if (!jobs.offer(nextToDispatch)) {
                return
            }
            nextToDispatch++
        }
    }
}

private class BlockingOrderedChunkBuffer(private val capacity: Int) {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private val slotData = arrayOfNulls<ByteArray>(capacity)
    private val slotIndex = IntArray(capacity) { SLOT_EMPTY }
    private var closed = false

    fun put(index: Int, data: ByteArray): Boolean {
        lock.withLock {
            val slot = index % capacity
            while (!closed && slotIndex[slot] != SLOT_EMPTY) {
                changed.await()
            }
            if (closed) return false

            slotData[slot] = data
            slotIndex[slot] = index
            changed.signalAll()
            return true
        }
    }

    fun take(index: Int): ByteArray? {
        lock.withLock {
            val slot = index % capacity
            while (!closed && slotIndex[slot] != index) {
                changed.await()
            }
            if (slotIndex[slot] != index) return null

            val data = slotData[slot] ?: error("Slot $slot marked ready without data")
            slotData[slot] = null
            slotIndex[slot] = SLOT_EMPTY
            changed.signalAll()
            return data
        }
    }

    fun close() {
        lock.withLock {
            closed = true
            changed.signalAll()
        }
    }

    companion object {
        private const val SLOT_EMPTY = -1
    }
}
