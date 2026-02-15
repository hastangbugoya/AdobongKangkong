package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.dao.FoodBarcodeDao
import com.example.adobongkangkong.data.local.db.entity.BarcodeMappingSource
import com.example.adobongkangkong.data.local.db.entity.FoodBarcodeEntity
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.repository.FoodRepository
import java.util.UUID
import javax.inject.Inject

class CreateMinimalFoodWithBarcodeUseCase @Inject constructor(
    private val foods: FoodRepository,
    private val barcodeDao: FoodBarcodeDao
) {

    suspend operator fun invoke(
        name: String,
        barcode: String
    ): Long {

        val food = Food(
            id = 0L,
            stableId = UUID.randomUUID().toString(),
            name = name.ifBlank { "Unnamed Food" },
            brand = null,
            servingSize = 1.0,
            servingUnit = ServingUnit.SERVING,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            servingsPerPackage = null,
            isRecipe = false,
            isLowSodium = null,
            usdaFdcId = null,
            usdaGtinUpc = null,
            usdaPublishedDate = null,
            usdaModifiedDate = null
        )

        val foodId = foods.upsert(food)

        val now = System.currentTimeMillis()

        barcodeDao.upsert(
            FoodBarcodeEntity(
                barcode = barcode,
                foodId = foodId,
                source = BarcodeMappingSource.USER_ASSIGNED,
                usdaFdcId = null,
                usdaPublishedDateIso = null,
                assignedAtEpochMs = now,
                lastSeenAtEpochMs = now
            )
        )

        return foodId
    }
}