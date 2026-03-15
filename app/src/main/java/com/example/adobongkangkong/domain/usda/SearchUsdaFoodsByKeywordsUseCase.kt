package com.example.adobongkangkong.domain.usda

import android.util.Log
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchService
import javax.inject.Inject

/**
 * Searches USDA `/foods/search` using free-text keywords.
 *
 * Purpose
 * - Provide a lightweight USDA food search for manual discovery.
 * - Intended primarily for development / advanced user flows.
 *
 * Rationale
 * - Barcode search is the primary UX for adding foods.
 * - Keyword search is useful for testing and discovering USDA catalog entries.
 * - This use case returns candidate foods plus the raw JSON so downstream
 *   flows can import the selected item without another network request.
 *
 * Behavior
 *
 * Step 1 — Validate query
 * - Trim whitespace.
 * - Blank input → Blocked.
 *
 * Step 2 — Fetch USDA search JSON
 * - Calls [UsdaFoodsSearchService.searchByKeywords].
 *
 * Step 3 — Parse USDA response
 * - Uses [UsdaFoodsSearchParser.parse].
 *
 * Step 4 — Build lightweight UI candidates
 * - Each USDA item becomes a [PickItem] suitable for display.
 *
 * Step 5 — Return candidates + raw JSON
 *
 * Parameters
 * - query: user search keywords
 * - pageSize: number of items to request
 * - pageNumber: USDA paging index (1-based)
 *
 * Return
 * - Success → candidates list + raw JSON
 * - Blocked → blank query or no results
 * - Failed → unexpected system failure
 *
 * Architectural rules
 * - No DB writes.
 * - No navigation.
 * - Pure network + parse + shaping.
 */
class SearchUsdaFoodsByKeywordsUseCase @Inject constructor(
    private val usdaSearch: UsdaFoodsSearchService
) {

    suspend operator fun invoke(
        query: String,
        pageSize: Int = 30,
        pageNumber: Int = 1
    ): Result {

        Log.d("Meow", "SearchUsdaFoodsByKeywordsUseCase > Invoke $query")

        val cleaned = query.trim()
        if (cleaned.isBlank()) {
            return Result.Blocked("Blank search query")
        }

        val json = usdaSearch.searchByKeywords(
            query = cleaned,
            pageSize = pageSize,
            pageNumber = pageNumber
        ) ?: return Result.Failed("USDA search returned null response")

        val parsed = UsdaFoodsSearchParser.parse(json)
        val foods = parsed.foods

        if (foods.isEmpty()) {
            return Result.Blocked("No USDA foods found")
        }

        val candidates = foods.map {
            val householdServingFullText = it.householdServingFullText?.trim().orEmpty()
            val fallbackServingText = run {
                val ss = it.servingSize
                val u = it.servingSizeUnit
                if (ss != null && !u.isNullOrBlank()) "$ss $u" else ""
            }

            PickItem(
                fdcId = it.fdcId,
                description = it.description?.trim().orEmpty(),
                brand = (it.brandName ?: it.brandOwner)?.trim().orEmpty(),
                servingText = householdServingFullText.ifBlank { fallbackServingText },
                householdServingFullText = householdServingFullText.ifBlank { null },
                packageWeight = it.packageWeight?.trim().takeIf { s -> !s.isNullOrBlank() },
                dataType = it.dataType?.trim().takeIf { s -> !s.isNullOrBlank() },
                gtinUpc = it.gtinUpc?.trim().orEmpty(),
                publishedDateIso = it.publishedDate?.trim().takeIf { s -> !s.isNullOrBlank() },
                modifiedDateIso = it.modifiedDate?.trim().takeIf { s -> !s.isNullOrBlank() },
            )
        }

        Log.d(
            "Meow",
            "SearchUsdaFoodsByKeywordsUseCase> results=${candidates.size}"
        )

        return Result.Success(
            query = cleaned,
            searchJson = json,
            candidates = candidates,
            pageNumber = pageNumber
        )
    }

    /**
     * Lightweight USDA candidate item for UI display.
     *
     * Display notes:
     * - `servingText` is the primary short serving display.
     * - `householdServingFullText` preserves the fuller USDA household wording when available.
     * - `packageWeight` is display-only and helps distinguish packaging variants.
     * - `dataType` is useful for debugging / advanced test UI (e.g. Branded vs Foundation).
     */
    data class PickItem(
        val fdcId: Long,
        val description: String,
        val brand: String,
        val servingText: String,
        val householdServingFullText: String?,
        val packageWeight: String?,
        val dataType: String?,
        val gtinUpc: String,
        val publishedDateIso: String?,
        val modifiedDateIso: String?
    )

    sealed class Result {

        data class Success(
            val query: String,
            val searchJson: String,
            val candidates: List<PickItem>,
            val pageNumber: Int
        ) : Result()

        data class Blocked(val reason: String) : Result()

        data class Failed(val message: String) : Result()
    }
}
