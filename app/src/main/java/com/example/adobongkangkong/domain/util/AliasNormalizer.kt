package com.example.adobongkangkong.domain.util

object AliasNormalizer {

    /**
     * Normalizes user input for alias uniqueness & search.
     *
     * Examples:
     *  - "Vitamin B-6" -> "vitamin b6"
     *  - "B6" -> "b6"
     *  - "pyridoxine" -> "pyridoxine"
     */
    fun key(raw: String): String {
        val trimmed = raw.trim().lowercase()
        // Keep alphanumerics + spaces only; collapse whitespace
        val cleaned = trimmed
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        return cleaned
    }

    fun display(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ")
}
