package fi.eonwe.wikilinks.segmentgraph

import fi.eonwe.wikilinks.fatpages.WikiPageData
import fi.eonwe.wikilinks.leanpages.BufferWikiPage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.math.min

class SegmentWikiGraphSerialization {
    fun serialize(source: GraphDataSource, channel: FileChannel) {
        val records = buildList(source.nodeCount) {
            source.forEachNode { node ->
                add(
                    PageRecord(
                        id = node.id,
                        title = node.title,
                        isRedirect = node.isRedirect,
                        links = node.outLinks
                    )
                )
            }
        }
        serializeRecords(records, channel)
    }

    fun serialize(pages: Collection<BufferWikiPage>, channel: FileChannel) {
        val records = pages.asSequence()
            .map { page ->
                val links = IntArray(page.linkCount)
                var i = 0
                page.forEachLink { target ->
                    links[i++] = target
                }
                PageRecord(
                    id = page.id,
                    title = page.title,
                    isRedirect = page.isRedirect,
                    links = links
                )
            }
            .toList()
        serializeRecords(records, channel)
    }

    fun serializeFatPages(pages: Collection<WikiPageData>, channel: FileChannel) {
        val records = pages.asSequence()
            .map { page ->
                val links = page.links.asSequence()
                    .mapNotNull { ptr -> ptr.page?.id() }
                    .distinct()
                    .toList()
                    .toIntArray()
                PageRecord(
                    id = page.id,
                    title = page.title,
                    isRedirect = false,
                    links = links
                )
            }
            .toList()
        serializeRecords(records, channel)
    }

    private fun serializeRecords(recordsIn: List<PageRecord>, channel: FileChannel) {
        require(recordsIn.isNotEmpty()) { "Cannot serialize empty graph" }
        val records = recordsIn.sortedBy { it.id }
        val nodeCount = records.size
        val idToRank = buildMap(nodeCount) {
            records.forEachIndexed { rank, record ->
                put(record.id, rank)
            }
        }

        val titleBytesByRank = Array(nodeCount) { i -> records[i].title.toByteArray(Charsets.UTF_8) }
        val titleOffsets = LongArray(nodeCount)
        val titleLengths = IntArray(nodeCount)
        var titlesSize = 0L
        for (i in 0 until nodeCount) {
            val titleBytes = titleBytesByRank[i]
            titleOffsets[i] = titlesSize
            titleLengths[i] = titleBytes.size
            titlesSize += titleBytes.size.toLong()
        }

        val outDegree = IntArray(nodeCount)
        val inDegree = IntArray(nodeCount)
        var edgeCountOut = 0L
        for (i in 0 until nodeCount) {
            val links = records[i].links
            outDegree[i] = links.size
            edgeCountOut += links.size.toLong()
            for (target in links) {
                val targetRank = idToRank[target]
                    ?: throw IllegalArgumentException("Target id $target does not exist in node set")
                inDegree[targetRank]++
            }
        }
        val edgeCountIn = edgeCountOut
        require(edgeCountOut <= Int.MAX_VALUE.toLong()) {
            "Too many edges to build in-memory writer buffers: $edgeCountOut"
        }

        val outStart = LongArray(nodeCount)
        val inStart = LongArray(nodeCount)
        run {
            var outCursor = 0L
            var inCursor = 0L
            for (i in 0 until nodeCount) {
                outStart[i] = outCursor
                inStart[i] = inCursor
                outCursor += outDegree[i]
                inCursor += inDegree[i]
            }
        }

        val outEdges = IntArray(edgeCountOut.toInt())
        val inEdges = IntArray(edgeCountIn.toInt())
        run {
            var outCursor = 0
            for (sourceRank in 0 until nodeCount) {
                for (target in records[sourceRank].links) {
                    outEdges[outCursor++] = target
                }
            }

            val inWriteCursor = IntArray(nodeCount)
            for (sourceRank in 0 until nodeCount) {
                val sourceId = records[sourceRank].id
                for (targetId in records[sourceRank].links) {
                    val targetRank = idToRank[targetId]
                        ?: throw IllegalArgumentException("Target id $targetId does not exist in node set")
                    val index = inStart[targetRank].toInt() + inWriteCursor[targetRank]
                    inEdges[index] = sourceId
                    inWriteCursor[targetRank]++
                }
            }
        }

        val sortedNameRanks = (0 until nodeCount).sortedWith { a, b ->
            compareUnsignedBytes(titleBytesByRank[a], titleBytesByRank[b])
        }
        val nameKeyOffsets = LongArray(nodeCount)
        val nameKeyLengths = IntArray(nodeCount)
        var nameKeysSize = 0L
        for ((rank, nodeRank) in sortedNameRanks.withIndex()) {
            val bytes = titleBytesByRank[nodeRank]
            nameKeyOffsets[rank] = nameKeysSize
            nameKeyLengths[rank] = bytes.size
            nameKeysSize += bytes.size.toLong()
        }

        val headerSize = SegmentWikiGraphStore.HEADER_SIZE_BYTES
        val nodesOffset = headerSize.toLong()
        val nodesSize = nodeCount.toLong() * SegmentWikiGraphStore.NODE_RECORD_SIZE_BYTES
        val titlesOffset = nodesOffset + nodesSize
        val outEdgesOffset = titlesOffset + titlesSize
        val outEdgesSize = edgeCountOut * Int.SIZE_BYTES
        val inEdgesOffset = outEdgesOffset + outEdgesSize
        val inEdgesSize = edgeCountIn * Int.SIZE_BYTES
        val nameIndexOffset = inEdgesOffset + inEdgesSize
        val nameIndexSize = nodeCount.toLong() * SegmentWikiGraphStore.NAME_RECORD_SIZE_BYTES
        val nameKeysOffset = nameIndexOffset + nameIndexSize
        val nameKeysSizeFinal = nameKeysSize
        val idIndexOffset = nameKeysOffset + nameKeysSizeFinal
        val idIndexSize = nodeCount.toLong() * SegmentWikiGraphStore.ID_RECORD_SIZE_BYTES

        channel.truncate(0)
        channel.position(0)
        writeHeader(
            channel = channel,
            nodeCount = nodeCount.toLong(),
            edgeCountOut = edgeCountOut,
            edgeCountIn = edgeCountIn,
            nodesOffset = nodesOffset,
            nodesSize = nodesSize,
            titlesOffset = titlesOffset,
            titlesSize = titlesSize,
            outEdgesOffset = outEdgesOffset,
            outEdgesSize = outEdgesSize,
            inEdgesOffset = inEdgesOffset,
            inEdgesSize = inEdgesSize,
            nameIndexOffset = nameIndexOffset,
            nameIndexSize = nameIndexSize,
            nameKeysOffset = nameKeysOffset,
            nameKeysSize = nameKeysSizeFinal,
            idIndexOffset = idIndexOffset,
            idIndexSize = idIndexSize
        )

        writeNodes(
            channel = channel,
            offset = nodesOffset,
            records = records,
            titleOffsets = titleOffsets,
            titleLengths = titleLengths,
            outStart = outStart,
            outDegree = outDegree,
            inStart = inStart,
            inDegree = inDegree,
            ids = records.map { it.id }.toIntArray()
        )
        writeByteChunks(channel, titlesOffset, titleBytesByRank.asList())
        writeIntArray(channel, outEdgesOffset, outEdges)
        writeIntArray(channel, inEdgesOffset, inEdges)
        writeNameIndex(
            channel = channel,
            offset = nameIndexOffset,
            sortedNameRanks = sortedNameRanks,
            nodeIdsByRank = records.map { it.id }.toIntArray(),
            nameKeyOffsets = nameKeyOffsets,
            nameKeyLengths = nameKeyLengths
        )
        writeByteChunks(channel, nameKeysOffset, sortedNameRanks.map { titleBytesByRank[it] })
        writeIdIndex(channel, idIndexOffset, records.map { it.id }.toIntArray())
        channel.force(true)
    }

    private fun writeHeader(
        channel: FileChannel,
        nodeCount: Long,
        edgeCountOut: Long,
        edgeCountIn: Long,
        nodesOffset: Long,
        nodesSize: Long,
        titlesOffset: Long,
        titlesSize: Long,
        outEdgesOffset: Long,
        outEdgesSize: Long,
        inEdgesOffset: Long,
        inEdgesSize: Long,
        nameIndexOffset: Long,
        nameIndexSize: Long,
        nameKeysOffset: Long,
        nameKeysSize: Long,
        idIndexOffset: Long,
        idIndexSize: Long
    ) {
        val bb = ByteBuffer.allocate(SegmentWikiGraphStore.HEADER_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        bb.putLong(SegmentWikiGraphStore.MAGIC)
        bb.putInt(SegmentWikiGraphStore.VERSION)
        bb.putInt(0)
        bb.putLong(nodeCount)
        bb.putLong(edgeCountOut)
        bb.putLong(edgeCountIn)
        bb.putLong(nodesOffset)
        bb.putLong(nodesSize)
        bb.putLong(titlesOffset)
        bb.putLong(titlesSize)
        bb.putLong(outEdgesOffset)
        bb.putLong(outEdgesSize)
        bb.putLong(inEdgesOffset)
        bb.putLong(inEdgesSize)
        bb.putLong(nameIndexOffset)
        bb.putLong(nameIndexSize)
        bb.putLong(nameKeysOffset)
        bb.putLong(nameKeysSize)
        bb.putLong(idIndexOffset)
        bb.putLong(idIndexSize)
        while (bb.hasRemaining()) {
            bb.put(0)
        }
        bb.flip()
        channel.write(bb, 0)
    }

    private fun writeNodes(
        channel: FileChannel,
        offset: Long,
        records: List<PageRecord>,
        titleOffsets: LongArray,
        titleLengths: IntArray,
        outStart: LongArray,
        outDegree: IntArray,
        inStart: LongArray,
        inDegree: IntArray,
        ids: IntArray
    ) {
        val totalBytes = (records.size.toLong() * SegmentWikiGraphStore.NODE_RECORD_SIZE_BYTES).toInt()
        val bb = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in records.indices) {
            bb.putLong(titleOffsets[i])
            bb.putInt(titleLengths[i])
            bb.putInt(if (records[i].isRedirect) SegmentWikiGraphStore.FLAG_REDIRECT else 0)
            bb.putInt(ids[i])
            bb.putInt(0)
            bb.putLong(outStart[i])
            bb.putInt(outDegree[i])
            bb.putInt(0)
            bb.putLong(inStart[i])
            bb.putInt(inDegree[i])
            bb.putInt(0)
        }
        bb.flip()
        channel.write(bb, offset)
    }

    private fun writeNameIndex(
        channel: FileChannel,
        offset: Long,
        sortedNameRanks: List<Int>,
        nodeIdsByRank: IntArray,
        nameKeyOffsets: LongArray,
        nameKeyLengths: IntArray
    ) {
        val totalBytes = (sortedNameRanks.size.toLong() * SegmentWikiGraphStore.NAME_RECORD_SIZE_BYTES).toInt()
        val bb = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in sortedNameRanks.indices) {
            bb.putLong(nameKeyOffsets[i])
            bb.putInt(nameKeyLengths[i])
            bb.putInt(nodeIdsByRank[sortedNameRanks[i]])
        }
        bb.flip()
        channel.write(bb, offset)
    }

    private fun writeIdIndex(channel: FileChannel, offset: Long, nodeIdsByRank: IntArray) {
        val bb = ByteBuffer.allocate((nodeIdsByRank.size.toLong() * SegmentWikiGraphStore.ID_RECORD_SIZE_BYTES).toInt())
            .order(ByteOrder.LITTLE_ENDIAN)
        for (rank in nodeIdsByRank.indices) {
            bb.putInt(nodeIdsByRank[rank])
            bb.putInt(rank)
        }
        bb.flip()
        channel.write(bb, offset)
    }

    private fun writeByteChunks(channel: FileChannel, offset: Long, chunks: List<ByteArray>) {
        channel.position(offset)
        val temp = ByteBuffer.allocate(min(1 shl 20, chunks.sumOf { it.size }.coerceAtLeast(1))).order(ByteOrder.LITTLE_ENDIAN)
        for (chunk in chunks) {
            var chunkOffset = 0
            while (chunkOffset < chunk.size) {
                val toCopy = min(temp.remaining(), chunk.size - chunkOffset)
                temp.put(chunk, chunkOffset, toCopy)
                chunkOffset += toCopy
                if (!temp.hasRemaining()) {
                    temp.flip()
                    while (temp.hasRemaining()) {
                        channel.write(temp)
                    }
                    temp.clear()
                }
            }
        }
        temp.flip()
        while (temp.hasRemaining()) {
            channel.write(temp)
        }
    }

    private fun writeIntArray(channel: FileChannel, offset: Long, values: IntArray) {
        val bb = ByteBuffer.allocate(values.size * Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        for (value in values) {
            bb.putInt(value)
        }
        bb.flip()
        channel.write(bb, offset)
    }

    private fun compareUnsignedBytes(a: ByteArray, b: ByteArray): Int {
        val minLen = min(a.size, b.size)
        for (i in 0 until minLen) {
            val av = a[i].toInt() and 0xFF
            val bv = b[i].toInt() and 0xFF
            if (av != bv) return av - bv
        }
        return a.size - b.size
    }

    private data class PageRecord(
        val id: Int,
        val title: String,
        val isRedirect: Boolean,
        val links: IntArray
    )

    companion object {
        fun open(path: Path): SegmentWikiGraphStore {
            return SegmentWikiGraphStore.open(path)
        }

        fun writeTo(path: Path, pages: Collection<BufferWikiPage>) {
            FileChannel.open(
                path,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            ).use { channel ->
                SegmentWikiGraphSerialization().serialize(pages, channel)
            }
        }

        fun writeFatPagesTo(path: Path, pages: Collection<WikiPageData>) {
            FileChannel.open(
                path,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            ).use { channel ->
                SegmentWikiGraphSerialization().serializeFatPages(pages, channel)
            }
        }
    }
}
