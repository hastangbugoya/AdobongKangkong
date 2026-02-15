package com.example.adobongkangkong.domain.usda.model

import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource

sealed class ResolveBarcodeWithUsdaResult {

    /**
     * We already have a mapping and should open it; no import.
     * Can be used for:
     * - USDA mapping where incoming is not newer
     * - Any mapping when user chose Open Existing
     */
    data class OpenExisting(
        val barcode: String,
        val foodId: Long,
        val reason: OpenReason
    ) : ResolveBarcodeWithUsdaResult()

    /**
     * We have USDA candidates but need the UI to show a picker.
     * (Only when it's valid to proceed with selection.)
     */
    data class ShowPicker(
        val barcode: String,
        val candidates: List<UsdaBarcodeCandidateMeta>,
        val reason: PickerReason
    ) : ResolveBarcodeWithUsdaResult()

    /**
     * We must prompt because barcode is mapped to a USER_ASSIGNED food,
     * or because we detected a risky USDA mismatch (e.g., fdcId drift).
     *
     * UI will show: Remap / Open Existing / Cancel.
     */
    data class NeedsCollisionPrompt(
        val barcode: String,
        val existingFoodId: Long,
        val existingSource: BarcodeMappingSource,          // USER_ASSIGNED or USDA (mismatch case)
        val incoming: UsdaBarcodeCandidateMeta?,           // null allowed when no USDA data (optional future)
        val reason: CollisionReason
    ) : ResolveBarcodeWithUsdaResult()

    /**
     * We should import/update the chosen USDA item, then write mapping.
     * Resolver does not do it; VM coordinates import + mapping write.
     */
    data class ProceedToImport(
        val barcode: String,
        val chosen: UsdaBarcodeCandidateMeta,
        val importPolicy: ImportPolicy,
        val mappingPolicy: MappingPolicy
    ) : ResolveBarcodeWithUsdaResult()

    /**
     * Resolver blocks the flow (rare): invalid barcode, empty candidates, etc.
     * UI should show fallback dialog or error.
     */
    data class Blocked(
        val barcode: String,
        val reason: String
    ) : ResolveBarcodeWithUsdaResult()
}