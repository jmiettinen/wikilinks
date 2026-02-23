package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
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
import kotlin.text.Charsets.UTF_8

object WikiReader {

    enum class IndexSelection {
        AUTO,
        DISABLED,
        EXPLICIT
    }

    fun processWiki(data: MappedByteBuffer, config: ProcessingConfig) {
        readPages(data, config)
    }

    fun readPages(data: MappedByteBuffer, config: ProcessingConfig = defaultProcessingConfig): MutableList<BufferWikiPage> {
        return readPagesWithStats(ByteBufferCompressedSource(data), config).pages
    }

    fun readPages(
        source: FileCompressedSource,
        config: ProcessingConfig = defaultProcessingConfig,
        indexSelection: IndexSelection = IndexSelection.AUTO,
        explicitIndexPath: Path? = null
    ): MutableList<BufferWikiPage> {
        return readPagesWithStats(source, config, indexSelection, explicitIndexPath).pages
    }

    fun readPages(source: CompressedSource, config: ProcessingConfig = defaultProcessingConfig): MutableList<BufferWikiPage> {
        return readPagesWithStats(source, config).pages
    }

    fun readPagesWithStats(
        data: MappedByteBuffer,
        config: ProcessingConfig = defaultProcessingConfig
    ): WikiProcessor.ReadPagesResult {
        return readPagesWithStats(ByteBufferCompressedSource(data), config)
    }

    fun readPagesWithStats(
        source: CompressedSource,
        config: ProcessingConfig = defaultProcessingConfig
    ): WikiProcessor.ReadPagesResult {
        return readSingleThreaded(source)
    }

    private fun readSingleThreaded(source: CompressedSource): WikiProcessor.ReadPagesResult {
        source.openSequential().use { raw ->
            BZip2CompressorInputStream(raw, true).use {
                return WikiProcessor.readPagesWithStats(it)
            }
        }
    }

    fun readPagesWithStats(
        source: FileCompressedSource,
        config: ProcessingConfig = defaultProcessingConfig,
        indexSelection: IndexSelection = IndexSelection.AUTO,
        explicitIndexPath: Path? = null
    ): WikiProcessor.ReadPagesResult {
        val ranges: List<OpenEndRange<Long>> = when (indexSelection) {
            IndexSelection.DISABLED -> emptyList()
            IndexSelection.EXPLICIT -> {
                val path = explicitIndexPath ?: throw IllegalArgumentException("Explicit index path must be provided")
                substreamRangesFromIndex(source, path)
            }
            IndexSelection.AUTO -> {
                val sidecar = source.path.toSidecarIndexPath()
                if (!sidecar.toFile().exists()) {
                    emptyList()
                } else {
                    substreamRangesFromIndex(source, sidecar)
                }
            }
        }

        if (ranges.isEmpty()) {
            return readSingleThreaded(source)
        }

        ParallelBzip2InputStream(source, ranges, config).use {
            return WikiProcessor.readPagesWithStats(it)
        }
    }

    private fun substreamRangesFromIndex(source: CompressedSource, indexPath: Path): List<OpenEndRange<Long>> {
        return try {
            indexPath.toFile().inputStream().use { raw ->
                BZip2CompressorInputStream(raw, true).use { decompressed ->
                    parseIndexRanges(decompressed, source.size)
                }
            }
        } catch (t: Throwable) {
            System.err.printf("Failed to use index %s (%s), falling back to single-threaded read%n", indexPath, t.message)
            emptyList()
        }
    }

    internal fun parseIndexRanges(indexInput: InputStream, compressedSize: Long): List<OpenEndRange<Long>> {
        require(compressedSize > 0) { "Compressed source must not be empty" }
        val offsets = mutableListOf<Long>()
        var previous: Long? = null
        BufferedReader(InputStreamReader(indexInput, UTF_8)).use { reader ->
            reader.lineSequence().forEachIndexed { lineNumber, line ->
                if (line.isBlank()) {
                    return@forEachIndexed
                }
                val separator = line.indexOf(':')
                require(separator > 0) { "Malformed index line ${lineNumber + 1}: missing ':'" }
                val offset = line.substring(0, separator).toLongOrNull()
                    ?: throw IllegalArgumentException("Malformed index line ${lineNumber + 1}: invalid offset")
                require(offset in 0 until compressedSize) {
                    "Index offset out of bounds on line ${lineNumber + 1}: $offset not in [0, $compressedSize)"
                }
                if (previous == null || previous != offset) {
                    if (previous != null) {
                        require(offset > previous!!) {
                            "Index offsets must be strictly increasing: $offset after $previous"
                        }
                    }
                    offsets.add(offset)
                    previous = offset
                }
            }
        }

        if (offsets.isEmpty()) {
            return emptyList()
        }

        if (offsets[0] != 0L) {
            offsets.add(0, 0L)
        }

        return buildList(offsets.size) {
            offsets.forEachIndexed { i, start ->
                val endExclusive = if (i + 1 < offsets.size) offsets[i + 1] else compressedSize
                require(endExclusive > start) {
                    "Invalid index ranges: end $endExclusive must be > start $start"
                }
                add(start until endExclusive)
            }
        }
    }

    private fun Path.toSidecarIndexPath(): Path {
        val name = fileName.toString()
        val indexName = when {
            name.endsWith(".xml.bz2") -> name.removeSuffix(".xml.bz2") + "-index.txt.bz2"
            name.endsWith(".bz2") -> name.removeSuffix(".bz2") + "-index.txt.bz2"
            else -> "$name-index.txt.bz2"
        }
        return resolveSibling(indexName)
    }

    fun generateSubstreams(source: CompressedSource): Sequence<OpenEndRange<Long>> = sequence {
        val magic0 = 0x42 // B
        val magic1 = 0x5A // Z
        val magic2 = 0x68 // h
        val blockMagic0 = 0x31 // 1
        val blockMagic1 = 0x41 // A
        val blockMagic2 = 0x59 // Y
        val blockMagic3 = 0x26 // &
        val blockMagic4 = 0x53 // S
        val blockMagic5 = 0x59 // Y
        var currentStart = -1L
        var pos = 0L

        source.openSequential().use { input ->
            val rolling = IntArray(10) { -1 }
            while (true) {
                val b = input.read()
                if (b < 0) break

                for (i in 0 until rolling.lastIndex) {
                    rolling[i] = rolling[i + 1]
                }
                rolling[rolling.lastIndex] = b
                pos++

                if (
                    rolling[0] == magic0 &&
                    rolling[1] == magic1 &&
                    rolling[2] == magic2 &&
                    rolling[3] in '1'.code..'9'.code &&
                    rolling[4] == blockMagic0 &&
                    rolling[5] == blockMagic1 &&
                    rolling[6] == blockMagic2 &&
                    rolling[7] == blockMagic3 &&
                    rolling[8] == blockMagic4 &&
                    rolling[9] == blockMagic5
                ) {
                    val streamStart = pos - 10
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

class FileCompressedSource(val path: Path) : CompressedSource {
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
                                BZip2CompressorInputStream(raw, true).use { it.readAllBytes() }
                            }
                            if (!chunkBuffer.put(index, bytes)) return@unstarted
                        } catch (_: InterruptedException) {
                            return@unstarted
                        } catch (t: Throwable) {
                            if (failure == null) {
                                failure = IOException(
                                    "Failed to decompress substream index=$index range=[${range.start}, ${range.endExclusive})",
                                    t
                                )
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
