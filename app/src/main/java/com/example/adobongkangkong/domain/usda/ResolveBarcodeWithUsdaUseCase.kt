package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.domain.usda.model.CollisionReason
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

/**
 * Pure decision layer for barcode + USDA candidate selection.
 *
 * This use case:
 * - Reads existing mapping from FoodBarcodeRepository (single source of truth).
 * - Applies locked rules to decide whether to:
 *   - import (ProceedToImport),
 *   - open existing mapping (OpenExisting),
 *   - or prompt collision UI (NeedsCollisionPrompt).
 *
 * IMPORTANT:
 * - No navigation.
 * - No DB writes (other than reading mappings).
 * - No UI state mutation.
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

    data class UsdaBarcodeCandidateMeta(
        val fdcId: Long,
        val gtinUpc: String?,
        val publishedDateIso: String?, // yyyy-MM-dd
        val modifiedDateIso: String?,
        val description: String?,
        val brand: String?
    )

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

    enum class OpenReason {
        ExistingUsdaUpToDate,
        ExistingUsdaNoDateConservative
    }
}