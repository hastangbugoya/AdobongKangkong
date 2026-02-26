package com.example.adobongkangkong.domain.util

import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.model.NutrientCategory
import kotlin.math.min

/**
 * Deterministic scoring utility for ranking nutrients against a free-text search query.
 *
 * ## Purpose
 * Compute a single integer score for a [Nutrient] given a user-entered query, so callers can sort
 * nutrients by “best match” without embedding ranking heuristics in UI or repository layers.
 *
 * ## Rationale (why this exists)
 * Nutrient naming is messy:
 * - Users search by display name: "Vitamin B6"
 * - Users search by code-like identifiers: "VITAMIN_B6"
 * - Users search by common names / synonyms: "pyridoxine", "b6", "vit c"
 * - Users make typos.
 *
 * A deterministic, local scorer:
 * - Produces stable ordering (important for UI trust),
 * - Keeps ranking logic centralized and consistent across screens,
 * - Avoids heavy dependencies (no ML, no network),
 * - Allows explicit “tasteful” bumps (e.g., macros appear slightly higher).
 *
 * ## Behavior
 * - Normalizes inputs (lowercase, strips punctuation, collapses whitespace).
 * - Scores the query against multiple fields:
 *   - `displayName` (highest base weight)
 *   - `code` (slightly lower base weight; underscores treated as spaces)
 *   - `aliases` (high base weight; each alias is considered)
 * - For each target field, assigns points for match strength:
 *   1) exact match
 *   2) prefix match
 *   3) word-boundary prefix match (e.g., "vit c" matches "vitamin c")
 *   4) substring match
 *   5) limited fuzzy match using Levenshtein distance (typo tolerance)
 * - Adds a small category-based bonus (macros/energy slightly preferred).
 * - Returns the best score found across all candidate target strings plus the category bonus.
 *
 * ## Parameters
 * - `query`: Raw search query string from user input.
 * - `nutrient`: Nutrient candidate to score.
 * - `aliases`: Additional strings that may represent this nutrient (synonyms, abbreviations).
 *
 * ## Return
 * An integer score where higher values indicate a better match. `0` means “no match”.
 *
 * ## Edge cases
 * - Blank query → score `0`.
 * - Blank targets (missing display name / code / alias) → ignored.
 * - Multiple aliases → best alias match wins.
 *
 * ## Pitfalls / gotchas
 * - This is a heuristic scorer, not a search engine:
 *   - It does not tokenize by advanced linguistic rules,
 *   - It does not rank by popularity or user history,
 *   - It does not do phonetic matching (Soundex, etc.).
 * - Levenshtein is computed against the full target string; for very short queries, fuzzy matching
 *   is intentionally strict (distance threshold scales with query length).
 * - Category bonus is intentionally small; it should not overpower strong textual matches.
 *
 * ## Architectural rules
 * - Pure, deterministic function; no I/O and no dependency on persistence or UI state.
 * - Must remain stable across runs to prevent “shuffling” search results on the user.
 */
object NutrientSearchScorer {

    /**
     * Higher score = better match.
     *
     * We score against:
     * - displayName (e.g., "Vitamin B6")
     * - code (e.g., "VITAMIN_B6")
     * - aliases (e.g., "pyridoxine", "b6")
     */
    fun score(
        query: String,
        nutrient: Nutrient,
        aliases: List<String>
    ): Int {
        val q = normalize(query)
        if (q.isBlank()) return 0

        val name = normalize(nutrient.displayName)
        val code = normalize(nutrient.code.replace("_", " "))
        val aliasKeys = aliases.map { normalize(it) }

        var best = 0

        best = maxOf(best, scoreOne(q, name, base = 1000))
        best = maxOf(best, scoreOne(q, code, base = 950))

        for (a in aliasKeys) {
            best = maxOf(best, scoreOne(q, a, base = 980))
        }

        // category preference bump (optional but “feels right”)
        best += categoryBonus(nutrient.category)

        return best
    }

    private fun scoreOne(q: String, target: String, base: Int): Int {
        if (target.isBlank()) return 0

        // Perfect / strong matches
        if (q == target) return base + 400
        if (target.startsWith(q)) return base + 250
        if (wordBoundaryContains(target, q)) return base + 180
        if (target.contains(q)) return base + 120

        // Fuzzy match (typos)
        val dist = levenshtein(q, target)
        // only accept fuzzy if “close enough”
        if (dist <= maxOf(1, q.length / 4)) {
            return base + 60 - (dist * 10)
        }

        return 0
    }

    private fun categoryBonus(cat: NutrientCategory): Int = when (cat) {
        NutrientCategory.MACRO -> 40
        NutrientCategory.ENERGY -> 35
        NutrientCategory.MINERAL -> 20
        NutrientCategory.VITAMIN -> 18
        else -> 0
    }

    private fun normalize(s: String): String =
        s.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")

    private fun wordBoundaryContains(target: String, q: String): Boolean {
        // “vit c” matches "vitamin c"
        val pattern = Regex("(^|\\s)${Regex.escape(q)}")
        return pattern.containsMatchIn(target)
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = min(
                    min(dp[j] + 1, dp[j - 1] + 1),
                    prev + cost
                )
                prev = temp
            }
        }
        return dp[b.length]
    }
}

/**
 * FUTURE-AI / MAINTENANCE KDoc (Do not remove)
 *
 * ## Invariants (must not change)
 * - Scoring must remain deterministic for the same inputs (no randomness, no time-based behavior).
 * - “Higher score = better match” must remain true across all match types.
 * - Normalization must remain conservative and locale-independent (lowercase, strip punctuation,
 *   collapse whitespace) to keep stable matching across devices.
 * - Best-of strategy must remain: pick the best score across name/code/aliases, then apply category bonus.
 *
 * ## Do not refactor notes
 * - Do not replace this with DB-specific ranking logic without a clear, tested migration plan;
 *   UI ordering stability is a user-facing invariant.
 * - Do not introduce grams↔mL or nutrition semantics here; this is text ranking only.
 * - Avoid micro-optimizations that change scoring behavior (e.g., changing base weights or
 *   match thresholds) unless you also update tests and accept UI ordering changes.
 *
 * ## Architectural boundaries
 * - Pure utility: no repositories, no flows, no I/O, no Android APIs.
 * - Callers may sort/filter using this score; this object should not perform sorting itself.
 *
 * ## Migration notes (KMP / time APIs)
 * - KMP-safe: uses only Kotlin stdlib + regex + math.
 * - If performance becomes an issue on KMP/JS targets, consider replacing Levenshtein with a more
 *   efficient implementation only if behavior (distance results) remains identical.
 *
 * ## Performance considerations
 * - Complexity is O(L1 * L2) per Levenshtein call. This is acceptable for short nutrient strings and
 *   small result sets, but can become expensive if called for many candidates repeatedly.
 * - If used in tight loops (e.g., scoring hundreds of nutrients per keystroke), consider:
 *   - caching normalized fields per nutrient,
 *   - precomputing normalized aliases,
 *   - short-circuiting when strong matches occur,
 *   while keeping score semantics identical.
 *
 * ## Limitations / known tradeoffs
 * - Levenshtein is computed against the full target string; it will under-rank cases where the query
 *   closely matches a substring within a longer target (beyond the substring contains checks).
 * - No phonetic matching, stemming, or synonym expansion beyond provided aliases.
 * - Category bonus is a UX bias; keep it small to avoid surprising rankings.
 */