package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import javax.inject.Inject

/**
 * Upserts a barcode mapping row.
 *
 * - PK is barcode, so upsert replaces any previous mapping (needed for USDA overwrite).
 * - Keeps assignedAtEpochMs stable when re-upserting the same mapping.
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
            existing.foodId == foodId && existing.source == source -> existing.assignedAtEpochMs
            else -> nowEpochMs // mapping changed (overwrite), treat as new assignment
        }

        val entity = FoodBarcodeEntity(
            barcode = barcode,
            foodId = foodId,
            source = source,
            usdaFdcId = if (source == BarcodeMappingSource.USDA) usdaFdcId else null,
            usdaPublishedDateIso = if (source == BarcodeMappingSource.USDA) usdaPublishedDateIso else null,
            assignedAtEpochMs = assignedAt,
            lastSeenAtEpochMs = nowEpochMs,
        )

        barcodes.upsert(entity)

        return Result.Success(
            barcode = barcode,
            foodId = foodId,
            source = source,
        )
    }

    sealed class Result {
        data class Success(val barcode: String, val foodId: Long, val source: BarcodeMappingSource) : Result()
        data class Blocked(val reason: String) : Result()
    }

    private fun normalizeDigits(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val sb = StringBuilder(trimmed.length)
        for (c in trimmed) if (c in '0'..'9') sb.append(c)
        return sb.toString()
    }
}
