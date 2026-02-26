package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

/**
 * Resolves a barcode into a local foodId, preferring USDA-backed identity and falling back to local mappings.
 *
 * Purpose
 * - Provide a single, canonical “scan barcode → get foodId” decision path for the app.
 * - Enforce barcode ownership precedence:
 *   - USDA mappings are authoritative when available.
 *   - USER_ASSIGNED mappings exist to support manual/offline workflows, but can be overridden by USDA once recognized.
 *
 * Rationale (why this use case exists)
 * - Barcode resolution is a shared dependency across:
 *   - barcode scanning flows
 *   - quick logging
 *   - food editor entry/assignment
 *   - planner actions
 * - Multiple sources may claim the same barcode over time:
 *   - user-assigned mappings (manual linking)
 *   - USDA-recognized barcode imports (authoritative)
 * - This use case centralizes the precedence rules so that:
 *   - the same barcode always resolves to the same foodId under the same state,
 *   - the system gradually “upgrades” manual mappings to USDA mappings when possible,
 *   - mapping metadata (assignedAt/lastSeen/publishedDate gate) remains consistent.
 *
 * Overview (high-level)
 * - Normalize the barcode into digits-only canonical form.
 * - Attempt USDA import first:
 *   - if success: ensure the barcode mapping is USDA-owned and points to the USDA-backed foodId.
 *   - store USDA metadata on the mapping (fdcId + publishedDate gate).
 * - If USDA import is blocked/failed (or returns nothing usable):
 *   - fall back to existing local mapping (if any).
 *
 * Behavior
 *
 * Step 1 — Normalize barcode
 * - Input can include whitespace or formatting; normalization strips to digits-only.
 * - Blank after normalization → Blocked.
 *
 * Step 2 — Read existing mapping (once)
 * - Used for:
 *   - preserving assignedAt when already USDA-owned
 *   - publishedDate refresh gating
 *   - fallback source reporting
 *
 * Step 3 — Try USDA import first (authoritative)
 * - Delegates to [ImportUsdaFoodByBarcodeUseCase].
 * - If success:
 *   - Decide whether USDA publishedDate indicates a “refresh” compared to existing mapping.
 *     - publishedDate is the primary version gate (ISO yyyy-MM-dd lexicographic compare).
 *   - Upsert mapping with:
 *     - source=USDA
 *     - foodId = imported/resolved USDA foodId
 *     - usdaFdcId
 *     - usdaPublishedDateIso (gated)
 *     - assignedAtEpochMs semantics (preserve if already USDA)
 *     - lastSeenAtEpochMs updated
 *   - Return Resolved(foodId, barcode, USDA).
 *
 * Step 4 — Fallback to local mapping
 * - If USDA is blocked/failed:
 *   - resolve from local barcode mapping.
 *   - if none: NotFound.
 *   - touch lastSeen.
 *   - return Resolved(foodId, barcode, mapping source).
 *
 * Parameters
 * - rawBarcode:
 *   Raw barcode string from scanner/manual entry.
 *
 * - nowEpochMs:
 *   Timestamp for assignedAt/lastSeen updates.
 *
 * Return
 * - [Result.Resolved]
 *   Successfully resolved a local foodId (from USDA or local mapping).
 *
 * - [Result.NotFound]
 *   No USDA result and no local mapping.
 *
 * - [Result.Blocked]
 *   Invalid input (e.g., blank barcode after normalization).
 *
 * Edge cases
 * - USDA success may return “no refresh needed” (publishedDate not newer). We still:
 *   - set mapping to USDA ownership (if not already),
 *   - update lastSeen,
 *   - preserve stored publishedDate if newer/known.
 * - Local fallback source is reported from the mapping; if mapping is missing at read time,
 *   default source to USER_ASSIGNED for reporting.
 *
 * Pitfalls / gotchas
 * - “Refresh gate” here is only about mapping metadata and the decision signal. The actual import is executed
 *   by ImportUsdaFoodByBarcodeUseCase before we get the result.
 * - This use case writes barcode mappings. Do not call it in contexts that must remain read-only.
 *
 * Architectural rules
 * - USDA is authoritative and may overwrite USER_ASSIGNED mappings.
 * - Snapshot logs are immutable and must not rejoin foods. This use case only resolves foodId and updates mappings;
 *   it must not mutate historical log rows or snapshot foods.
 * - ISO-date model: USDA publishedDateIso is treated as ISO yyyy-MM-dd and compared lexicographically.
 */
class ResolveFoodIdForBarcodeUseCase @Inject constructor(
    private val importUsdaFoodByBarcode: ImportUsdaFoodByBarcodeUseCase,
    private val foods: FoodRepository,
    private val barcodes: FoodBarcodeRepository,
) {
    suspend operator fun invoke(rawBarcode: String, nowEpochMs: Long = System.currentTimeMillis()): Result {
        val barcode = normalizeDigits(rawBarcode)
        if (barcode.isBlank()) return Result.Blocked("Blank barcode")

        // Read existing mapping once (used for assignedAt preservation + publishedDate gate).
        val existing = barcodes.getByBarcode(barcode)

        // 1) Try USDA first (authoritative).
        when (val usda = importUsdaFoodByBarcode(barcode)) {
            is ImportUsdaFoodByBarcodeUseCase.Result.Success -> {
                val newPublished = usda.publishedDateIso?.trim()?.takeIf { it.isNotBlank() }
                val oldPublished = existing?.usdaPublishedDateIso?.trim()?.takeIf { it.isNotBlank() }

                // Refresh gate:
                // - Only consider "newer" if USDA publishedDate exists and is strictly greater than stored.
                // - ISO yyyy-MM-dd compares lexicographically correctly.
                val needsRefresh = when {
                    newPublished == null -> false
                    oldPublished == null -> true
                    else -> newPublished > oldPublished
                }

                // assignedAt semantics:
                // - If mapping already USDA → preserve assignedAt (ownership unchanged).
                // - If mapping was USER_ASSIGNED (or absent) and now USDA wins → assignedAt becomes now (ownership upgrade).
                val assignedAt = when {
                    existing == null -> nowEpochMs
                    existing.source == BarcodeMappingSource.USDA -> existing.assignedAtEpochMs
                    else -> nowEpochMs
                }

                // Always ensure mapping points to USDA food and lastSeen updates.
                // Only update publishedDate on mapping if:
                // - we need refresh (strictly newer), OR
                // - there was no stored publishedDate yet.
                val publishedToStore = when {
                    needsRefresh -> newPublished
                    oldPublished == null -> newPublished
                    else -> oldPublished
                }

                val entity = FoodBarcodeEntity(
                    barcode = barcode,
                    foodId = usda.foodId,
                    source = BarcodeMappingSource.USDA,
                    usdaFdcId = usda.fdcId,
                    usdaPublishedDateIso = publishedToStore,
                    assignedAtEpochMs = assignedAt,
                    lastSeenAtEpochMs = nowEpochMs,
                )

                barcodes.upsert(entity)

                // NOTE:
                // - Food refresh/persistence occurs inside the USDA import path.
                // - The needsRefresh signal remains available if you later short-circuit expensive work upstream.
                //   Currently ImportUsdaFoodByBarcodeUseCase has already executed before we reach this point.
                return Result.Resolved(
                    foodId = usda.foodId,
                    normalizedBarcode = barcode,
                    source = BarcodeMappingSource.USDA
                )
            }

            is ImportUsdaFoodByBarcodeUseCase.Result.Blocked -> {
                // Fall through to local mapping.
            }

            is ImportUsdaFoodByBarcodeUseCase.Result.Failed -> {
                // Fall through to local mapping.
            }
        }

        // 2) Local fallback.
        val mappedFoodId = barcodes.getFoodIdForBarcode(barcode)
            ?: return Result.NotFound(normalizedBarcode = barcode)

        barcodes.touchLastSeen(barcode, nowEpochMs)

        val mapping = existing ?: barcodes.getByBarcode(barcode)
        return Result.Resolved(
            foodId = mappedFoodId,
            normalizedBarcode = barcode,
            source = mapping?.source ?: BarcodeMappingSource.USER_ASSIGNED
        )
    }

    /**
     * Result of resolving a barcode to a food.
     *
     * Resolved
     * - A foodId was found (via USDA or local mapping).
     *
     * NotFound
     * - No USDA candidate and no local mapping.
     *
     * Blocked
     * - Invalid input (e.g., blank barcode after normalization).
     */
    sealed class Result {
        data class Resolved(
            val foodId: Long,
            val normalizedBarcode: String,
            val source: BarcodeMappingSource,
        ) : Result()

        data class NotFound(val normalizedBarcode: String) : Result()
        data class Blocked(val reason: String) : Result()
    }

    /**
     * Normalizes barcode input into canonical digits-only form.
     *
     * Required because repository storage and lookup assume a normalized numeric barcode.
     * Preserves leading zeros.
     */
    private fun normalizeDigits(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val sb = StringBuilder(trimmed.length)
        for (c in trimmed) if (c in '0'..'9') sb.append(c)
        return sb.toString()
    }
}

/**
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Invariants (must never change)
 * - Barcode normalization must happen before any repository lookup/write.
 * - USDA resolution is attempted before local fallback.
 * - USDA is authoritative: USDA success must upsert mapping as source=USDA and point to USDA-backed foodId.
 * - USER_ASSIGNED mappings may be overwritten by USDA when USDA recognizes the barcode.
 * - publishedDateIso is the version gate stored on mappings (ISO yyyy-MM-dd lexicographic compare).
 * - lastSeenAtEpochMs must be updated on successful resolution paths.
 *
 * Do not refactor notes
 * - Do not remove the initial single read of `existing`; assignedAt semantics depend on it.
 * - Do not change assignedAt semantics:
 *   - preserve when already USDA-owned
 *   - set to now when ownership upgrades to USDA
 * - Do not change publishedToStore rules without auditing ImportUsdaFoodByBarcodeUseCase and ResolveBarcodeWithUsdaUseCase.
 * - Keep local fallback behavior: return NotFound when no mapping exists.
 *
 * Architectural boundaries
 * - This use case is an orchestration boundary and IS allowed to write barcode mappings.
 * - It must NOT modify logs or snapshot foods (snapshot logs are immutable and must not rejoin foods).
 * - Food persistence/refresh belongs to USDA import use cases, not here.
 *
 * Migration notes (KMP / time)
 * - Uses System.currentTimeMillis() and epoch-ms timestamps; KMP-safe but consider injecting a Clock for tests.
 *
 * Performance considerations
 * - Reads mapping once up front; local fallback may perform one extra read if `existing` was null.
 * - USDA import path is the expensive part; mapping updates are small and deterministic.
 *
 * Maintenance recommendations
 * - Consider removing the unused `foods: FoodRepository` dependency if truly unused (future refactor only).
 * - If you later add in-session caching, keep the precedence rules identical.
 */