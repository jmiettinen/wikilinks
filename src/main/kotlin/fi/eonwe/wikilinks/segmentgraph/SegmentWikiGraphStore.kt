package fi.eonwe.wikilinks.segmentgraph

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min

class SegmentWikiGraphStore private constructor(
    private val channel: FileChannel,
    private val arena: Arena,
    private val nodes: MemorySegment,
    private val titles: MemorySegment,
    private val outEdges: MemorySegment,
    private val inEdges: MemorySegment,
    private val nameIndex: MemorySegment,
    private val nameKeys: MemorySegment,
    private val idIndex: MemorySegment,
    val nodeCount: Int
) : AutoCloseable {

    fun findIdByTitle(title: String): Int? {
        val query = title.toByteArray(Charsets.UTF_8)
        val rank = binarySearchNameIndex(query)
        return if (rank >= 0) nameRecordId(rank) else null
    }

    private fun binarySearchNameIndex(query: ByteArray): Int {
        var lo = 0
        var hi = nodeCount - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when (compareNameRecordWithQuery(mid, query)) {
                0 -> return mid
                in Int.MIN_VALUE..-1 -> hi = mid - 1
                else -> lo = mid + 1
            }
        }
        return -1
    }

    fun hasTitle(title: String): Boolean = findIdByTitle(title) != null

    fun randomTitle(): String? {
        if (nodeCount == 0) return null
        val rank = ThreadLocalRandom.current().nextInt(nodeCount)
        return titleOf(nameRecordId(rank))
    }

    fun findTitlesByPrefix(prefix: String, maxMatches: Int): List<String> {
        if (maxMatches <= 0 || nodeCount == 0) return emptyList()
        val p = prefix.toByteArray(Charsets.UTF_8)
        var lo = 0
        var hi = nodeCount
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            val cmp = comparePrefixAgainstNameRecord(p, mid)
            if (cmp <= 0) {
                hi = mid
            } else {
                lo = mid + 1
            }
        }
        val start = lo
        if (start !in 0 until nodeCount) return emptyList()

        val out = ArrayList<String>(min(maxMatches, 16))
        var rank = start
        while (rank < nodeCount && out.size < maxMatches) {
            if (!nameRecordStartsWith(rank, p)) break
            out.add(titleOf(nameRecordId(rank)))
            rank++
        }
        return out
    }

    fun titleOf(id: Int): String {
        val rank = rankOfId(id)
        val base = nodeRecordOffset(rank)
        val titleOffset = nodes.get(I64, base + NODE_TITLE_OFFSET)
        val titleLen = nodes.get(I32, base + NODE_TITLE_LEN_OFFSET)
        val data = ByteArray(titleLen)
        for (i in 0 until titleLen) {
            data[i] = titles.get(I8, titleOffset + i)
        }
        return String(data, Charsets.UTF_8)
    }

    fun isRedirect(id: Int): Boolean {
        val rank = rankOfId(id)
        val base = nodeRecordOffset(rank)
        val flags = nodes.get(I32, base + NODE_FLAGS_OFFSET)
        return flags and FLAG_REDIRECT != 0
    }

    fun outNeighbors(id: Int): IntCursor {
        val rank = rankOfId(id)
        val base = nodeRecordOffset(rank)
        val start = nodes.get(I64, base + NODE_OUT_START_OFFSET)
        val degree = nodes.get(I32, base + NODE_OUT_DEGREE_OFFSET)
        return SegmentIntCursor(outEdges, start, degree)
    }

    fun inNeighbors(id: Int): IntCursor {
        val rank = rankOfId(id)
        val base = nodeRecordOffset(rank)
        val start = nodes.get(I64, base + NODE_IN_START_OFFSET)
        val degree = nodes.get(I32, base + NODE_IN_DEGREE_OFFSET)
        return SegmentIntCursor(inEdges, start, degree)
    }

    fun forEachNode(consumer: (NodeRecord) -> Unit) {
        for (entry in 0 until nodeCount) {
            val entryOffset = entry.toLong() * ID_RECORD_SIZE_BYTES
            val id = idIndex.get(I32, entryOffset + ID_ID_OFFSET)
            val rank = idIndex.get(I32, entryOffset + ID_RANK_OFFSET)
            val base = nodeRecordOffset(rank)
            val titleOffset = nodes.get(I64, base + NODE_TITLE_OFFSET)
            val titleLen = nodes.get(I32, base + NODE_TITLE_LEN_OFFSET)
            val titleBytes = ByteArray(titleLen)
            for (i in 0 until titleLen) {
                titleBytes[i] = titles.get(I8, titleOffset + i)
            }
            val outStart = nodes.get(I64, base + NODE_OUT_START_OFFSET)
            val outDegree = nodes.get(I32, base + NODE_OUT_DEGREE_OFFSET)
            val outLinks = IntArray(outDegree)
            for (i in 0 until outDegree) {
                outLinks[i] = outEdges.get(I32, (outStart + i.toLong()) * Int.SIZE_BYTES)
            }
            val flags = nodes.get(I32, base + NODE_FLAGS_OFFSET)
            consumer(
                NodeRecord(
                    id = id,
                    title = String(titleBytes, Charsets.UTF_8),
                    isRedirect = (flags and FLAG_REDIRECT) != 0,
                    outLinks = outLinks
                )
            )
        }
    }

    override fun close() {
        arena.close()
        channel.close()
    }

    private fun nameRecordOffset(rank: Int): Long = rank.toLong() * NAME_RECORD_SIZE_BYTES

    private fun nameRecordKeyOffset(rank: Int): Long = nameIndex.get(I64, nameRecordOffset(rank) + NAME_KEY_OFFSET)

    private fun nameRecordKeyLen(rank: Int): Int = nameIndex.get(I32, nameRecordOffset(rank) + NAME_KEY_LEN_OFFSET)

    private fun nameRecordId(rank: Int): Int = nameIndex.get(I32, nameRecordOffset(rank) + NAME_ID_OFFSET)

    private fun compareNameRecordWithQuery(rank: Int, query: ByteArray): Int {
        val keyOffset = nameRecordKeyOffset(rank)
        val keyLen = nameRecordKeyLen(rank)
        return compareUnsignedLex(query, nameKeys, keyOffset, keyLen)
    }

    private fun nodeRecordOffset(rank: Int): Long = rank.toLong() * NODE_RECORD_SIZE_BYTES

    private fun compareUnsignedLex(query: ByteArray, keys: MemorySegment, keyOffset: Long, keyLen: Int): Int {
        val minLen = min(query.size, keyLen)
        for (i in 0 until minLen) {
            val q = query[i].toInt() and 0xFF
            val k = keys.get(I8, keyOffset + i).toInt() and 0xFF
            if (q != k) return q - k
        }
        return query.size - keyLen
    }

    private fun comparePrefixAgainstNameRecord(prefix: ByteArray, rank: Int): Int {
        val keyOffset = nameRecordKeyOffset(rank)
        val keyLen = nameRecordKeyLen(rank)
        val minLen = min(prefix.size, keyLen)
        for (i in 0 until minLen) {
            val p = prefix[i].toInt() and 0xFF
            val k = nameKeys.get(I8, keyOffset + i).toInt() and 0xFF
            if (p != k) return p - k
        }
        return if (prefix.size <= keyLen) 0 else 1
    }

    private fun nameRecordStartsWith(rank: Int, prefix: ByteArray): Boolean {
        val keyOffset = nameRecordKeyOffset(rank)
        val keyLen = nameRecordKeyLen(rank)
        if (prefix.size > keyLen) return false
        for (i in prefix.indices) {
            if (nameKeys.get(I8, keyOffset + i) != prefix[i]) return false
        }
        return true
    }

    private fun rankOfId(id: Int): Int {
        var lo = 0
        var hi = nodeCount - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val entryOffset = mid.toLong() * ID_RECORD_SIZE_BYTES
            val candidateId = idIndex.get(I32, entryOffset + ID_ID_OFFSET)
            when {
                id == candidateId -> return idIndex.get(I32, entryOffset + ID_RANK_OFFSET)
                id < candidateId -> hi = mid - 1
                else -> lo = mid + 1
            }
        }
        throw IllegalArgumentException("Id $id not found")
    }

    companion object {
        const val MAGIC: Long = 0x574B475241463031L // "WKGRAF01"
        const val VERSION: Int = 1
        const val FLAG_REDIRECT: Int = 1

        const val HEADER_SIZE_BYTES: Int = 176
        const val NODE_RECORD_SIZE_BYTES: Long = 56
        const val NAME_RECORD_SIZE_BYTES: Long = 16
        const val ID_RECORD_SIZE_BYTES: Long = 8

        private const val NODE_TITLE_OFFSET: Long = 0
        private const val NODE_TITLE_LEN_OFFSET: Long = 8
        private const val NODE_FLAGS_OFFSET: Long = 12
        private const val NODE_ID_OFFSET: Long = 16
        private const val NODE_OUT_START_OFFSET: Long = 24
        private const val NODE_OUT_DEGREE_OFFSET: Long = 32
        private const val NODE_IN_START_OFFSET: Long = 40
        private const val NODE_IN_DEGREE_OFFSET: Long = 48

        private const val NAME_KEY_OFFSET: Long = 0
        private const val NAME_KEY_LEN_OFFSET: Long = 8
        private const val NAME_ID_OFFSET: Long = 12
        private const val ID_ID_OFFSET: Long = 0
        private const val ID_RANK_OFFSET: Long = 4

        private const val HEADER_MAGIC_OFFSET: Long = 0
        private const val HEADER_VERSION_OFFSET: Long = 8
        private const val HEADER_NODE_COUNT_OFFSET: Long = 16
        private const val HEADER_EDGE_COUNT_OUT_OFFSET: Long = 24
        private const val HEADER_EDGE_COUNT_IN_OFFSET: Long = 32
        private const val HEADER_NODES_OFFSET: Long = 40
        private const val HEADER_NODES_LENGTH_OFFSET: Long = 48
        private const val HEADER_TITLES_OFFSET: Long = 56
        private const val HEADER_TITLES_LENGTH_OFFSET: Long = 64
        private const val HEADER_OUT_EDGES_OFFSET: Long = 72
        private const val HEADER_OUT_EDGES_LENGTH_OFFSET: Long = 80
        private const val HEADER_IN_EDGES_OFFSET: Long = 88
        private const val HEADER_IN_EDGES_LENGTH_OFFSET: Long = 96
        private const val HEADER_NAME_INDEX_OFFSET: Long = 104
        private const val HEADER_NAME_INDEX_LENGTH_OFFSET: Long = 112
        private const val HEADER_NAME_KEYS_OFFSET: Long = 120
        private const val HEADER_NAME_KEYS_LENGTH_OFFSET: Long = 128
        private const val HEADER_ID_INDEX_OFFSET: Long = 136
        private const val HEADER_ID_INDEX_LENGTH_OFFSET: Long = 144

        private val I8: ValueLayout.OfByte = ValueLayout.JAVA_BYTE
        private val I32: ValueLayout.OfInt =
            ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN) as ValueLayout.OfInt
        private val I64: ValueLayout.OfLong =
            ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN) as ValueLayout.OfLong

        fun open(path: Path): SegmentWikiGraphStore {
            val channel = FileChannel.open(path, StandardOpenOption.READ)
            val arena = Arena.ofShared()
            try {
                val header = channel.map(FileChannel.MapMode.READ_ONLY, 0, HEADER_SIZE_BYTES.toLong(), arena)
                val magic = header.get(I64, HEADER_MAGIC_OFFSET)
                require(magic == MAGIC) { "Invalid segment graph magic: $magic" }
                val version = header.get(I32, HEADER_VERSION_OFFSET)
                require(version == VERSION) { "Unsupported segment graph version: $version" }

                val nodeCountLong = header.get(I64, HEADER_NODE_COUNT_OFFSET)
                require(nodeCountLong in 1..Int.MAX_VALUE.toLong()) { "Invalid node count $nodeCountLong" }
                val nodeCount = nodeCountLong.toInt()
                require(header.get(I64, HEADER_EDGE_COUNT_OUT_OFFSET) >= 0) { "Invalid out-edge count" }
                require(header.get(I64, HEADER_EDGE_COUNT_IN_OFFSET) >= 0) { "Invalid in-edge count" }

                val nodesOffset = header.get(I64, HEADER_NODES_OFFSET)
                val nodesLen = header.get(I64, HEADER_NODES_LENGTH_OFFSET)
                val titlesOffset = header.get(I64, HEADER_TITLES_OFFSET)
                val titlesLen = header.get(I64, HEADER_TITLES_LENGTH_OFFSET)
                val outOffset = header.get(I64, HEADER_OUT_EDGES_OFFSET)
                val outLen = header.get(I64, HEADER_OUT_EDGES_LENGTH_OFFSET)
                val inOffset = header.get(I64, HEADER_IN_EDGES_OFFSET)
                val inLen = header.get(I64, HEADER_IN_EDGES_LENGTH_OFFSET)
                val nameIndexOffset = header.get(I64, HEADER_NAME_INDEX_OFFSET)
                val nameIndexLen = header.get(I64, HEADER_NAME_INDEX_LENGTH_OFFSET)
                val nameKeysOffset = header.get(I64, HEADER_NAME_KEYS_OFFSET)
                val nameKeysLen = header.get(I64, HEADER_NAME_KEYS_LENGTH_OFFSET)
                val idIndexOffset = header.get(I64, HEADER_ID_INDEX_OFFSET)
                val idIndexLen = header.get(I64, HEADER_ID_INDEX_LENGTH_OFFSET)

                val size = channel.size()
                require(nodesOffset + nodesLen <= size) { "Nodes section out of file bounds" }
                require(titlesOffset + titlesLen <= size) { "Titles section out of file bounds" }
                require(outOffset + outLen <= size) { "Out edges section out of file bounds" }
                require(inOffset + inLen <= size) { "In edges section out of file bounds" }
                require(nameIndexOffset + nameIndexLen <= size) { "Name index section out of file bounds" }
                require(nameKeysOffset + nameKeysLen <= size) { "Name keys section out of file bounds" }
                require(idIndexOffset + idIndexLen <= size) { "Id index section out of file bounds" }

                val nodes = channel.map(FileChannel.MapMode.READ_ONLY, nodesOffset, nodesLen, arena)
                val titles = channel.map(FileChannel.MapMode.READ_ONLY, titlesOffset, titlesLen, arena)
                val outEdges = channel.map(FileChannel.MapMode.READ_ONLY, outOffset, outLen, arena)
                val inEdges = channel.map(FileChannel.MapMode.READ_ONLY, inOffset, inLen, arena)
                val nameIndex = channel.map(FileChannel.MapMode.READ_ONLY, nameIndexOffset, nameIndexLen, arena)
                val nameKeys = channel.map(FileChannel.MapMode.READ_ONLY, nameKeysOffset, nameKeysLen, arena)
                val idIndex = channel.map(FileChannel.MapMode.READ_ONLY, idIndexOffset, idIndexLen, arena)

                return SegmentWikiGraphStore(
                    channel = channel,
                    arena = arena,
                    nodes = nodes,
                    titles = titles,
                    outEdges = outEdges,
                    inEdges = inEdges,
                    nameIndex = nameIndex,
                    nameKeys = nameKeys,
                    idIndex = idIndex,
                    nodeCount = nodeCount
                )
            } catch (t: Throwable) {
                try {
                    arena.close()
                } finally {
                    channel.close()
                }
                throw t
            }
        }
    }
}
interface IntCursor {
    fun hasNext(): Boolean
    fun nextInt(): Int
}

private class SegmentIntCursor(
    private val edges: MemorySegment,
    private val start: Long,
    degree: Int
) : IntCursor {
    private var cursor = 0
    private val degree = degree

    override fun hasNext(): Boolean = cursor < degree

    override fun nextInt(): Int {
        if (!hasNext()) {
            throw NoSuchElementException("No more elements")
        }
        val value = edges.get(
            ValueLayout.JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN) as ValueLayout.OfInt,
            (start + cursor.toLong()) * Int.SIZE_BYTES
        )
        cursor++
        return value
    }
}
