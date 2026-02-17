package fi.eonwe.wikilinks

import fi.eonwe.wikilinks.Utils.asInputStream
import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.max

object WikiReader {

    fun processWiki(data: ByteBuffer, config: ProcessingConfig) {
        readPages(data, config)
    }

    fun readPages(data: ByteBuffer, config: ProcessingConfig = defaultProcessingConfig): MutableList<BufferWikiPage> {
        val decompressedXml = decompressInParallel(data, config)
        return decompressedXml.inputStream().use { WikiProcessor.readPages(it) }
    }

    private fun decompressInParallel(data: ByteBuffer, config: ProcessingConfig): ByteArray {
        val substreams = generateSubstreams(data).map { it.asReadOnlyBuffer() }.toList()
        if (substreams.isEmpty()) return ByteArray(0)

        val queueCapacity = max(1, config.maxBlocksWaiting.toInt())
        val workerCount = minOf(max(1, config.parallelism.toInt()), substreams.size)
        val blocksQueue = ArrayBlockingQueue<WorkToDo>(queueCapacity)
        val outputByIndex = AtomicReferenceArray<ByteArray>(substreams.size)

        val workers = (0..<workerCount).map {
            Thread.ofVirtual().unstarted {
                var shouldStop = false
                while (!shouldStop) {
                    when (val read = blocksQueue.take()) {
                        is WorkToDo.BufferContainer -> {
                            outputByIndex.set(read.index, decompressSingleStream(read.value))
                        }

                        WorkToDo.Done -> {
                            blocksQueue.put(read)
                            shouldStop = true
                        }
                    }
                }
            }
        }

        workers.forEach { it.start() }
        substreams.forEachIndexed { index, stream ->
            blocksQueue.put(WorkToDo.BufferContainer(index, stream))
        }
        blocksQueue.put(WorkToDo.Done)
        workers.forEach { it.join() }

        val totalBytes = (0..<outputByIndex.length()).sumOf { index ->
            outputByIndex.get(index)?.size?.toLong() ?: error("Missing output for substream $index")
        }
        val out = ByteArrayOutputStream(totalBytes.toInt())
        for (i in 0..<outputByIndex.length()) {
            out.write(outputByIndex.get(i) ?: error("Missing output for substream $i"))
        }
        return out.toByteArray()
    }

    private fun decompressSingleStream(bzip2Data: ByteBuffer): ByteArray {
        return BZip2CompressorInputStream(bzip2Data.asInputStream(), false).use { it.readAllBytes() }
    }

    fun generateSubstreams(bzip2Data: ByteBuffer): Sequence<ByteBuffer> = sequence {
        val magic = byteArrayOf(0x42, 0x5A, 0x68) // BZh
        val limit = bzip2Data.limit()
        var pos = 0
        var currentStart = -1

        while (pos <= limit - 4) {
            val isStreamStart =
                bzip2Data.get(pos) == magic[0] &&
                    bzip2Data.get(pos + 1) == magic[1] &&
                    bzip2Data.get(pos + 2) == magic[2] &&
                    (bzip2Data.get(pos + 3).toInt() in '1'.code..'9'.code)

            if (isStreamStart) {
                if (currentStart >= 0) {
                    yield(sliceBuffer(bzip2Data, currentStart, pos))
                }
                currentStart = pos
            }
            pos++
        }

        if (currentStart >= 0 && currentStart < limit) {
            yield(sliceBuffer(bzip2Data, currentStart, limit))
        }
    }

    private fun sliceBuffer(original: ByteBuffer, start: Int, end: Int): ByteBuffer {
        val copy = original.duplicate()
        copy.position(start).limit(end)
        return copy.slice()
    }

    val defaultProcessingConfig = ProcessingConfig(
        parallelism = max(1, Runtime.getRuntime().availableProcessors()).toUInt(),
        maxBlocksWaiting = (max(1, Runtime.getRuntime().availableProcessors()) + 2).toUInt()
    )
}

private sealed interface WorkToDo {
    object Done : WorkToDo

    data class BufferContainer(val index: Int, val value: ByteBuffer) : WorkToDo
}

data class ProcessingConfig(
    val parallelism: UInt,
    val maxBlocksWaiting: UInt
)
