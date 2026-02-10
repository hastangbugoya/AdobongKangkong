package com.example.adobongkangkong.data.local.db.mapper

import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.domain.model.Food

internal fun Food.toEntity() = FoodEntity(
    id = id,
    name = name,
    brand = brand,
    stableId = stableId,
    servingSize = servingSize,
    servingUnit = servingUnit,
    gramsPerServingUnit = gramsPerServingUnit,
    mlPerServingUnit = mlPerServingUnit,
    servingsPerPackage = servingsPerPackage,
    isRecipe = isRecipe,
    isLowSodium = isLowSodium,
    usdaFdcId = usdaFdcId,
    usdaGtinUpc = usdaGtinUpc,
    usdaPublishedDate = usdaPublishedDate,
    usdaModifiedDate = usdaModifiedDate,
)

internal fun FoodEntity.toDomain() = Food(
    id = id,
    stableId = stableId,
    name = name,
    brand = brand,
    servingSize = servingSize,
    servingUnit = servingUnit,
    gramsPerServingUnit = gramsPerServingUnit,
    mlPerServingUnit = mlPerServingUnit,
    servingsPerPackage = servingsPerPackage,
    isRecipe = isRecipe,
    isLowSodium = isLowSodium,
    usdaFdcId = usdaFdcId,
    usdaGtinUpc = usdaGtinUpc,
    usdaPublishedDate = usdaPublishedDate,
    usdaModifiedDate = usdaModifiedDate,
)
