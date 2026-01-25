package com.example.adobongkangkong.domain.util

import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.model.NutrientCategory
import kotlin.math.min

object NutrientSearchScorer {

    /**
     * Higher score = better match.
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
