package com.example.adobongkangkong.domain.usda.model

sealed class MappingPolicy {
    data class UpsertUsdaMapping(
        val usdaFdcId: Long,
        val usdaPublishedDateIso: String?
    ) : MappingPolicy()

    data object KeepExistingMappingOnlyTouchLastSeen : MappingPolicy()
}