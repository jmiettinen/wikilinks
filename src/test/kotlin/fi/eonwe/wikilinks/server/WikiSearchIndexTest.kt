package fi.eonwe.wikilinks.server

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WikiSearchIndexTest {
    @Test
    fun `bounded candidates retain only the requested number of best results`() {
        val candidates = BoundedSearchCandidates(limit = 3)

        for (score in 10 downTo 1) {
            candidates.add(SearchCandidate(title = "Title $score", score = score, isRedirect = false))
        }

        candidates.size shouldBe 3
        candidates.results() shouldContainExactly listOf("Title 1", "Title 2", "Title 3")
    }

    @Test
    fun `bounded candidates replace duplicate titles only when the new score is better`() {
        val candidates = BoundedSearchCandidates(limit = 2)

        candidates.add(SearchCandidate(title = "Finland", score = 100, isRedirect = false))
        candidates.add(SearchCandidate(title = "Poland", score = 10, isRedirect = false))
        candidates.add(SearchCandidate(title = "Finland", score = 200, isRedirect = false))
        candidates.add(SearchCandidate(title = "Finland", score = 0, isRedirect = false))

        candidates.size shouldBe 2
        candidates.results() shouldContainExactly listOf("Finland", "Poland")
    }

    @Test
    fun `bounded candidates prefer non-redirects and lexical order when scores tie`() {
        val candidates = BoundedSearchCandidates(limit = 3)

        candidates.add(SearchCandidate(title = "Zulu", score = 10, isRedirect = false))
        candidates.add(SearchCandidate(title = "Alpha redirect", score = 10, isRedirect = true))
        candidates.add(SearchCandidate(title = "Bravo", score = 10, isRedirect = false))
        candidates.add(SearchCandidate(title = "Alpha", score = 10, isRedirect = false))

        candidates.size shouldBe 3
        candidates.results() shouldContainExactly listOf("Alpha", "Bravo", "Zulu")
    }

    @Test
    fun `full negative-score candidate set cannot be improved by fuzzy matches`() {
        val candidates = BoundedSearchCandidates(limit = 2)

        candidates.add(SearchCandidate(title = "Exact", score = -2000, isRedirect = false))
        candidates.add(SearchCandidate(title = "Prefix", score = -1000, isRedirect = false))

        candidates.isFullWithOnlyBetterCandidatesThanFuzzy() shouldBe true
    }

    @Test
    fun `full non-negative candidate set can still be improved by later fuzzy matches`() {
        val candidates = BoundedSearchCandidates(limit = 2)

        candidates.add(SearchCandidate(title = "Typo", score = 100, isRedirect = false))
        candidates.add(SearchCandidate(title = "Tie Z", score = 0, isRedirect = false))

        candidates.isFullWithOnlyBetterCandidatesThanFuzzy() shouldBe false
    }
}
