package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

class SaveFoodWithNutrientsUseCase @Inject constructor(
    private val foods: FoodRepository,
    private val foodNutrients: FoodNutrientRepository
) {

    suspend operator fun invoke(
        food: Food,
        rows: List<FoodNutrientRow>
    ): Long {

        val foodId = foods.upsert(food)

        val canonicalRows = canonicalizeToSingleBasis(food, rows)

        foodNutrients.replaceForFood(foodId, canonicalRows)
        return foodId
    }

    /**
     * Ensures we persist a single row per (foodId, nutrientId) by choosing ONE basis:
     * - PER_100G when the food's serving model can be grounded in grams
     * - PER_100ML when the food's serving model can be grounded in milliliters
     * - Otherwise USDA_REPORTED_SERVING (raw, not safely scalable)
     */
    private fun canonicalizeToSingleBasis(
        food: Food,
        rows: List<FoodNutrientRow>
    ): List<FoodNutrientRow> {

        // If the serving unit is mass-based, we can compute grams-per-serving directly.
        val gramsPerServing: Double? = when {
            food.servingUnit.isMassUnit() -> food.servingUnit.toGrams(food.servingSize)
            else -> food.gramsPerServingUnit?.takeIf { it > 0.0 }
        }?.takeIf { it > 0.0 }

        // If the serving unit is volume-based, we can compute ml-per-serving directly.
        val mlPerServing: Double? =
            (if (food.servingUnit.isVolumeUnit()) {
                food.servingUnit.toMilliliters(food.servingSize)
            } else {
                null
            })?.takeIf { it > 0.0 }

        val targetBasis: BasisType? = when {
            gramsPerServing != null -> BasisType.PER_100G
            mlPerServing != null -> BasisType.PER_100ML
            else -> BasisType.USDA_REPORTED_SERVING
        }

        return when (targetBasis) {
            BasisType.PER_100G -> {
                val factor = 100.0 / gramsPerServing!!
                rows.mapNotNull { row ->
                    when (row.basisType) {
                        BasisType.PER_100G -> row.copy(basisGrams = 100.0)
                        BasisType.USDA_REPORTED_SERVING -> row.copy(
                            basisType = BasisType.PER_100G,
                            amount = row.amount * factor,
                            basisGrams = 100.0
                        )
                        else -> null // ignore incompatible basis types
                    }
                }
            }

            BasisType.PER_100ML -> {
                val factor = 100.0 / mlPerServing!!
                rows.mapNotNull { row ->
                    when (row.basisType) {
                        BasisType.PER_100ML -> row.copy(basisGrams = 100.0)
                        BasisType.USDA_REPORTED_SERVING -> row.copy(
                            basisType = BasisType.PER_100ML,
                            amount = row.amount * factor,
                            basisGrams = 100.0
                        )
                        else -> null
                    }
                }
            }

            /*BasisType.USDA_REPORTED_SERVING*/ else -> rows.map { it.copy(basisType = BasisType.USDA_REPORTED_SERVING, basisGrams = null) }
        }
    }
}

/**
 * ============================
 * SAVE_FOOD_WITH_NUTRIENTS_USECASE – FUTURE-ME NOTES
 * ============================
 *
 * Core job:
 * - Persist a Food plus its nutrient rows in a way that guarantees we do NOT create confusing duplicates.
 *
 * Original problem:
 * - We used to persist the row "as-is" (often USDA_REPORTED_SERVING) and ALSO derive PER_100G / PER_100ML.
 * - Because the DB PK includes basisType, this created 2+ rows per nutrient.
 * - UI/editor often assumes 1 row per nutrientId → crashes (duplicate LazyColumn keys) and confusion.
 *
 * Locked-down rule (canonical data model):
 * - Persist exactly ONE basis row per nutrient per food.
 * - Canonical basis is chosen from the food’s serving model:
 *     1) If food can be grounded in grams → PER_100G only
 *     2) Else if food can be grounded in milliliters → PER_100ML only
 *     3) Else → USDA_REPORTED_SERVING only (raw-per-unit), and food is BLOCKED for logging/recipes
 *
 * How canonicalization works (algorithm mindset):
 * - Determine if the food has a meaningful grams-per-serving:
 *     - If servingUnit is a mass unit (G/OZ/LB/etc), compute grams from servingSize+unit.
 *     - Else, rely on gramsPerServingUnit if user provided it.
 * - Determine if the food has a meaningful ml-per-serving:
 *     - Only if servingUnit is a volume unit (ML/CUP_US/etc), compute mL from servingSize+unit.
 * - Pick ONE target basis based on availability:
 *     - Prefer grams (PER_100G) over ml (PER_100ML) over raw (USDA_REPORTED_SERVING).
 * - Convert incoming rows into the chosen basis and discard incompatible basis rows.
 *
 * Why this is written this way:
 * - We want all math (servings, grams eaten, recipe ingredients) to be easy and consistent.
 * - PER_100G / PER_100ML makes scaling straightforward across different serving units.
 * - USDA_REPORTED_SERVING is only kept when we literally cannot ground the serving unit yet (packet/box/etc).
 *
 * Packet/box/bunch “blocked food” nuance:
 * - It is VALID to store nutrients as USDA_REPORTED_SERVING per 1 packet/box/etc.
 * - But the app must treat the food as unusable until a grams-per-unit or ml-based serving is provided.
 * - Once the user provides grounding, we reprocess ENTIRE nutrient list into PER_100G/PER_100ML and overwrite.
 *
 * Future regression smells:
 * - If you ever see both PER_100G and USDA_REPORTED_SERVING rows after saving → canonicalization regressed.
 * - If you’re tempted to keep multiple basis rows “for traceability” → do it elsewhere, not in food_nutrients.
 *
 * Change discipline:
 * - Do not invent identifiers. Use existing BasisType / ServingUnit utilities.
 * - Keep minimal surface area: canonicalize here and keep repository/DAO dumb.
 */
