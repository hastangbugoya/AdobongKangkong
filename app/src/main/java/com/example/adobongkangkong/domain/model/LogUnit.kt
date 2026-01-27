package com.example.adobongkangkong.domain.model

/**
 * How the user expressed the logged amount.
 * - GRAM_COOKED: cooked grams from a specific recipe batch (yield known)
 * - SERVING: "servings" (often recipe portions)
 * - ITEM: fallback/legacy (one unit)
 */
enum class LogUnit {
    GRAM_COOKED,
    SERVING,
    ITEM
}