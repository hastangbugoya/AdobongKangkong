package com.example.adobongkangkong.domain.usda.model

sealed class BarcodeResolutionRequest {
    data class ScanWithUsdaCandidates(
        val barcode: String,                       // normalized
        val candidates: List<UsdaBarcodeCandidateMeta>
    ) : BarcodeResolutionRequest()

    data class CandidateChosen(
        val barcode: String,                       // normalized
        val chosen: UsdaBarcodeCandidateMeta
    ) : BarcodeResolutionRequest()
}