package com.example.adobongkangkong.domain.usda

import android.util.Log
import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchService
import javax.inject.Inject

/**
 * Imports USDA food data using a barcode scan, with version-gating to avoid unnecessary re-imports.
 *
 * ============================================================
 * DATA FLOW DIAGRAM (DFD) — Barcode → USDA → Food snapshot
 * ============================================================
 *
 *   Barcode Scanner
 *        │
 *        ▼
 *   normalizeDigits()
 *        │
 *        ▼
 *   UsdaFoodsSearchService.searchByBarcode()
 *        │
 *        ▼
 *   UsdaFoodsSearchParser.parse()
 *        │
 *        ▼
 *   FoodBarcodeRepository.getByBarcode()
 *        │
 *        ├──────────────► Existing USDA mapping?
 *        │                     │
 *        │                     ├─ YES → Compare publishedDate
 *        │                     │        │
 *        │                     │        ├─ Not newer → RETURN existing foodId (skip import)
 *        │                     │        └─ Newer → continue import
 *        │                     │
 *        │                     └─ NO → continue import
 *        │
 *        ▼
 *   ImportUsdaFoodFromSearchJsonUseCase
 *        │
 *        ▼
 *   Writes:
 *      FoodEntity
 *      FoodNutrientEntity rows
 *      FoodBarcodeEntity mapping
 *        │
 *        ▼
 *   RETURN foodId + USDA metadata
 *
 * ============================================================
 *
 * Purpose
 * - Retrieve authoritative nutrition and serving data from the USDA FoodData Central API using a barcode.
 * - Persist the food locally only when necessary.
 * - Reuse existing USDA imports whenever the USDA record has not changed.
 *
 * Rationale (why this use case exists)
 * - Barcode scanning is the fastest and most reliable way for users to add foods.
 * - USDA is treated as the authoritative source for standardized nutrition.
 * - However, repeatedly importing identical data is expensive and risks unnecessary DB churn.
 * - USDA provides publishedDate and modifiedDate fields that can act as version gates.
 * - This use case avoids re-importing when the existing USDA mapping is already current.
 *
 * Authority and ownership model
 * - USDA mappings are authoritative.
 * - USER_ASSIGNED mappings do NOT block USDA import.
 * - If USDA later recognizes a previously unknown barcode, the USDA mapping replaces the manual mapping.
 *
 * Version-gating logic
 * - Existing mapping found AND source == USDA:
 *      Compare USDA publishedDate values.
 *
 *      If newPublishedDate <= existingPublishedDate:
 *          Skip import.
 *          Return existing foodId.
 *
 *      If newPublishedDate > existingPublishedDate:
 *          Import new version.
 *
 * - Existing mapping found AND source == USER_ASSIGNED:
 *      USDA import proceeds normally.
 *
 * - No existing mapping:
 *      USDA import proceeds normally.
 *
 * ISO date comparison
 * - USDA dates use ISO yyyy-MM-dd format.
 * - Lexicographic comparison is valid and intentional.
 *
 * Behavior summary
 *
 * Step 1 — Normalize barcode
 * - Removes whitespace and non-digit characters.
 *
 * Step 2 — Query USDA search API
 * - Uses [UsdaFoodsSearchService.searchByBarcode].
 * - Returns raw JSON.
 *
 * Step 3 — Parse USDA JSON
 * - Uses [UsdaFoodsSearchParser.parse].
 * - Extracts metadata needed for version gating.
 *
 * Step 4 — Check existing mapping
 * - Uses [FoodBarcodeRepository.getByBarcode].
 *
 * Step 5 — Version gate decision
 * - Skip import if existing USDA mapping is already current.
 *
 * Step 6 — Import if required
 * - Delegates to [ImportUsdaFoodFromSearchJsonUseCase].
 * - That use case performs all persistence.
 *
 * Step 7 — Return identifiers
 * - foodId (local DB id)
 * - fdcId (USDA id)
 * - gtinUpc
 * - publishedDate
 * - modifiedDate
 *
 * Parameters
 * - barcode:
 *   Raw barcode string from scanner or manual entry.
 *
 * Return
 * - [Result.Success]
 *   Food is available locally.
 *   Either imported now or reused from existing mapping.
 *
 * - [Result.Blocked]
 *   USDA returned no foods or unsupported data.
 *
 * - [Result.Failed]
 *   Network, parsing, or unexpected system failure.
 *
 * Edge cases
 * - USDA may return multiple foods; first result is treated as primary candidate.
 * - USDA may omit publishedDate; treated as non-newer.
 * - USER_ASSIGNED mappings do not prevent USDA import.
 *
 * Pitfalls / gotchas
 * - This use case does NOT directly write DB rows.
 * - All writes occur in ImportUsdaFoodFromSearchJsonUseCase.
 * - Version gate relies on publishedDate only (intentional).
 * - modifiedDate is informational only.
 *
 * Architectural rules
 * - USDA import must remain deterministic.
 * - Barcode normalization must always occur before lookup.
 * - USDA mappings must remain authoritative.
 * - Snapshot logs must reference immutable food snapshot IDs.
 *
 * Performance considerations
 * - Prevents unnecessary DB writes.
 * - Prevents duplicate nutrient snapshot creation.
 * - Reduces network and parsing overhead when food already exists.
 */
class ImportUsdaFoodByBarcodeUseCase @Inject constructor(
    private val usdaSearch: UsdaFoodsSearchService,
    private val importFromJson: ImportUsdaFoodFromSearchJsonUseCase,
    private val barcodes: FoodBarcodeRepository
) {

    suspend operator fun invoke(barcode: String): Result {

        Log.d("Meow", "ImportUsdaFoodByBarcodeUseCase> invoke $barcode")

        val normalized = normalizeDigits(barcode)
        if (normalized.isBlank()) return Result.Blocked("Blank barcode")

        val json = usdaSearch.searchByBarcode(normalized)
            ?: return Result.Failed("USDA search returned null response")

        val parsed = runCatching {
            UsdaFoodsSearchParser.parse(json)
        }.getOrNull()
            ?: return Result.Failed("USDA parse failed")

        val first = parsed.foods.firstOrNull()
            ?: return Result.Blocked("USDA search returned no foods")

        val newPublished = first.publishedDate?.trim()?.takeIf { it.isNotBlank() }
        val newModified = first.modifiedDate?.trim()?.takeIf { it.isNotBlank() }
        val newGtin = first.gtinUpc?.trim()?.takeIf { it.isNotBlank() }

        val existing = barcodes.getByBarcode(normalized)

        if (existing != null &&
            existing.source == BarcodeMappingSource.USDA
        ) {

            val oldPublished =
                existing.usdaPublishedDateIso
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

            val isNewer = when {
                newPublished == null -> false
                oldPublished == null -> true
                else -> newPublished > oldPublished
            }

            if (!isNewer &&
                existing.foodId > 0L
            ) {
                return Result.Success(
                    foodId = existing.foodId,
                    fdcId = existing.usdaFdcId ?: first.fdcId,
                    gtinUpc = newGtin,
                    publishedDateIso = oldPublished ?: newPublished,
                    modifiedDateIso = newModified
                )
            }
        }

        return when (val r = importFromJson(json)) {

            is ImportUsdaFoodFromSearchJsonUseCase.Result.Success ->
                Result.Success(
                    foodId = r.foodId,
                    fdcId = r.fdcId,
                    gtinUpc = r.gtinUpc,
                    publishedDateIso = r.publishedDateIso,
                    modifiedDateIso = r.modifiedDateIso
                )

            is ImportUsdaFoodFromSearchJsonUseCase.Result.Blocked ->
                Result.Blocked(r.reason)
        }
    }

    /**
     * Result of barcode-based USDA import attempt.
     *
     * Success
     * - Food exists locally and is safe to use for logging.
     *
     * Blocked
     * - USDA returned no usable result.
     *
     * Failed
     * - System or network failure occurred.
     */
    sealed class Result {

        /**
         * USDA food is available locally.
         *
         * foodId
         *   Local FoodEntity primary key.
         *
         * fdcId
         *   USDA FoodData Central identifier.
         *
         * gtinUpc
         *   Barcode value associated with food.
         *
         * publishedDateIso
         *   USDA version identifier used for version gating.
         *
         * modifiedDateIso
         *   USDA modification timestamp (informational).
         */
        data class Success(
            val foodId: Long,
            val fdcId: Long,
            val gtinUpc: String?,
            val publishedDateIso: String?,
            val modifiedDateIso: String?
        ) : Result()

        /**
         * USDA did not provide a usable food.
         */
        data class Blocked(val reason: String) : Result()

        /**
         * System failure occurred.
         */
        data class Failed(val message: String) : Result()
    }

    /**
     * Normalizes barcode into canonical digits-only form.
     *
     * Required for consistent lookup and identity matching.
     */
    private fun normalizeDigits(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val sb = StringBuilder(trimmed.length)
        for (c in trimmed)
            if (c in '0'..'9')
                sb.append(c)
        return sb.toString()
    }
}

/**
 * ============================================================
 * Bottom KDoc — AI assistant / future developer reference
 * ============================================================
 *
 * Invariants (must never change)
 *
 * - USDA mappings must remain authoritative.
 * - publishedDateIso is the primary version gate.
 * - Do NOT import when existing USDA mapping is already current.
 * - Must always normalize barcode before lookup.
 * - Must never write DB rows directly.
 *
 * Architectural boundaries
 *
 * This use case orchestrates:
 *
 * USDA network fetch
 * version gating
 * import delegation
 *
 * It must NOT:
 *
 * create FoodEntity directly
 * create nutrient rows directly
 * modify barcode mappings directly
 *
 * All persistence handled by ImportUsdaFoodFromSearchJsonUseCase.
 *
 * Identity model
 *
 * Barcode → FoodBarcodeEntity → FoodEntity
 *
 * USDA mapping ensures stable identity across scans.
 *
 * Logging safety
 *
 * Snapshot logs reference FoodEntity.id.
 *
 * This use case ensures:
 *
 * stable identity
 * no duplicate imports
 * version consistency
 *
 * Migration notes
 *
 * If USDA changes versioning scheme:
 *
 * update version gate logic
 *
 * do not remove gate entirely
 *
 * Performance notes
 *
 * Version gate avoids expensive DB writes and nutrient insertions.
 *
 * Recommended future improvements
 *
 * Add caching layer for repeated scans within session.
 *
 * Replace android.util.Log with injected logger abstraction.
 *
 * Consider structured telemetry for USDA import outcomes.
 */