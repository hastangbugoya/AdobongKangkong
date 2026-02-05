package com.example.adobongkangkong.domain.usda


import android.util.Log
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.domain.model.*
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.NutrientRepository
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import com.example.adobongkangkong.domain.usda.UsdaToCsvNutrientMap
import androidx.room.withTransaction

/**
 * Imports the first USDA foods/search result from a JSON response string into the app DB.
 *
 * - Persists Food (serving size/unit + optional gramsPerServingUnit if serving is grams)
 * - Persists nutrient rows as USDA_REPORTED_SERVING
 * - Additionally derives PER_100G when serving is mass-based (GRM/G) and servingSize > 0
 * - Additionally derives PER_100ML when serving is volume-based (MLT/ML) and servingSize > 0
 *
 * No density guessing. No "fixing" inconsistent branded data.
 */
class ImportUsdaFoodFromSearchJsonUseCase @Inject constructor(
    private val db: NutriDatabase,
    private val foods: FoodRepository,
    private val foodNutrients: FoodNutrientRepository,
    private val nutrients: NutrientRepository
) {
    suspend operator fun invoke(searchJson: String, selectedFdcId: Long? = null): Result {
        Log.d("Meow","ImportUsdaFoodFromSearchJsonUseCase> invoke json:$searchJson fdcid:$selectedFdcId")
        val parsed = UsdaFoodsSearchParser.parse(searchJson)

        val item = when (selectedFdcId) {
            null -> parsed.foods.firstOrNull()
            else -> parsed.foods.firstOrNull { it.fdcId == selectedFdcId }
        } ?: return Result.Blocked(
            if (selectedFdcId == null) "No foods in USDA response."
            else "Selected item not found in USDA response (fdcId=$selectedFdcId)."
        )
        // --- Serving unit (uses ServingUnitExt.kt) ---
        val servingUnit = ServingUnit.fromUsda(item.servingSizeUnit)
            ?: return Result.Blocked("Unsupported USDA servingSizeUnit='${item.servingSizeUnit}'")

        val servingSize = item.servingSize ?: 1.0

        // gramsPerServingUnit only when USDA serving is grams
        val gramsPerServingUnit: Double? =
            if (servingUnit == ServingUnit.G) servingSize.takeIf { it > 0.0 } else null

        // --- Persist Food ---
        val stableId = when {
            !item.gtinUpc.isNullOrBlank() -> "usda:gtin:${item.gtinUpc.trim()}"
            else -> "usda:fdc:${item.fdcId}"
        }

        val brand: String? =
            item.brandName?.trim().takeIf { !it.isNullOrBlank() }
                ?: item.brandOwner?.trim().takeIf { !it.isNullOrBlank() }
        val normalizedName = item.description
            ?.trim()
            ?.toTitleCase()
            .orEmpty()
            .ifBlank { "Unnamed USDA Food" }
        val food = Food(
            id = 0L,
            name = normalizedName,
            servingSize = servingSize,
            servingUnit = servingUnit,
            servingsPerPackage = null,
            gramsPerServingUnit = gramsPerServingUnit,

            // ✅ TODOs resolved:
            stableId = stableId.ifBlank { UUID.randomUUID().toString() },
            brand = brand,
            isRecipe = false,
            isLowSodium = null
        )

// --- Map USDA nutrients → CSV nutrients (USDA_REPORTED_SERVING only) ---
        val servingRows: List<FoodNutrientRow> = item.foodNutrients.mapNotNull { n ->
            val usdaNumber = n.nutrientNumber ?: return@mapNotNull null
            val csvCode = UsdaToCsvNutrientMap.byUsdaNumber[usdaNumber]
                ?: return@mapNotNull null

            val nutrient = nutrients.getByCode(csvCode)
                ?: return@mapNotNull null   // skip if CSV doesn’t define it

            val amt = n.value ?: return@mapNotNull null

            FoodNutrientRow(
                nutrient = nutrient,
                amount = amt,
                basisType = BasisType.USDA_REPORTED_SERVING,
                basisGrams = null
            )
        }

        // --- Add derived normalization rows when safe ---
        val allRows = buildList {
            addAll(servingRows)

            // PER_100G only if serving is grams
            if (servingUnit == ServingUnit.G && servingSize > 0.0) {
                val factor = 100.0 / servingSize
                addAll(
                    servingRows.map { r ->
                        r.copy(
                            basisType = BasisType.PER_100G,
                            amount = r.amount * factor,
                            basisGrams = 100.0
                        )
                    }
                )
            }
        }

//        foodNutrients.replaceForFood(foodId, allRows)
        val foodId = db.withTransaction {
            android.util.Log.d("USDA_IMPORT", "FoodRepository impl=${foods::class.qualifiedName}")

            val id = foods.upsert(food)

            // 1) prove whether returned id exists as a real row PK
            val row = db.foodDao().getById(id)
            val dbPath = db.openHelper.writableDatabase.path
            android.util.Log.d("USDA_IMPORT", "DB path=$dbPath")
            android.util.Log.d("USDA_IMPORT", "Food row from DAO=$row")

            android.util.Log.d("USDA_IMPORT", "returnedId=$id getById(found)=${row != null}")

            // 2) compare with the real PK resolved via stableId
            val resolved = db.foodDao().getIdByStableId(food.stableId)
            android.util.Log.d("USDA_IMPORT", "stableId=${food.stableId} resolvedByStableId=$resolved")

            check(resolved != null) { "No row found by stableId=${food.stableId}" }
            check(id == resolved) { "upsert returned $id but DB id is $resolved (returnedId is not a DB PK)" }

            foodNutrients.replaceForFood(id, allRows)
            id
        }
        android.util.Log.d("Meow", "USDA_IMPORT > Success foodId=$foodId rows=${allRows.size}")
        return Result.Success(foodId)
    }

    sealed class Result {
        data class Success(val foodId: Long) : Result()
        data class Blocked(val reason: String) : Result()
    }
}

@Serializable
data class UsdaFoodsSearchResponse(
    val totalHits: Int = 0,
    val foods: List<UsdaFoodSearchItem> = emptyList()
)

@Serializable
data class UsdaFoodSearchItem(
    val fdcId: Long,
    val description: String? = null,
    val gtinUpc: String? = null,
    val brandOwner: String? = null,
    val brandName: String? = null,
    val ingredients: String? = null,
    val servingSizeUnit: String? = null,
    val servingSize: Double? = null,
    val householdServingFullText: String? = null,
    val foodNutrients: List<UsdaFoodNutrient> = emptyList()
)

@Serializable
data class UsdaFoodNutrient(
    val nutrientId: Long,
    val nutrientName: String? = null,
    val unitName: String? = null,
    val value: Double? = null
)

/** Unit mapping for USDA nutrient units. Expand as needed. */
fun NutrientUnit.Companion.fromUsda(unitName: String?): NutrientUnit? {
    if (unitName.isNullOrBlank()) return null
    return when (unitName.trim().uppercase()) {
        "KCAL" -> NutrientUnit.KCAL
        "G" -> NutrientUnit.G
        "MG" -> NutrientUnit.MG
        "UG", "MCG" -> NutrientUnit.UG
        "IU" -> NutrientUnit.IU
        else -> null
    }
}

fun String.toTitleCase(): String =
    lowercase()
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase() else c.toString()
            }
        }