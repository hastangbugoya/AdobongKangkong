package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import javax.inject.Inject

/**
 * Upserts a barcode → food mapping row, enforcing canonical identity and ownership rules.
 *
 * Purpose
 * - Persist or update the mapping between a normalized barcode and a local FoodEntity.
 * - Ensure barcode identity remains stable while allowing authoritative sources (USDA) to overwrite manual mappings.
 *
 * Why FoodBarcodeEntity exists (core architectural rationale)
 *
 * The barcode is NOT stored directly on FoodEntity because:
 *
 * 1) One food may have multiple barcodes
 *    Example:
 *      Same product sold in different package sizes or regions.
 *
 * 2) Barcode identity and food identity evolve independently
 *    - USER_ASSIGNED mapping may exist before USDA recognizes the barcode.
 *    - Later USDA import upgrades ownership and metadata.
 *
 * 3) Barcode mappings carry metadata not appropriate for FoodEntity:
 *    - source (USER_ASSIGNED vs USDA)
 *    - usdaFdcId (authoritative identity link)
 *    - usdaPublishedDateIso (version gate)
 *    - assignedAtEpochMs (ownership history)
 *    - lastSeenAtEpochMs (scan telemetry)
 *
 * 4) Logging and snapshot invariants require stable FoodEntity identity
 *    - Snapshot logs reference FoodEntity.id, not barcode.
 *    - Barcode mappings must be safely replaceable without affecting historical logs.
 *
 * Therefore:
 *
 * BarcodeIdentity (FoodBarcodeEntity)
 *     barcode PK
 *         ↓
 *     foodId FK → FoodEntity
 *
 * This layer allows barcode ownership and mapping to change safely over time.
 *
 * Behavior
 *
 * Step 1 — Normalize barcode
 * - Removes whitespace and non-digit characters.
 * - Ensures canonical storage format.
 *
 * Step 2 — Validate inputs
 * - Blank barcode → Blocked
 * - Invalid foodId → Blocked
 *
 * Step 3 — Read existing mapping
 * Used to determine assignedAt semantics.
 *
 * Step 4 — Determine assignedAtEpochMs
 *
 * Cases:
 *
 * A) No existing mapping
 *    → assignedAt = now
 *
 * B) Same foodId AND same source
 *    → assignedAt preserved
 *
 * C) Mapping changed (foodId or source changed)
 *    → assignedAt = now
 *
 * This allows assignment history tracking while preserving continuity.
 *
 * Step 5 — Build mapping entity
 *
 * USDA-specific metadata stored ONLY when source == USDA:
 *
 * - usdaFdcId
 * - usdaPublishedDateIso
 *
 * USER_ASSIGNED mappings must not contain USDA metadata.
 *
 * Step 6 — Upsert mapping
 *
 * Primary key = barcode
 *
 * Upsert guarantees:
 *
 * - overwrite existing mapping safely
 * - preserve referential integrity
 *
 * Step 7 — Return success result
 *
 * Parameters
 * - rawBarcode:
 *   Raw scanned or entered barcode string.
 *
 * - foodId:
 *   Local FoodEntity id.
 *
 * - source:
 *   Ownership source of mapping.
 *
 * - nowEpochMs:
 *   Timestamp for assignment and lastSeen tracking.
 *
 * - usdaFdcId:
 *   USDA identity reference (only valid for USDA source).
 *
 * - usdaPublishedDateIso:
 *   USDA version gate.
 *
 * Return
 * - Success
 *   Mapping persisted.
 *
 * - Blocked
 *   Invalid input.
 *
 * Edge cases
 * - Existing USER_ASSIGNED mapping overwritten by USDA mapping.
 * - Existing USDA mapping refreshed.
 * - Mapping reassigned to a different foodId.
 *
 * Pitfalls / gotchas
 * - Barcode normalization must remain consistent with repository storage.
 * - assignedAt must not change unless mapping ownership changes.
 * - USDA metadata must never be stored on USER_ASSIGNED rows.
 *
 * Architectural rules
 * - Barcode mapping is the canonical identity bridge between scanner and FoodEntity.
 * - FoodEntity must remain barcode-agnostic.
 * - Snapshot logs must reference FoodEntity.id only.
 * - Barcode mappings must be replaceable without breaking historical logs.
 */
class UpsertBarcodeMappingUseCase @Inject constructor(
    private val barcodes: FoodBarcodeRepository
) {
    suspend operator fun invoke(
        rawBarcode: String,
        foodId: Long,
        source: BarcodeMappingSource,
        nowEpochMs: Long = System.currentTimeMillis(),
        usdaFdcId: Long? = null,
        usdaPublishedDateIso: String? = null,
    ): Result {
        val barcode = normalizeDigits(rawBarcode)
        if (barcode.isBlank()) return Result.Blocked("Blank barcode")
        if (foodId <= 0L) return Result.Blocked("Invalid foodId")

        val existing = barcodes.getByBarcode(barcode)

        val assignedAt = when {
            existing == null -> nowEpochMs
            existing.foodId == foodId && existing.source == source ->
                existing.assignedAtEpochMs
            else ->
                nowEpochMs // ownership or target food changed
        }

        val entity = FoodBarcodeEntity(
            barcode = barcode,
            foodId = foodId,
            source = source,

            // USDA metadata stored only for USDA mappings.
            usdaFdcId =
                if (source == BarcodeMappingSource.USDA)
                    usdaFdcId
                else null,

            usdaPublishedDateIso =
                if (source == BarcodeMappingSource.USDA)
                    usdaPublishedDateIso
                else null,

            assignedAtEpochMs = assignedAt,

            // Always update lastSeen when mapping touched.
            lastSeenAtEpochMs = nowEpochMs,
        )

        barcodes.upsert(entity)

        return Result.Success(
            barcode = barcode,
            foodId = foodId,
            source = source,
        )
    }

    /**
     * Result of mapping upsert operation.
     *
     * Success
     * - Mapping persisted.
     *
     * Blocked
     * - Invalid inputs prevented write.
     */
    sealed class Result {
        data class Success(
            val barcode: String,
            val foodId: Long,
            val source: BarcodeMappingSource
        ) : Result()

        data class Blocked(val reason: String) : Result()
    }

    /**
     * Normalizes barcode into canonical digits-only representation.
     *
     * This prevents duplicate mappings caused by formatting differences.
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
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Invariants (must never change)
 *
 * - FoodBarcodeEntity primary key is barcode.
 * - Upsert replaces entire mapping row.
 * - assignedAtEpochMs represents ownership assignment moment.
 * - lastSeenAtEpochMs represents last scan usage.
 *
 * Ownership model
 *
 * USER_ASSIGNED
 *     temporary / user-defined
 *
 * USDA
 *     authoritative mapping
 *
 * USDA may overwrite USER_ASSIGNED.
 *
 * USER_ASSIGNED must never overwrite USDA automatically.
 *
 * Identity model
 *
 * barcode → FoodBarcodeEntity → FoodEntity.id
 *
 * NOT:
 *
 * barcode → FoodEntity directly
 *
 * Logging safety rule
 *
 * Snapshot logs reference FoodEntity.id only.
 *
 * Barcode mappings must never modify historical logs.
 *
 * Do not refactor notes
 *
 * - Do not move barcode into FoodEntity.
 * - Do not remove normalizeDigits.
 * - Do not change assignedAt semantics.
 * - Do not store USDA metadata for USER_ASSIGNED mappings.
 *
 * Migration notes
 *
 * If barcode format expands beyond digits,
 * update normalizeDigits consistently across:
 *
 * ResolveFoodIdForBarcodeUseCase
 * AssignBarcodeToFoodUseCase
 * FoodBarcodeRepository
 *
 * Performance considerations
 *
 * Single indexed lookup and upsert.
 *
 * Recommended future improvements
 *
 * Consider injecting Clock instead of System.currentTimeMillis for deterministic testing.
 */