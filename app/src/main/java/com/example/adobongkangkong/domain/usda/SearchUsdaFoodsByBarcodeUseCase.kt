package com.example.adobongkangkong.domain.usda

import android.util.Log
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchService
import javax.inject.Inject

/**
 * Searches USDA `/foods/search` by barcode and returns candidate items + the raw JSON for downstream selection/import.
 *
 * Purpose
 * - Perform a USDA barcode search and return a curated list of candidate foods that the UI can present for selection.
 * - Preserve the raw USDA response JSON so downstream flows can import the exact chosen item without re-fetching.
 *
 * Rationale (why this use case exists)
 * - Barcode search can return multiple foods (especially branded items with variants).
 * - The UI often needs a “picker” list showing:
 *   - name/brand
 *   - serving text
 *   - GTIN/UPC
 * - Downstream import should operate on the same JSON payload to avoid:
 *   - repeated network calls,
 *   - discrepancies from changing USDA results between steps,
 *   - and redundant parsing.
 * - The returned metadata includes published/modified dates to support freshness/version gating in resolution logic.
 *
 * Behavior
 *
 * Step 1 — Validate and normalize input
 * - Trims the incoming barcode.
 * - Blank input → Blocked.
 *
 * Step 2 — Fetch USDA JSON
 * - Calls [UsdaFoodsSearchService.searchByBarcode].
 * - Null response → Failed.
 *
 * Step 3 — Parse USDA response
 * - Uses [UsdaFoodsSearchParser.parse] to materialize the foods list.
 * - No foods → Blocked.
 *
 * Step 4 — Candidate selection policy
 * - Prefer exact `gtinUpc == scannedBarcode` matches when available.
 *   Why:
 *   - USDA responses can contain multiple items whose GTIN/UPC differs from the scanned code (catalog quirks).
 *   - If exact GTIN matches exist, they are the best representation of “this barcode”.
 * - Otherwise, return all foods as candidates.
 *
 * Step 5 — Build lightweight UI candidates
 * - Maps each USDA item into [PickItem] for display and selection.
 * - Includes:
 *   - fdcId (identity for later import)
 *   - display strings (description, brand, servingText)
 *   - gtinUpc
 *   - publishedDateIso / modifiedDateIso for later resolver freshness rules
 *
 * Parameters
 * - barcode:
 *   Raw barcode string from scanner/manual entry.
 *
 * Return
 * - [Result.Success]
 *   Contains:
 *   - scannedBarcode: trimmed canonical value used for matching
 *   - searchJson: raw USDA JSON response (for later import)
 *   - candidates: list of [PickItem] to display/select
 *
 * - [Result.Blocked]
 *   User/actionable issue (blank input, no results).
 *
 * - [Result.Failed]
 *   Unexpected/system failure (null network response).
 *
 * Edge cases
 * - USDA may return multiple foods; picker list may include multiple candidates.
 * - Items may omit householdServingFullText; servingText falls back to servingSize + unit.
 * - Brand fields may be missing; fallback to empty string.
 *
 * Pitfalls / gotchas
 * - This use case does not normalize barcode to digits-only; it only trims.
 *   Callers that store barcodes digits-only should normalize before calling (or standardize upstream).
 * - This use case returns raw JSON, which can be large. Ensure callers do not persist it long-term unintentionally.
 * - Date strings are passed through without parsing here; parsing/validation belongs to resolver logic.
 *
 * Architectural rules (if applicable)
 * - No DB writes.
 * - No navigation.
 * - Pure network + parse + shaping for UI.
 * - Designed to feed ImportUsdaFoodFromSearchJsonUseCase (via returned searchJson + selected fdcId).
 */
class SearchUsdaFoodsByBarcodeUseCase @Inject constructor(
    private val usdaSearch: UsdaFoodsSearchService
) {

    suspend operator fun invoke(barcode: String): Result {
        Log.d("Meow", "SearchUsdaFoodsByBarcodeUseCase > Invoke $barcode")

        // Trim only (not digits-only normalization). Many scan libraries already produce digits-only,
        // but upstream should normalize if repository storage requires it.
        val cleaned = barcode.trim()

        Log.d("Meow", "SearchUsdaFoodsByBarcodeUseCase > barcode cleaned: $cleaned")
        if (cleaned.isBlank()) return Result.Blocked("Blank barcode")

        val json = usdaSearch.searchByBarcode(cleaned)
            ?: return Result.Failed("USDA search returned null response")

        Log.d("Meow", "SearchUsdaFoodsByBarcodeUseCase> json from searchByBarcode> ${json.length}")

        // Parse once and reuse. Downstream import should use this same JSON to avoid re-fetching.
        val parsed = UsdaFoodsSearchParser.parse(json)
        val all = parsed.foods

        if (all.isEmpty()) return Result.Blocked("No results for barcode")

        // Prefer exact gtinUpc match when possible.
        // Non-intuitive but intentional:
        // - USDA may return multiple branded items where some do not match the scanned GTIN exactly.
        // - If exact matches exist, they should be prioritized for user selection.
        val exact = all.filter { it.gtinUpc?.trim() == cleaned }
        val candidatesSource = if (exact.isNotEmpty()) exact else all

        val candidates = candidatesSource.map {
            PickItem(
                fdcId = it.fdcId,
                description = it.description?.trim().orEmpty(),
                brand = (it.brandName ?: it.brandOwner)?.trim().orEmpty(),
                servingText = it.householdServingFullText?.trim()
                    ?: run {
                        // Fallback when household serving text is missing.
                        // Keep it simple and avoid unit conversions here.
                        val ss = it.servingSize
                        val u = it.servingSizeUnit
                        if (ss != null && !u.isNullOrBlank()) "$ss $u" else ""
                    },
                gtinUpc = it.gtinUpc?.trim().orEmpty(),

                // Included for later freshness/version gating in resolver/import orchestration.
                publishedDateIso = it.publishedDate?.trim().takeIf { s -> !s.isNullOrBlank() },
                modifiedDateIso = it.modifiedDate?.trim().takeIf { s -> !s.isNullOrBlank() },
            )
        }

        Log.d(
            "Meow",
            "SearchUsdaFoodsByBarcodeUseCase> Result(barcode:$cleaned jason length: ${json.length} candidate.size:${candidates.size})"
        )

        return Result.Success(
            scannedBarcode = cleaned,
            searchJson = json,
            candidates = candidates
        )
    }

    /**
     * Lightweight UI-facing representation of a USDA candidate from `/foods/search`.
     *
     * Why this exists
     * - The full USDA item model is larger than what the picker UI needs.
     * - The picker must carry stable identity (fdcId) and display strings.
     * - published/modified dates are carried forward to support freshness rules after selection.
     *
     * Notes
     * - servingText is display-only; it is not canonicalized and should not be used for math.
     * - brand is best-effort (brandName or brandOwner); may be empty.
     */
    data class PickItem(
        val fdcId: Long,
        val description: String,
        val brand: String,
        val servingText: String,
        val gtinUpc: String,
        val publishedDateIso: String?,
        val modifiedDateIso: String?,
    )

    /**
     * Result of USDA barcode search.
     *
     * Success
     * - searchJson is returned to allow importing the chosen item without a second network call.
     *
     * Blocked
     * - User/actionable issue (blank barcode or no results).
     *
     * Failed
     * - Unexpected/system issue (null response from service).
     */
    sealed class Result {
        data class Success(
            val scannedBarcode: String,
            val searchJson: String,
            val candidates: List<PickItem>
        ) : Result()

        data class Blocked(val reason: String) : Result()
        data class Failed(val message: String) : Result()
    }
}

/**
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Invariants (must never change)
 * - No DB writes.
 * - Must return raw USDA JSON on success (to avoid re-fetch and ensure deterministic import).
 * - Candidate selection must prefer exact gtinUpc matches when they exist.
 * - PickItem must include fdcId (identity) and published/modified dates (freshness rules downstream).
 *
 * Do not refactor notes
 * - Do not remove returning `searchJson`; downstream import relies on using the same payload.
 * - Do not “improve” servingText into canonical units here; this is display-only and must remain low-risk.
 * - Do not parse dates here; keep parsing in resolver logic so the policy stays centralized.
 *
 * Architectural boundaries
 * - This use case is network + parse + shape only.
 * - Import/persistence is handled by ImportUsdaFoodFromSearchJsonUseCase and orchestrators.
 *
 * Migration notes (KMP / time)
 * - Uses android.util.Log; replace with injected logger for KMP.
 *
 * Performance considerations
 * - Single network call; single parse.
 * - Returns full JSON string; avoid storing long-term in DB.
 *
 * Maintenance recommendations
 * - Consider normalizing barcode to digits-only here only if you standardize repository storage and all call sites.
 * - If USDA starts returning better match signals, update exact-match selection policy carefully (keep determinism).
 */