package com.example.adobongkangkong.domain.usda.model

data class UsdaBarcodeCandidateMeta(
    val fdcId: Long,
    val gtinUpc: String?,              // optional but useful for logging/trace
    val publishedDateIso: String?,     // yyyy-MM-dd
    val modifiedDateIso: String?,      // yyyy-MM-dd (optional future use)
    val description: String? = null,   // optional for UI prompt text
    val brand: String? = null          // optional for UI prompt text
)