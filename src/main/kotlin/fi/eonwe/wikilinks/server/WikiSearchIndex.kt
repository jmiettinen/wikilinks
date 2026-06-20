package fi.eonwe.wikilinks.server

import fi.eonwe.wikilinks.segmentgraph.SegmentWikiGraphStore
import java.text.Normalizer
import java.util.TreeSet
import kotlin.math.abs
import kotlin.math.min

internal data class SearchCandidate(val title: String, val score: Int, val isRedirect: Boolean)

internal class BoundedSearchCandidates(private val limit: Int) {
    private val byTitle = HashMap<String, SearchCandidate>(limit * 2)
    private val ordered = TreeSet<SearchCandidate>(SEARCH_CANDIDATE_ORDER)

    val size: Int
        get() = byTitle.size

    fun add(candidate: SearchCandidate) {
        val existing = byTitle[candidate.title]
        if (existing != null) {
            if (SEARCH_CANDIDATE_ORDER.compare(candidate, existing) >= 0) return
            byTitle[candidate.title] = candidate
            ordered.remove(existing)
            ordered.add(candidate)
            return
        }

        if (byTitle.size < limit) {
            byTitle[candidate.title] = candidate
            ordered.add(candidate)
            return
        }

        val worst = ordered.lastOrNull() ?: return
        if (SEARCH_CANDIDATE_ORDER.compare(candidate, worst) >= 0) return

        ordered.remove(worst)
        byTitle.remove(worst.title)
        byTitle[candidate.title] = candidate
        ordered.add(candidate)
    }

    fun isFullWithOnlyBetterCandidatesThanFuzzy(): Boolean {
        val worst = ordered.lastOrNull() ?: return false
        return byTitle.size >= limit && worst.score < 0
    }

    fun results(): List<String> {
        return ordered.map { it.title }
    }
}

internal val SEARCH_CANDIDATE_ORDER: Comparator<SearchCandidate> =
    compareBy<SearchCandidate> { it.score }
        .thenBy { it.isRedirect }
        .thenBy { it.title }

class WikiSearchIndex(
    private val store: SegmentWikiGraphStore,
    private val maxFuzzyCandidates: Int = 50_000
) {
    fun search(query: String, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val normalizedQuery = normalizeForSearch(trimmed)
        val candidates = BoundedSearchCandidates(limit)

        // Fast-path for exact/prefix in canonical title index.
        if (store.hasTitle(trimmed)) {
            addCandidate(candidates, trimmed, -2000)
        }
        val prefixMatches = store.findTitlesByPrefix(trimmed, limit)
        for (m in prefixMatches) {
            addCandidate(candidates, m, -1000)
        }
        if (normalizedQuery.isEmpty()) {
            return candidates.results()
        }
        if (candidates.isFullWithOnlyBetterCandidatesThanFuzzy()) {
            return candidates.results()
        }

        val queryBucket = bucketKey(normalizedQuery)
        var fuzzyCandidatesScanned = 0
        store.forEachTitleRecord { _, title, isRedirect ->
            if (isRedirect || fuzzyCandidatesScanned >= maxFuzzyCandidates) return@forEachTitleRecord
            val normalizedTitle = normalizeForSearch(title)
            if (normalizedTitle.isEmpty() || bucketKey(normalizedTitle) != queryBucket) return@forEachTitleRecord
            fuzzyCandidatesScanned++
            val score = score(normalizedTitle, normalizedQuery)
            if (score >= 0) {
                candidates.add(SearchCandidate(title = title, score = score, isRedirect = false))
            }
        }
        return candidates.results()
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

    private fun addCandidate(candidates: BoundedSearchCandidates, title: String, baseScore: Int) {
        val id = store.findIdByTitle(title) ?: return
        val isRedirect = store.isRedirect(id)
        val redirectPenalty = if (isRedirect) 100 else 0
        candidates.add(SearchCandidate(title = title, score = baseScore + redirectPenalty, isRedirect = isRedirect))
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
