package com.example.adobongkangkong.domain.usda

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.mapper.toEntity
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.RecipeMacroPreview
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.NutrientRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportUsdaFoodFromSearchJsonUseCaseInstrumentedTest {

    private lateinit var db: NutriDatabase
    private lateinit var foods: FoodRepository
    private lateinit var foodNutrients: FoodNutrientRepository
    private lateinit var nutrients: NutrientRepository
    private lateinit var useCase: ImportUsdaFoodFromSearchJsonUseCase

    private val nutrientByCode = mapOf(
        "CALORIES_KCAL" to Nutrient(
            id = 1008L,
            code = "CALORIES_KCAL",
            displayName = "Calories",
            unit = NutrientUnit.KCAL,
            category = NutrientCategory.ENERGY
        ),
        "PROTEIN_G" to Nutrient(
            id = 1003L,
            code = "PROTEIN_G",
            displayName = "Protein",
            unit = NutrientUnit.G,
            category = NutrientCategory.PROTEIN
        ),
        "FAT_G" to Nutrient(
            id = 1004L,
            code = "FAT_G",
            displayName = "Fat",
            unit = NutrientUnit.G,
            category = NutrientCategory.FAT
        ),
        "CARBS_G" to Nutrient(
            id = 1005L,
            code = "CARBS_G",
            displayName = "Carbohydrates",
            unit = NutrientUnit.G,
            category = NutrientCategory.CARBOHYDRATE
        ),
        "SODIUM_MG" to Nutrient(
            id = 1093L,
            code = "SODIUM_MG",
            displayName = "Sodium",
            unit = NutrientUnit.MG,
            category = NutrientCategory.MINERAL
        ),
        "TOTAL_SUGARS_G" to Nutrient(
            id = 2000L,
            code = "TOTAL_SUGARS_G",
            displayName = "Total Sugars",
            unit = NutrientUnit.G,
            category = NutrientCategory.SUGAR
        ),
        "SUGARS_G" to Nutrient(
            id = 2000L,
            code = "SUGARS_G",
            displayName = "Sugars",
            unit = NutrientUnit.G,
            category = NutrientCategory.SUGAR
        )
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        db = Room.inMemoryDatabaseBuilder(
            context,
            NutriDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        foods = mockk(relaxed = true)
        foodNutrients = mockk(relaxed = true)
        nutrients = mockk(relaxed = true)

        coEvery { foods.upsert(any()) } coAnswers {
            val food = invocation.args[0] as Food
            db.foodDao().upsert(food.toEntity())

            db.foodDao().getIdByStableId(food.stableId)
                ?: error("Test upsert failed: no food found for stableId=${food.stableId}")
        }

        coEvery { foods.getById(any()) } coAnswers {
            db.foodDao().getById(firstArg())?.let {
                // Not needed by ImportUsdaFoodFromSearchJsonUseCase.
                // Keeping this relaxed would also be OK.
                null
            }
        }

        coEvery { foodNutrients.replaceForFood(any(), any()) } coAnswers {
            val foodId = invocation.args[0] as Long
            val rows = arg<List<FoodNutrientRow>>(1)

            db.foodNutrientDao().deleteForFood(foodId)

            if (rows.isNotEmpty()) {
                db.foodNutrientDao().upsertAll(
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
        }

        coEvery { foodNutrients.getForFood(any()) } coAnswers {
            emptyList()
        }

        coEvery { foodNutrients.deleteOne(any(), any()) } coAnswers {
            db.foodNutrientDao().deleteOne(firstArg(), secondArg())
        }

        coEvery { foodNutrients.computeRecipeMacroPreview(any()) } coAnswers {
            RecipeMacroPreview()
        }

        coEvery { nutrients.getByCode(any()) } coAnswers {
            val code = invocation.args[0] as String
            nutrientByCode[code]
        }

        useCase = ImportUsdaFoodFromSearchJsonUseCase(
            db = db,
            foods = foods,
            foodNutrients = foodNutrients,
            nutrients = nutrients
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun initialAmbiguousBrandedImport_savesFoodShellOnly_andReturnsNeedsInterpretationChoice() = runTest {
        val result = useCase(
            searchJson = huntsFourCheeseSearchJson,
            selectedFdcId = HUNTS_FDC_ID,
            forcedInterpretation = null
        )

        assertTrue(result is ImportUsdaFoodFromSearchJsonUseCase.Result.NeedsInterpretationChoice)

        val prompt = result as ImportUsdaFoodFromSearchJsonUseCase.Result.NeedsInterpretationChoice
        val food = db.foodDao().getById(prompt.foodId)

        assertNotNull(food)
        assertEquals("027000500439", food!!.usdaGtinUpc)
        assertEquals(HUNTS_FDC_ID, food.usdaFdcId)
        assertEquals(0.5, food.servingSize, 0.0001)
        assertEquals(ServingUnit.CUP, food.servingUnit)
        assertEquals(252.0, food.gramsPerServingUnit ?: -1.0, 0.0001)
        assertEquals(null, food.mlPerServingUnit)

        val rows = db.foodNutrientDao().getForFood(prompt.foodId)
        assertTrue(
            "NeedsInterpretationChoice should save shell only; nutrient rows should wait for user choice.",
            rows.isEmpty()
        )
    }

    @Test
    fun forcedPer100Import_writesPer100gRows_withoutScalingUsdaValues() = runTest {
        val result = useCase(
            searchJson = huntsFourCheeseSearchJson,
            selectedFdcId = HUNTS_FDC_ID,
            forcedInterpretation = ImportUsdaFoodFromSearchJsonUseCase.InterpretationChoice.PER_100_STYLE
        )

        assertTrue(result is ImportUsdaFoodFromSearchJsonUseCase.Result.Success)

        val success = result as ImportUsdaFoodFromSearchJsonUseCase.Result.Success
        val rows = db.foodNutrientDao().getForFood(success.foodId)

        assertTrue("Forced PER_100 import should persist nutrient rows.", rows.isNotEmpty())
        assertTrue(rows.all { it.basisType == BasisType.PER_100G })

        val byNutrientId = rows.associateBy { it.nutrientId }

        assertEquals(48.0, byNutrientId.getValue(1008L).nutrientAmountPerBasis, 0.0001)
        assertEquals(1.59, byNutrientId.getValue(1003L).nutrientAmountPerBasis, 0.0001)
        assertEquals(7.94, byNutrientId.getValue(1005L).nutrientAmountPerBasis, 0.0001)
        assertEquals(460.0, byNutrientId.getValue(1093L).nutrientAmountPerBasis, 0.0001)
    }

    @Test
    fun forcedPerServingImport_writesReportedServingRows_withoutScalingUsdaValues() = runTest {
        val result = useCase(
            searchJson = huntsFourCheeseSearchJson,
            selectedFdcId = HUNTS_FDC_ID,
            forcedInterpretation = ImportUsdaFoodFromSearchJsonUseCase.InterpretationChoice.PER_SERVING_STYLE
        )

        assertTrue(result is ImportUsdaFoodFromSearchJsonUseCase.Result.Success)

        val success = result as ImportUsdaFoodFromSearchJsonUseCase.Result.Success
        val rows = db.foodNutrientDao().getForFood(success.foodId)

        assertTrue("Forced PER_SERVING import should persist nutrient rows.", rows.isNotEmpty())
        assertTrue(rows.all { it.basisType == BasisType.USDA_REPORTED_SERVING })

        val byNutrientId = rows.associateBy { it.nutrientId }

        assertEquals(48.0, byNutrientId.getValue(1008L).nutrientAmountPerBasis, 0.0001)
        assertEquals(1.59, byNutrientId.getValue(1003L).nutrientAmountPerBasis, 0.0001)
        assertEquals(7.94, byNutrientId.getValue(1005L).nutrientAmountPerBasis, 0.0001)
        assertEquals(460.0, byNutrientId.getValue(1093L).nutrientAmountPerBasis, 0.0001)
    }

    companion object {
        private const val HUNTS_FDC_ID = 1935250L

        private val huntsFourCheeseSearchJson = """
            {
              "totalHits": 1,
              "currentPage": 1,
              "totalPages": 1,
              "foods": [
                {
                  "fdcId": 1935250,
                  "description": "HUNT'S, PASTA SAUCE, FOUR CHEESE",
                  "dataType": "Branded",
                  "gtinUpc": "027000500439",
                  "publishedDate": "2021-07-29",
                  "brandOwner": "Conagra Brands, Inc.",
                  "brandName": "HUNT'S",
                  "marketCountry": "United States",
                  "foodCategory": "Prepared Pasta & Pizza Sauces",
                  "modifiedDate": "2018-06-30",
                  "packageWeight": "48 oz/1.36 kg",
                  "servingSizeUnit": "g",
                  "servingSize": 126.0,
                  "householdServingFullText": "0.5 cup",
                  "foodNutrients": [
                    {
                      "nutrientId": 1003,
                      "nutrientName": "Protein",
                      "nutrientNumber": "203",
                      "unitName": "G",
                      "value": 1.59
                    },
                    {
                      "nutrientId": 1004,
                      "nutrientName": "Total lipid (fat)",
                      "nutrientNumber": "204",
                      "unitName": "G",
                      "value": 0.79
                    },
                    {
                      "nutrientId": 1005,
                      "nutrientName": "Carbohydrate, by difference",
                      "nutrientNumber": "205",
                      "unitName": "G",
                      "value": 7.94
                    },
                    {
                      "nutrientId": 1008,
                      "nutrientName": "Energy",
                      "nutrientNumber": "208",
                      "unitName": "KCAL",
                      "value": 48.0
                    },
                    {
                      "nutrientId": 2000,
                      "nutrientName": "Total Sugars",
                      "nutrientNumber": "269",
                      "unitName": "G",
                      "value": 3.97
                    },
                    {
                      "nutrientId": 1093,
                      "nutrientName": "Sodium, Na",
                      "nutrientNumber": "307",
                      "unitName": "MG",
                      "value": 460
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}