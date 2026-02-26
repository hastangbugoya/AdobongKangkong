package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.domain.usda.model.CollisionReason
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Resolves a barcode + chosen USDA candidate into a single next action: import, open existing, or prompt collision UI.
 *
 * Purpose
 * - Decide what should happen after a barcode scan returns (or the user selects) a USDA candidate:
 *   - Proceed with importing USDA data into the local DB,
 *   - Open an already-mapped local food,
 *   - Or prompt the user to resolve a collision between existing mapping and incoming USDA identity.
 *
 * Rationale (why this use case exists)
 * - Barcode-to-food identity is a shared, cross-feature concern (logging, food editor, USDA import, planner).
 * - Barcode mappings can be created in multiple ways:
 *   - USER_ASSIGNED (manual assignment when USDA has no match)
 *   - USDA (authoritative import)
 * - When USDA later recognizes a barcode that a user previously assigned manually, the app must not silently overwrite:
 *   the user should be prompted because this changes what “scan barcode” will return going forward.
 * - USDA may also change records over time; we need a version gate so repeated scans do not cause unnecessary imports.
 * - Centralizing this decision logic prevents drift across UI flows and keeps policies consistent.
 *
 * What needs resolving
 * - The incoming USDA candidate has an identity (fdcId + published/modified dates).
 * - The barcode may already be mapped locally (FoodBarcodeRepository is the source of truth).
 * - The system must resolve:
 *   1) Whether this barcode is unmapped (simple import),
 *   2) Whether a USER_ASSIGNED mapping exists (collision),
 *   3) Whether a USDA mapping exists (version gate + mismatch checks),
 *   4) Whether the incoming USDA candidate is actually “newer” than what we already have.
 *
 * Decision flow (high-level)
 *
 * Step 0 — Validate inputs
 * - Blank barcode → Blocked
 * - Invalid incoming fdcId → Blocked
 *
 * Step 1 — Lookup existing mapping
 * - If no mapping exists:
 *   → ProceedToImport (safe; no identity conflict)
 *
 * Step 2 — Branch by mapping source
 *
 * A) Existing source == USER_ASSIGNED
 * - Meaning: user manually mapped this barcode to a local food.
 * - Incoming USDA candidate now exists, so identity is ambiguous:
 *   → NeedsCollisionPrompt(reason = ExistingUserAssignedMapping)
 *
 * B) Existing source == USDA
 * - Meaning: barcode is already owned by an authoritative USDA mapping.
 * - Two sub-problems must be resolved:
 *
 *   B1) Identity mismatch (fdcId mismatch)
 *   - If stored usdaFdcId is null OR differs from incoming fdcId:
 *     → NeedsCollisionPrompt(reason = ExistingUsdaFdcIdMismatch)
 *   - Rationale:
 *     - This is a serious identity conflict: two different USDA foods claim the same barcode.
 *     - Must be user-visible and handled explicitly.
 *
 *   B2) Freshness / version gate (same fdcId)
 *   - If fdcId matches, decide whether importing again is needed.
 *   - Primary gate: publishedDateIso (yyyy-MM-dd)
 *     - Missing or unparseable dates:
 *         → OpenExisting(reason = ExistingUsdaNoDateConservative)
 *         (Conservative policy: do not overwrite when uncertain.)
 *     - incoming publishedDate > existing publishedDate:
 *         → ProceedToImport (newer authoritative version)
 *     - incoming publishedDate < existing publishedDate:
 *         → OpenExisting(reason = ExistingUsdaUpToDate)
 *     - publishedDate equal:
 *         Tie-breaker: modifiedDateIso
 *         - If both parse and incoming modifiedDate is newer:
 *             → ProceedToImport
 *         - Else:
 *             → OpenExisting(reason = ExistingUsdaUpToDate)
 *
 * Parameters
 * - barcode:
 *   Raw barcode string (expected to already be digits-only in most flows, but trimmed here).
 *
 * - incoming:
 *   Metadata for the chosen USDA candidate (fdcId + dates + display fields).
 *
 * Return
 * - [Result.ProceedToImport]
 *   Caller should execute the import pipeline for the selected candidate.
 *
 * - [Result.OpenExisting]
 *   Caller should open the currently mapped local food (no import needed).
 *
 * - [Result.NeedsCollisionPrompt]
 *   Caller must show a collision resolution UI (remap/open/cancel) based on [CollisionReason].
 *
 * - [Result.Blocked]
 *   Caller should show message and stop.
 *
 * Edge cases
 * - Missing/unparseable publishedDate → conservative no-overwrite.
 * - Modified date is only a tie-breaker when publishedDate is equal.
 * - USDA mapping with missing stored usdaFdcId is treated as mismatch (collision prompt).
 *
 * Pitfalls / gotchas
 * - This use case does not normalize the barcode to digits-only; it only trims.
 *   Upstream scanning flows should normalize before calling if required by DB storage rules.
 * - LocalDate.parse assumes ISO yyyy-MM-dd; anything else becomes null and triggers conservative behavior.
 * - “OpenExistingUpToDate” means “do not import”; it does NOT guarantee the local food has every nutrient you expect.
 *
 * Architectural rules (if applicable)
 * - Pure decision layer:
 *   - No navigation.
 *   - No DB writes (read-only lookup via repository).
 *   - No UI state mutation.
 * - FoodBarcodeRepository is the single source of truth for barcode ownership.
 * - Snapshot logs are immutable and must not rejoin foods; this use case must not touch logs.
 * - ISO-date-based model: logDateIso is authoritative elsewhere; here we only compare USDA ISO dates.
 */
class ResolveBarcodeWithUsdaUseCase @Inject constructor(
    private val barcodes: FoodBarcodeRepository
) {

    suspend fun resolveCandidateChosen(
        barcode: String,
        incoming: UsdaBarcodeCandidateMeta
    ): Result {
        val code = barcode.trim()
        if (code.isBlank()) return Result.Blocked("Blank barcode")
        if (incoming.fdcId <= 0L) return Result.Blocked("Invalid incoming fdcId=${incoming.fdcId}")

        val existing = barcodes.getByBarcode(code) ?: return Result.ProceedToImport(
            barcode = code,
            chosen = incoming
        )

        return when (existing.source) {
            BarcodeMappingSource.USER_ASSIGNED -> {
                Result.NeedsCollisionPrompt(
                    barcode = code,
                    existingFoodId = existing.foodId,
                    existingSource = existing.source,
                    incoming = incoming,
                    reason = CollisionReason.ExistingUserAssignedMapping
                )
            }

            BarcodeMappingSource.USDA -> {
                val existingFdcId = existing.usdaFdcId
                if (existingFdcId == null || existingFdcId != incoming.fdcId) {
                    Result.NeedsCollisionPrompt(
                        barcode = code,
                        existingFoodId = existing.foodId,
                        existingSource = existing.source,
                        incoming = incoming,
                        reason = CollisionReason.ExistingUsdaFdcIdMismatch
                    )
                } else {
                    // Freshness compare
                    val existingPub = parseIsoDate(existing.usdaPublishedDateIso)
                    val incomingPub = parseIsoDate(incoming.publishedDateIso)

                    // Conservative policy: missing/unparseable published dates => do NOT overwrite
                    if (existingPub == null || incomingPub == null) {
                        Result.OpenExisting(
                            barcode = code,
                            foodId = existing.foodId,
                            reason = OpenReason.ExistingUsdaNoDateConservative
                        )
                    } else if (incomingPub.isAfter(existingPub)) {
                        Result.ProceedToImport(
                            barcode = code,
                            chosen = incoming
                        )
                    } else if (incomingPub.isBefore(existingPub)) {
                        Result.OpenExisting(
                            barcode = code,
                            foodId = existing.foodId,
                            reason = OpenReason.ExistingUsdaUpToDate
                        )
                    } else {
                        // published equal -> Rule C: compare modifiedDateIso (only as tie-breaker)
                        val existingMod = parseIsoDate(existing.usdaModifiedDateIso)
                        val incomingMod = parseIsoDate(incoming.modifiedDateIso)

                        if (existingMod != null && incomingMod != null && incomingMod.isAfter(existingMod)) {
                            Result.ProceedToImport(
                                barcode = code,
                                chosen = incoming
                            )
                        } else {
                            Result.OpenExisting(
                                barcode = code,
                                foodId = existing.foodId,
                                reason = OpenReason.ExistingUsdaUpToDate
                            )
                        }
                    }
                }
            }
        }
    }

    private fun parseIsoDate(iso: String?): LocalDate? {
        val s = iso?.trim().takeIf { !it.isNullOrBlank() } ?: return null
        return try {
            LocalDate.parse(s)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /**
     * Minimal metadata about a USDA search candidate needed to make a collision/import decision.
     *
     * Notes
     * - This is intentionally not the full USDA object; it is the smallest stable set of fields needed for:
     *   - identity checks (fdcId)
     *   - version gating (published/modified)
     *   - UI display during collision prompts (description/brand)
     */
    data class UsdaBarcodeCandidateMeta(
        val fdcId: Long,
        val gtinUpc: String?,
        val publishedDateIso: String?, // yyyy-MM-dd
        val modifiedDateIso: String?,
        val description: String?,
        val brand: String?
    )

    /**
     * Decision result describing the single next step the caller must take.
     *
     * - OpenExisting: show the currently mapped local food (no import needed).
     * - NeedsCollisionPrompt: show UI to resolve mapping conflict.
     * - ProceedToImport: import selected USDA candidate.
     * - Blocked: invalid input.
     */
    sealed class Result {
        data class OpenExisting(
            val barcode: String,
            val foodId: Long,
            val reason: OpenReason
        ) : Result()

        data class NeedsCollisionPrompt(
            val barcode: String,
            val existingFoodId: Long,
            val existingSource: BarcodeMappingSource,
            val incoming: UsdaBarcodeCandidateMeta,
            val reason: CollisionReason
        ) : Result()

        data class ProceedToImport(
            val barcode: String,
            val chosen: UsdaBarcodeCandidateMeta
        ) : Result()

        data class Blocked(val reason: String) : Result()
    }

    /**
     * Reason codes for “OpenExisting” decisions.
     *
     * ExistingUsdaUpToDate
     * - Existing USDA mapping is current under publishedDate/modifiedDate rules.
     *
     * ExistingUsdaNoDateConservative
     * - Missing/unparseable dates → conservative policy: do not overwrite.
     */
    enum class OpenReason {
        ExistingUsdaUpToDate,
        ExistingUsdaNoDateConservative
    }
}

/**
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Invariants (must never change)
 * - FoodBarcodeRepository is the single source of truth for barcode ownership.
 * - This use case is read-only (no writes, no navigation, no UI mutation).
 * - Collision policy is locked:
 *   - USER_ASSIGNED existing mapping → NeedsCollisionPrompt(ExistingUserAssignedMapping).
 *   - USDA existing mapping + fdcId mismatch → NeedsCollisionPrompt(ExistingUsdaFdcIdMismatch).
 *   - USDA existing mapping + same fdcId → version gate decision.
 * - Version gating rules are locked:
 *   - publishedDateIso is primary gate.
 *   - modifiedDateIso is tie-breaker only when published dates are equal.
 *   - missing/unparseable date → conservative OpenExisting (do not overwrite).
 *
 * Do not refactor notes
 * - Do not change date parsing behavior (LocalDate.parse ISO yyyy-MM-dd) without updating all USDA date sources.
 * - Do not switch to lexicographic compare here unless you standardize all date inputs first.
 * - Do not add barcode digit-normalization here unless you also update all callers and repository storage assumptions.
 *
 * Architectural boundaries
 * - This use case decides; it never imports. Import is performed elsewhere (ImportUsdaFoodByBarcodeUseCase / ImportUsdaFoodFromSearchJsonUseCase).
 * - This use case never modifies FoodBarcodeEntity mappings; remap flows must be explicit user actions.
 * - Snapshot logs are immutable and must not rejoin foods; do not let collision handling modify historical data.
 *
 * Migration notes (KMP / time)
 * - Uses java.time.LocalDate; if migrating to KMP, replace with a platform abstraction or kotlinx-datetime.
 *
 * Performance considerations
 * - Single repository lookup.
 * - Date parsing is trivial and bounded.
 *
 * Maintenance recommendations
 * - Keep CollisionReason exhaustive and aligned with UI prompt options.
 * - If USDA introduces a stronger version signal than publishedDate, add it only after auditing all import flows.
 */