package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.domain.repository.FoodBarcodeRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

/**
 * Barcode resolution precedence:
 * 1) Normalize digits
 * 2) Try USDA import
 *    - if success: ensure mapping points to USDA-backed food (USDA wins, overwrites USER_ASSIGNED)
 *    - store usdaFdcId + usdaPublishedDateIso on mapping (publishedDate is the version gate)
 *    - refresh gate: if USDA publishedDate is not newer than stored, treat as "no refresh needed"
 *      (we still update lastSeen + ensure mapping points to USDA food)
 * 3) If USDA returns nothing/blocked/failed: fallback to local mapping (if present)
 */
class ResolveFoodIdForBarcodeUseCase @Inject constructor(
    private val importUsdaFoodByBarcode: ImportUsdaFoodByBarcodeUseCase,
    private val foods: FoodRepository,
    private val barcodes: FoodBarcodeRepository,
) {
    suspend operator fun invoke(rawBarcode: String, nowEpochMs: Long = System.currentTimeMillis()): Result {
        val barcode = normalizeDigits(rawBarcode)
        if (barcode.isBlank()) return Result.Blocked("Blank barcode")

        // Read existing mapping once (used for assignedAt preservation + refresh gate)
        val existing = barcodes.getByBarcode(barcode)

        // 1) Try USDA first
        when (val usda = importUsdaFoodByBarcode(barcode)) {
            is ImportUsdaFoodByBarcodeUseCase.Result.Success -> {
                val newPublished = usda.publishedDateIso?.trim()?.takeIf { it.isNotBlank() }
                val oldPublished = existing?.usdaPublishedDateIso?.trim()?.takeIf { it.isNotBlank() }

                // Refresh gate: newPublished must be strictly newer than oldPublished.
                // ISO yyyy-MM-dd compares lexicographically correctly.
                val needsRefresh = when {
                    newPublished == null -> false
                    oldPublished == null -> true
                    else -> newPublished > oldPublished
                }

                // assignedAt semantics:
                // - If mapping already USDA -> preserve assignedAt.
                // - If mapping was USER_ASSIGNED (or absent) and now USDA wins -> assignedAt becomes now.
                val assignedAt = when {
                    existing == null -> nowEpochMs
                    existing.source == BarcodeMappingSource.USDA -> existing.assignedAtEpochMs
                    else -> nowEpochMs
                }

                // Always ensure mapping points to USDA food and lastSeen updates.
                // Only update publishedDate on mapping if:
                // - we need refresh, OR
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
                // - The actual Food refresh (foods table fields) should happen inside the USDA import path.
                // - The "needsRefresh" signal exists here if you later want to avoid expensive work.
                //   Currently, ImportUsdaFoodByBarcodeUseCase already ran before we get here.
                return Result.Resolved(
                    foodId = usda.foodId,
                    normalizedBarcode = barcode,
                    source = BarcodeMappingSource.USDA
                )
            }

            is ImportUsdaFoodByBarcodeUseCase.Result.Blocked -> {
                // fall through to local mapping
            }

            is ImportUsdaFoodByBarcodeUseCase.Result.Failed -> {
                // fall through to local mapping
            }
        }

        // 2) Local fallback
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

    sealed class Result {
        data class Resolved(
            val foodId: Long,
            val normalizedBarcode: String,
            val source: BarcodeMappingSource,
        ) : Result()

        data class NotFound(val normalizedBarcode: String) : Result()
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
