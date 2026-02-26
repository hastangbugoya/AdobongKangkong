package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import javax.inject.Inject

/**
 * Assigns a barcode to an existing food via the USER_ASSIGNED path when no USDA match exists.
 *
 * Purpose
 * - Allow users to manually link a physical barcode (UPC/EAN/GTIN) to an existing food in the database
 *   when USDA or other authoritative sources do not provide a match.
 *
 * Rationale (why this use case exists)
 * - Barcode scans may return no USDA candidates (private-label foods, regional products, supplements,
 *   or custom-prepared foods).
 * - Users still need a fast scan → log workflow. Without manual assignment, every scan would require
 *   repeated manual lookup.
 * - USER_ASSIGNED mappings provide a local override that enables immediate reuse of the barcode.
 *
 * Authority hierarchy
 * - USDA mappings are authoritative and must not be overridden by manual assignment.
 * - USER_ASSIGNED mappings are considered provisional and may later be replaced if a verified USDA mapping appears.
 *
 * Flow: assigning a non-USDA barcode to an existing food
 *
 * Step 1 — Normalize input
 * - Strip whitespace and non-digit characters.
 * - Ensures consistent canonical storage regardless of scanner formatting.
 *
 * Step 2 — Validate basic constraints
 * - Blank barcode → Blocked.
 * - Invalid foodId (<= 0) → Blocked.
 *
 * Step 3 — Check existing mapping ownership
 * - If barcode already exists and source == USDA:
 *   → Blocked.
 *   Rationale: USDA identity must remain authoritative and stable.
 *
 * - If barcode exists and source == USER_ASSIGNED:
 *   → Allowed (upsert will update or reaffirm mapping).
 *
 * - If barcode does not exist:
 *   → Allowed.
 *
 * Step 4 — Upsert mapping via [UpsertBarcodeMappingUseCase]
 * - Stores:
 *   - barcode
 *   - foodId
 *   - source = USER_ASSIGNED
 *   - timestamp
 * - USDA metadata fields remain null.
 *
 * Step 5 — Return result
 * - Success → barcode and foodId linked.
 * - Blocked → caller must inform user and stop.
 *
 * Behavior guarantees
 * - Never overwrites USDA-owned barcode mappings.
 * - Always normalizes barcode before persistence.
 * - Delegates all persistence logic to [UpsertBarcodeMappingUseCase].
 *
 * Parameters
 * - rawBarcode:
 *   Raw scanned or user-entered barcode string.
 *
 * - foodId:
 *   Existing food to assign barcode to.
 *
 * - nowEpochMs:
 *   Assignment timestamp used for auditing and conflict resolution.
 *
 * Return
 * - [Result.Success]
 *   Mapping successfully created or updated.
 *
 * - [Result.Blocked]
 *   User-actionable issue such as:
 *   - blank barcode
 *   - invalid foodId
 *   - barcode owned by USDA mapping
 *
 * Edge cases
 * - Barcode scanners sometimes include whitespace or formatting characters; normalization handles this.
 * - Leading zeros are preserved.
 * - Re-assigning a USER_ASSIGNED barcode to the same food is allowed (idempotent behavior).
 *
 * Pitfalls / gotchas
 * - This use case intentionally does NOT check if foodId exists; repository layer should enforce referential integrity.
 * - USDA mappings must never be overridden here; collision resolution happens in USDA import flows.
 *
 * Architectural rules
 * - Domain-level decision logic only; persistence delegated to repository/use case abstraction.
 * - Barcode ownership authority must remain consistent across all barcode flows.
 * - Snapshot log immutability rule: barcode mappings affect lookup only, never historical nutrition logs.
 */
class AssignBarcodeToFoodUseCase @Inject constructor(
    private val barcodes: FoodBarcodeRepository,
    private val upsert: UpsertBarcodeMappingUseCase,
) {
    suspend operator fun invoke(
        rawBarcode: String,
        foodId: Long,
        nowEpochMs: Long = System.currentTimeMillis()
    ): Result {
        val barcode = normalizeDigits(rawBarcode)
        if (barcode.isBlank()) return Result.Blocked("Blank barcode")
        if (foodId <= 0L) return Result.Blocked("Invalid foodId")

        val existing = barcodes.getByBarcode(barcode)
        if (existing?.source == BarcodeMappingSource.USDA) {
            return Result.Blocked("Barcode is already owned by USDA mapping; cannot user-assign.")
        }

        return when (val r = upsert(
            rawBarcode = barcode,
            foodId = foodId,
            source = BarcodeMappingSource.USER_ASSIGNED,
            nowEpochMs = nowEpochMs,
            usdaFdcId = null,
            usdaPublishedDateIso = null,
        )) {
            is UpsertBarcodeMappingUseCase.Result.Success ->
                Result.Success(barcode = r.barcode, foodId = r.foodId)

            is UpsertBarcodeMappingUseCase.Result.Blocked ->
                Result.Blocked(r.reason)
        }
    }

    sealed class Result {
        data class Success(val barcode: String, val foodId: Long) : Result()
        data class Blocked(val reason: String) : Result()
    }

    /**
     * Normalizes barcode input into canonical numeric form.
     *
     * Purpose
     * - Ensure barcode storage and lookup use consistent formatting.
     *
     * Behavior
     * - Trims whitespace.
     * - Removes all non-digit characters.
     * - Preserves digit order and leading zeros.
     *
     * Examples
     * - " 01234-56789 " → "0123456789"
     * - "UPC: 12345 67890" → "1234567890"
     *
     * Edge cases
     * - Empty or non-numeric input → returns empty string.
     *
     * Architectural rule
     * - Barcode normalization must remain deterministic and locale-independent.
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
 * - USDA barcode mappings are authoritative and must never be overwritten here.
 * - All stored barcodes must be normalized digits-only form.
 * - Persistence must be delegated to UpsertBarcodeMappingUseCase.
 * - USER_ASSIGNED mappings may be replaced later by USDA import flows.
 *
 * Authority model
 * - USDA source > USER_ASSIGNED source
 * - USER_ASSIGNED exists to support offline/manual workflows.
 *
 * Architectural boundaries
 * - This use case performs validation and ownership checks only.
 * - Repository handles persistence.
 * - UpsertBarcodeMappingUseCase handles insert/update logic.
 *
 * Logging and snapshot rules
 * - Barcode mapping affects lookup resolution only.
 * - Existing nutrition logs and snapshot foods must never be modified.
 *
 * Migration notes (KMP)
 * - normalizeDigits is platform-safe and requires no changes.
 *
 * Performance considerations
 * - Single repository lookup and single upsert call.
 * - Normalization runs in O(n) on barcode length.
 *
 * Maintenance recommendations
 * - If future barcode formats require alphanumeric support, normalization rules must be expanded carefully.
 * - Consider centralizing normalization logic if multiple barcode use cases need identical behavior.
 */