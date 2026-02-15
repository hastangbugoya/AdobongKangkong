package com.example.adobongkangkong.domain.usda.model

/**
 * How to treat the import relative to existing FoodEntity.
 */
sealed class ImportPolicy {
    data object InsertOrReviveByFdcId : ImportPolicy()   // your current behavior
    data object ForceRefresh : ImportPolicy()            // only if you later add special refresh semantics
}