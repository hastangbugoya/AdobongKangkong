package com.example.adobongkangkong.domain.repository

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity

/**
 * Domain-facing access for persistent barcode -> food mappings.
 *
 * NOTE: This interface currently reuses the DB enum [BarcodeMappingSource] to keep v6 scope tight.
 * If you later want strict Clean Architecture boundaries, we can introduce a domain enum and map in the impl.
 */
interface FoodBarcodeRepository {

    /** Returns the full mapping row for a barcode, or null if unmapped. */
    suspend fun getByBarcode(normalizedBarcode: String): FoodBarcodeEntity?

    /** Returns the mapped foodId for a barcode, or null if unmapped. */
    suspend fun getFoodIdForBarcode(normalizedBarcode: String): Long?

    /** Insert or replace the mapping row (barcode is the PK; REPLACE enforces uniqueness). */
    suspend fun upsert(entity: FoodBarcodeEntity)

    /** Delete a mapping row for the barcode (if present). */
    suspend fun deleteByBarcode(normalizedBarcode: String)

    /** Updates lastSeenAtEpochMs for an existing barcode mapping (no-op if absent). */
    suspend fun touchLastSeen(normalizedBarcode: String, epochMs: Long)

    /** Count how many barcodes are mapped to a given foodId. */
    suspend fun countForFood(foodId: Long): Int

    /** List all mappings of a given source (USDA vs USER_ASSIGNED). */
    suspend fun getAllBySource(source: BarcodeMappingSource): List<FoodBarcodeEntity>

    /**
     * Convenience: upsert mapping but ensure lastSeen is updated to [nowEpochMs].
     * Matches DAO convenience method.
     */
    suspend fun upsertAndTouch(entity: FoodBarcodeEntity, nowEpochMs: Long)

    suspend fun getAllBarcodesForFood(foodId: Long): List<FoodBarcodeEntity>
}
