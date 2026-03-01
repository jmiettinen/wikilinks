package fi.eonwe.wikilinks.server

import fi.eonwe.wikilinks.segmentgraph.SegmentWikiGraphStore
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.min

class WikiSearchIndex(
    private val store: SegmentWikiGraphStore,
    private val maxFuzzyCandidates: Int = 50_000
) {
    private val buckets: Map<Int, IntArray> = buildBuckets(store)

    fun search(query: String, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val normalizedQuery = normalizeForSearch(trimmed)
        val out = ArrayList<String>(limit)
        val seen = HashSet<String>(limit * 2)

        // Fast-path for exact/prefix in canonical title index.
        if (store.hasTitle(trimmed) && seen.add(trimmed)) {
            out.add(trimmed)
        }
        val prefixMatches = store.findTitlesByPrefix(trimmed, limit)
        for (m in prefixMatches) {
            if (seen.add(m)) {
                out.add(m)
                if (out.size >= limit) return out
            }
        }

        if (normalizedQuery.isEmpty()) return out

        val ids = buckets[bucketKey(normalizedQuery)] ?: IntArray(0)
        if (ids.isEmpty()) return out

        val scored = ArrayList<ScoredTitle>(min(ids.size, 128))
        val scanCount = min(ids.size, maxFuzzyCandidates)
        for (i in 0 until scanCount) {
            val id = ids[i]
            val title = store.titleOf(id)
            if (seen.contains(title)) continue
            val normalizedTitle = normalizeForSearch(title)
            val score = score(normalizedTitle, normalizedQuery)
            if (score >= 0) {
                scored.add(ScoredTitle(title, score))
            }
        }
        scored.sortWith(compareBy<ScoredTitle> { it.score }.thenBy { it.title })

        for (candidate in scored) {
            if (seen.add(candidate.title)) {
                out.add(candidate.title)
                if (out.size >= limit) break
            }
        }
        return out
    }

    private fun score(normalizedTitle: String, normalizedQuery: String): Int {
        if (normalizedTitle == normalizedQuery) return 0
        if (normalizedTitle.startsWith(normalizedQuery)) {
            return 10 + min(20, normalizedTitle.length - normalizedQuery.length)
        }
        if (normalizedQuery.startsWith(normalizedTitle)) {
            return 40 + min(20, normalizedQuery.length - normalizedTitle.length)
        }
        val maxDistance = if (normalizedQuery.length <= 4) 1 else 2
        if (abs(normalizedTitle.length - normalizedQuery.length) > maxDistance) return -1
        val distance = boundedLevenshtein(normalizedTitle, normalizedQuery, maxDistance)
        return if (distance <= maxDistance) 100 + distance else -1
    }

    private data class ScoredTitle(val title: String, val score: Int)

    private class IntArrayBuilder(initial: Int = 16) {
        private var data = IntArray(initial)
        var size = 0
            private set

        fun add(v: Int) {
            if (size == data.size) {
                data = data.copyOf(data.size * 2)
            }
            data[size++] = v
        }

        fun toArray(): IntArray = data.copyOf(size)
    }

    companion object {
        fun normalizeForSearch(input: String): String {
            val decomposed = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
            val out = StringBuilder(decomposed.length)
            for (c in decomposed) {
                if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
                if (c.isLetterOrDigit()) {
                    out.append(c)
                } else if (c.isWhitespace()) {
                    if (out.isNotEmpty() && out[out.length - 1] != ' ') {
                        out.append(' ')
                    }
                }
            }
            return out.toString().trim()
        }

        private fun buildBuckets(store: SegmentWikiGraphStore): Map<Int, IntArray> {
            val builders = HashMap<Int, IntArrayBuilder>(2048)
            store.forEachTitle { id, title ->
                val normalized = normalizeForSearch(title)
                if (normalized.isEmpty()) return@forEachTitle
                val key = bucketKey(normalized)
                val builder = builders.getOrPut(key) { IntArrayBuilder() }
                builder.add(id)
            }
            return builders.mapValues { it.value.toArray() }
        }

        private fun bucketKey(normalized: String): Int {
            val c0 = normalized[0].code
            val c1 = if (normalized.length > 1) normalized[1].code else 0
            return (c0 shl 16) or (c1 and 0xFFFF)
        }

        private fun boundedLevenshtein(a: String, b: String, maxDistance: Int): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            if (abs(a.length - b.length) > maxDistance) return maxDistance + 1

            val previous = IntArray(b.length + 1) { it }
            val current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                var rowBest = current[0]
                val ai = a[i - 1]
                for (j in 1..b.length) {
                    val cost = if (ai == b[j - 1]) 0 else 1
                    current[j] = min(
                        min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                    )
                    if (current[j] < rowBest) rowBest = current[j]
                }
                if (rowBest > maxDistance) return maxDistance + 1
                for (j in previous.indices) {
                    previous[j] = current[j]
                }
            }
            return previous[b.length]
        }
    }
}
