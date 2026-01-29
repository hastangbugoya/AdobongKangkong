package com.example.adobongkangkong.data.local.db.seed

import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.FoodNutrientDao
import com.example.adobongkangkong.data.local.db.dao.NutrientDao
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.entity.NutrientEntity
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.nutrition.NutrientCodes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val foodDao: FoodDao,
    private val nutrientDao: NutrientDao,
    private val foodNutrientDao: FoodNutrientDao
) {

    suspend fun seedIfEmpty() {
        if (foodDao.countFoods() > 0) return

        // --- Nutrients (codes must match what dashboard expects) ---
        val nutrients = listOf(
            NutrientEntity(id = 1, code = NutrientCodes.CALORIES_KCAL, displayName = "Calories", unit = NutrientUnit.KCAL, category = NutrientCategory.MACRO),
            NutrientEntity(id = 2, code = NutrientCodes.PROTEIN_G, displayName = "Protein", unit = NutrientUnit.G, category = NutrientCategory.MACRO),
            NutrientEntity(id = 3, code = NutrientCodes.CARBS_G, displayName = "Carbohydrate", unit = NutrientUnit.G, category = NutrientCategory.MACRO),
            NutrientEntity(id = 4, code = NutrientCodes.FAT_G, displayName = "Fat", unit = NutrientUnit.G, category = NutrientCategory.MACRO),

            NutrientEntity(id = 5, code = NutrientCodes.FIBER_G, displayName = "Fiber", unit = NutrientUnit.G, category = NutrientCategory.MACRO),
            NutrientEntity(id = 6, code = NutrientCodes.SUGAR_G, displayName = "Total Sugars", unit = NutrientUnit.G, category = NutrientCategory.MACRO),
            NutrientEntity(id = 7, code = NutrientCodes.SODIUM_MG, displayName = "Sodium", unit = NutrientUnit.MG, category = NutrientCategory.MACRO),
        )
        nutrientDao.upsertAll(nutrients)

        // --- Foods ---
        val foods = listOf(
            // Classic “weighed” food: per serving is grams-based
            FoodEntity(
                id = 1001,
                name = "Chicken breast, cooked",
                brand = null,
                servingSize = 100.0,
                servingUnit = ServingUnit.G,
                gramsPerServing = 100.0,
                servingsPerPackage = null,
                isRecipe = false
            ),
            FoodEntity(
                id = 1002,
                name = "Jasmine rice, cooked",
                brand = null,
                servingSize = 1.0,
                servingUnit = ServingUnit.CUP,
                gramsPerServing = 158.0, // 1 cup cooked ~158g
                servingsPerPackage = null,
                isRecipe = false
            ),
            FoodEntity(
                id = 1003,
                name = "Olive oil",
                brand = null,
                servingSize = 1.0,
                servingUnit = ServingUnit.TBSP,
                gramsPerServing = 13.5, // label-ish
                servingsPerPackage = null,
                isRecipe = false
            ),

            // Package-style logging example
            FoodEntity(
                id = 1004,
                name = "Spaghetti sauce",
                brand = "Generic",
                servingSize = 0.5,
                servingUnit = ServingUnit.CUP,
                gramsPerServing = 125.0,      // 1/2 cup = 125g (example)
                servingsPerPackage = 6.0,     // “Servings per container”
                isRecipe = false
            ),

            // Some snack with sugars
            FoodEntity(
                id = 1005,
                name = "Greek yogurt, nonfat",
                brand = null,
                servingSize = 170.0,
                servingUnit = ServingUnit.G,
                gramsPerServing = 170.0,
                servingsPerPackage = null,
                isRecipe = false
            ),

            FoodEntity(
                id = 1006,
                name = "Banana",
                brand = null,
                servingSize = 1.0,
                servingUnit = ServingUnit.PIECE,
                gramsPerServing = 118.0,
                servingsPerPackage = null,
                isRecipe = false
            ),

            FoodEntity(
                id = 1007,
                name = "Egg",
                brand = null,
                servingSize = 1.0,
                servingUnit = ServingUnit.PIECE,
                gramsPerServing = 50.0,
                servingsPerPackage = null,
                isRecipe = false
            ),

            // Your namesake dish as a “recipe-like food” (precomputed macros)
            FoodEntity(
                id = 2001,
                name = "Adobong Kangkong (simple)",
                brand = null,
                servingSize = 1.0,
                servingUnit = ServingUnit.CUP,
                gramsPerServing = 180.0,
                servingsPerPackage = null,
                isRecipe = true
            )
        )
        foodDao.upsertAll(foods)

        // --- Food nutrients (amounts per serving) ---
        // helper IDs
        val CAL = 1L
        val PRO = 2L
        val CARB = 3L
        val FAT = 4L
        val FIB = 5L
        val SUG = 6L
        val SOD = 7L

        val fn = mutableListOf<FoodNutrientEntity>()

        fun add(foodId: Long, nutrientId: Long, perServing: Double, basisType: BasisType) {
            fn += FoodNutrientEntity(
                foodId = foodId, nutrientId = nutrientId, nutrientAmountPerBasis = perServing,
                basisType = basisType,
            )
        }

        // -------------------------------
// Chicken breast cooked, per 100g
// -------------------------------
        add(1001, CAL, 165.0, BasisType.PER_100G)
        add(1001, PRO, 31.0,  BasisType.PER_100G)
        add(1001, CARB, 0.0,  BasisType.PER_100G)
        add(1001, FAT, 3.6,   BasisType.PER_100G)
        add(1001, SOD, 74.0,  BasisType.PER_100G)

// -------------------------------
// Jasmine rice cooked, per 1 cup
// -------------------------------
        add(1002, CAL, 205.0, BasisType.PER_SERVING)
        add(1002, PRO, 4.3,   BasisType.PER_SERVING)
        add(1002, CARB, 44.5, BasisType.PER_SERVING)
        add(1002, FAT, 0.4,   BasisType.PER_SERVING)
        add(1002, FIB, 0.6,   BasisType.PER_SERVING)
        add(1002, SUG, 0.1,   BasisType.PER_SERVING)
        add(1002, SOD, 2.0,   BasisType.PER_SERVING)

// -------------------------------
// Olive oil, per 1 tbsp
// -------------------------------
        add(1003, CAL, 119.0, BasisType.PER_SERVING)
        add(1003, PRO, 0.0,   BasisType.PER_SERVING)
        add(1003, CARB, 0.0,  BasisType.PER_SERVING)
        add(1003, FAT, 13.5,  BasisType.PER_SERVING)
        add(1003, SOD, 0.0,   BasisType.PER_SERVING)

// -------------------------------
// Spaghetti sauce, per 1/2 cup
// -------------------------------
        add(1004, CAL, 70.0,  BasisType.PER_SERVING)
        add(1004, PRO, 2.0,   BasisType.PER_SERVING)
        add(1004, CARB, 12.0, BasisType.PER_SERVING)
        add(1004, FAT, 2.0,   BasisType.PER_SERVING)
        add(1004, FIB, 3.0,   BasisType.PER_SERVING)
        add(1004, SUG, 7.0,   BasisType.PER_SERVING)
        add(1004, SOD, 480.0, BasisType.PER_SERVING)

// -------------------------------
// Greek yogurt nonfat, per 170g
// -------------------------------
        add(1005, CAL, 100.0, BasisType.PER_SERVING)
        add(1005, PRO, 17.0,  BasisType.PER_SERVING)
        add(1005, CARB, 6.0,  BasisType.PER_SERVING)
        add(1005, FAT, 0.0,   BasisType.PER_SERVING)
        add(1005, SUG, 6.0,   BasisType.PER_SERVING)
        add(1005, SOD, 60.0,  BasisType.PER_SERVING)

// -------------------------------
// Banana, per 1 medium
// -------------------------------
        add(1006, CAL, 105.0, BasisType.PER_SERVING)
        add(1006, PRO, 1.3,   BasisType.PER_SERVING)
        add(1006, CARB, 27.0, BasisType.PER_SERVING)
        add(1006, FAT, 0.4,   BasisType.PER_SERVING)
        add(1006, FIB, 3.1,   BasisType.PER_SERVING)
        add(1006, SUG, 14.0,  BasisType.PER_SERVING)
        add(1006, SOD, 1.0,   BasisType.PER_SERVING)

// -------------------------------
// Egg, per 1 large
// -------------------------------
        add(1007, CAL, 72.0, BasisType.PER_SERVING)
        add(1007, PRO, 6.3,  BasisType.PER_SERVING)
        add(1007, CARB, 0.4, BasisType.PER_SERVING)
        add(1007, FAT, 4.8,  BasisType.PER_SERVING)
        add(1007, SOD, 71.0, BasisType.PER_SERVING)

// ----------------------------------------
// Adobong Kangkong (example), per serving
// ----------------------------------------
        add(2001, CAL, 140.0, BasisType.PER_SERVING)
        add(2001, PRO, 4.0,   BasisType.PER_SERVING)
        add(2001, CARB, 10.0, BasisType.PER_SERVING)
        add(2001, FAT, 9.0,   BasisType.PER_SERVING)
        add(2001, FIB, 4.0,   BasisType.PER_SERVING)
        add(2001, SUG, 2.0,   BasisType.PER_SERVING)
        add(2001, SOD, 900.0, BasisType.PER_SERVING)

        foodNutrientDao.upsertAll(fn)
    }
}
