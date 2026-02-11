package com.example.adobongkangkong.data.repository

import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.NutrientDao
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.RecipeMacroPreview
import com.example.adobongkangkong.domain.nutrition.NutrientCodes
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import javax.inject.Inject

class FoodNutrientRepositoryImpl @Inject constructor(
    private val foodNutrientDao: FoodNutrientDao,
    private val nutrientDao: NutrientDao
) : FoodNutrientRepository {

    override suspend fun computeRecipeMacroPreview(
        ingredients: List<Pair<Long, Double>>
    ): RecipeMacroPreview {
        if (ingredients.isEmpty()) return RecipeMacroPreview()

        // Resolve macro nutrient IDs once per call (fast enough for v1).
        // If you want, we can cache these later.
        val caloriesId = nutrientDao.getIdByCode(NutrientCodes.CALORIES_KCAL)
        val proteinId = nutrientDao.getIdByCode(NutrientCodes.PROTEIN_G)
        val carbsId = nutrientDao.getIdByCode(NutrientCodes.CARBS_G)
        val fatId = nutrientDao.getIdByCode(NutrientCodes.FAT_G)

        var totalCalories = 0.0
        var totalProtein = 0.0
        var totalCarbs = 0.0
        var totalFat = 0.0

        for ((foodId, servings) in ingredients) {
            if (servings <= 0.0) continue

            // You already have this DAO method. :contentReference[oaicite:2]{index=2}
            val nutrients = foodNutrientDao.getForFood(foodId)

            fun amountPerGramFor(nutrientId: Long?): Double {
                if (nutrientId == null) return 0.0

                val row = nutrients.firstOrNull { it.nutrientId == nutrientId } ?: return 0.0

                return when (row.basisType) {
                    BasisType.PER_100G,
                    BasisType.PER_100ML -> row.nutrientAmountPerBasis / 100.0
                    else -> row.nutrientAmountPerBasis
                }
            }


            totalCalories += amountPerGramFor(caloriesId) * servings
            totalProtein += amountPerGramFor(proteinId) * servings
            totalCarbs += amountPerGramFor(carbsId) * servings
            totalFat += amountPerGramFor(fatId) * servings
        }

        return RecipeMacroPreview(
            totalCalories = totalCalories,
            totalProteinG = totalProtein,
            totalCarbsG = totalCarbs,
            totalFatG = totalFat
        )
    }

    override suspend fun getForFood(foodId: Long): List<FoodNutrientRow> {
        return foodNutrientDao.getForFoodWithMeta(foodId).map { row ->

            // row.basisType comes from DB (String). Default safely for legacy/unknown values.
            val bt = row.basisType

            FoodNutrientRow(
                nutrient = Nutrient(
                    id = row.nutrientId,
                    code = row.code,
                    displayName = row.displayName,
                    unit = NutrientUnit.valueOf(row.unit),
                    category = NutrientCategory.fromDb(row.category)
                ),
                amount = row.amount,
                basisType = bt,
                basisGrams = when (bt) {
                    BasisType.PER_100G, BasisType.PER_100ML -> 100.0
                    BasisType.USDA_REPORTED_SERVING -> null
                }
            )
        }
    }


    override suspend fun replaceForFood(
        foodId: Long,
        rows: List<FoodNutrientRow>
    ) {
        foodNutrientDao.deleteForFood(foodId)

        if (rows.isEmpty()) return

        foodNutrientDao.upsertAll(
            rows.map { row ->
                FoodNutrientEntity(
                    foodId = foodId,
                    nutrientId = row.nutrient.id,
                    nutrientAmountPerBasis = row.amount,
                    unit = row.nutrient.unit,
                    basisType = row.basisType
                )
            }
        )

    }

    override suspend fun deleteOne(foodId: Long, nutrientId: Long) {
        foodNutrientDao.deleteOne(foodId = foodId, nutrientId = nutrientId)
    }
}
