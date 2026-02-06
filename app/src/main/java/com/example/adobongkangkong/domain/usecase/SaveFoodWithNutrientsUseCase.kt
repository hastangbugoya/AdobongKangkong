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

        // Lock-in gramsPerServingUnit for mass units if user/csv left it blank.
        // Meaning: grams per 1 unit of servingUnit (e.g. 1 oz -> 28.3495g).
        val foodToPersist = food.withLockedInGroundingIfPossible()

        val foodId = foods.upsert(foodToPersist)

        val canonicalRows = canonicalizeToSingleBasis(foodToPersist, rows)

        foodNutrients.replaceForFood(foodId, canonicalRows)
        return foodId
    }

    private fun Food.withLockedInGroundingIfPossible(): Food {
        val existing = this.gramsPerServingUnit?.takeIf { it > 0.0 }
        if (existing != null) return this

        // Only lock in for mass units where the conversion is deterministic.
        // For ServingUnit.G, gramsPerServingUnit is unnecessary; keep null.
        if (this.servingUnit.isMassUnit() && this.servingUnit != ServingUnit.G) {
            val gramsPer1Unit = this.servingUnit.toGrams(1.0)
            if (gramsPer1Unit != null && gramsPer1Unit > 0.0) {
                return this.copy(gramsPerServingUnit = gramsPer1Unit)
            }
        }

        return this
    }

    /**
     * Ensures we persist exactly ONE basis row per nutrient per food by choosing ONE target basis:
     * - PER_100G when the food can be grounded in grams
     * - PER_100ML when the food can be grounded in milliliters
     * - Otherwise USDA_REPORTED_SERVING (raw, not safely scalable)
     *
     * Also dedupes rows so there is exactly one row per nutrientId in the returned list.
     */
    private fun canonicalizeToSingleBasis(
        food: Food,
        rows: List<FoodNutrientRow>
    ): List<FoodNutrientRow> {

        val gramsPerServing: Double? = computeGramsPerServing(food)
        val mlPerServing: Double? = computeMlPerServing(food)

        val targetBasis: BasisType =
            when {
                gramsPerServing != null -> BasisType.PER_100G
                mlPerServing != null -> BasisType.PER_100ML
                else -> BasisType.USDA_REPORTED_SERVING
            }

        val converted: List<FoodNutrientRow> =
            when (targetBasis) {
                BasisType.PER_100G -> {
                    val factor = 100.0 / gramsPerServing!!
                    rows.mapNotNull { row ->
                        when (row.basisType) {
                            BasisType.PER_100G ->
                                row.copy(basisType = BasisType.PER_100G, basisGrams = 100.0)

                            BasisType.USDA_REPORTED_SERVING ->
                                row.copy(
                                    basisType = BasisType.PER_100G,
                                    amount = row.amount * factor,
                                    basisGrams = 100.0
                                )

                            else -> null
                        }
                    }
                }

                BasisType.PER_100ML -> {
                    val factor = 100.0 / mlPerServing!!
                    rows.mapNotNull { row ->
                        when (row.basisType) {
                            BasisType.PER_100ML ->
                                row.copy(basisType = BasisType.PER_100ML, basisGrams = 100.0)

                            BasisType.USDA_REPORTED_SERVING ->
                                row.copy(
                                    basisType = BasisType.PER_100ML,
                                    amount = row.amount * factor,
                                    basisGrams = 100.0
                                )

                            else -> null
                        }
                    }
                }

                else -> {
                    rows.map {
                        it.copy(basisType = BasisType.USDA_REPORTED_SERVING, basisGrams = null)
                    }
                }
            }

        // Hard guarantee: ONE row per nutrientId (within the chosen basis).
        // Preference order:
        // 1) if any row was already in the target basis, keep that (stable)
        // 2) else keep the first converted row (deterministic)
        return converted
            .groupBy { it.nutrient.id }
            .mapNotNull { (_, group) ->
                group.firstOrNull { it.basisType == targetBasis } ?: group.firstOrNull()
            }
    }

    private fun computeGramsPerServing(food: Food): Double? {
        val grams = when {
            // mass units are deterministically grounded by servingSize+unit
            food.servingUnit.isMassUnit() -> food.servingUnit.toGrams(food.servingSize)

            // non-mass grounded by grams-per-1-unit (e.g., 125g per bunch)
            else -> {
                val gramsPer1Unit = food.gramsPerServingUnit?.takeIf { it > 0.0 } ?: return null
                food.servingSize * gramsPer1Unit
            }
        }

        return grams?.takeIf { it > 0.0 }
    }

    private fun computeMlPerServing(food: Food): Double? {
        val ml =
            if (food.servingUnit.isVolumeUnit()) {
                food.servingUnit.toMilliliters(food.servingSize)
            } else null

        return ml?.takeIf { it > 0.0 }
    }
}

/**
 * FUTURE-YOU NOTE (2026-02-06):
 *
 * Canonical nutrients MUST be single-basis per nutrientId per food.
 *
 * - We persist exactly one basis row per nutrient (enforced here before replaceForFood()).
 * - Target basis selection:
 *     - If grounded in grams -> PER_100G
 *     - Else if grounded in mL -> PER_100ML
 *     - Else -> USDA_REPORTED_SERVING
 *
 * - IMPORTANT GRAMMING RULE:
 *     - If servingUnit is mass: gramsPerServing = toGrams(servingSize)
 *     - Else if gramsPerServingUnit exists: gramsPerServing = servingSize * gramsPerServingUnit
 *       (gramsPerServingUnit means grams per 1 unit of servingUnit)
 *
 * - We may lock in gramsPerServingUnit for mass units when blank (e.g. oz -> 28.3495g per oz),
 *   but we never overwrite a user-provided gramsPerServingUnit.
 */


/** 2025/02/03
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
/** 2025/02/05
 * ============================================================================
 * FOR-FUTURE-ME NOTES (SaveFoodWithNutrientsUseCase)
 * ============================================================================
 *
 * WHAT THIS USE CASE DOES
 * -----------------------
 * This is the single “commit point” for a Food + its nutrient rows:
 *
 *   1) Upsert the Food row (creates id if new)
 *   2) Normalize/augment nutrient rows into the DB’s basis model
 *   3) Replace all food_nutrients rows for that foodId in one shot
 *
 * It intentionally does NOT:
 * - decide whether a food is “usable” (that’s a separate validation/use case)
 * - manage flags (favorite/eatMore/limit) (FoodEditor handles via FoodGoalFlagsRepository)
 * - interpret “serving text” for math (household text is display-only)
 *
 * DESIGN GOAL / MINDSET
 * ---------------------
 * We want deterministic math and minimal surprises:
 *
 * - If we can safely normalize to PER_100G or PER_100ML, we do it here.
 * - If we can’t (container-ish units without weight/density), we keep USDA_REPORTED_SERVING.
 *
 * This use case is the “basis expansion” layer: it takes whatever the editor/importer
 * provides and ensures the DB has the canonical forms needed for later conversions.
 *
 * IMPORTANT CURRENT BEHAVIOR
 * --------------------------
 * The current implementation ALWAYS:
 *   - Persists the input row "as-is" (add(row))
 * THEN conditionally adds derived rows:
 *   - From USDA_REPORTED_SERVING → PER_100G when gramsPerServingUnit is known (> 0)
 *   - From USDA_REPORTED_SERVING → PER_100ML when servingUnit == ML
 *
 * This means the DB can contain multiple rows per (foodId, nutrientId) because
 * the FoodNutrientEntity primary key includes basisType:
 *
 *   (foodId, nutrientId, basisType)
 *
 * So “duplicates” in UI are expected unless the UI filters by basisType.
 *
 * LOCKED-DOWN RULES WE AGREED ON (CANONICALIZATION)
 * ------------------------------------------------
 * Long-term canonical rule (what we WANT):
 *   - Prefer storing ONE canonical basis (PER_100G for mass-backed foods; PER_100ML for liquid)
 *   - Serving basis rows become OPTIONAL / expendable when we have:
 *       FoodEntity.servingSize + servingUnit + (gramsPerServingUnit or density bridge)
 *
 * BUT: some units (PACKET/BOX/BUNCH/etc) have no inherent grams or mL.
 * For those, USDA_REPORTED_SERVING acts as:
 *   - “raw label data per 1 unit”
 *   - a flag that the user must provide weight/volume before the food is usable.
 *
 * In that model, conversion happens when the user later supplies gramsPerServingUnit
 * (or a volume definition + density). At that time we can rewrite nutrients to PER_100*
 * and (optionally) remove the USDA_REPORTED_SERVING rows.
 *
 * SO WHY DO WE STILL KEEP “AS-IS” ROWS RIGHT NOW?
 * -----------------------------------------------
 * Because:
 * - Imports (USDA/CSV) may come in serving-based, and we don’t want to lose raw provenance.
 * - Some foods are intentionally not convertible yet (no gramsPerServingUnit).
 * - Keeping as-is lets the user see something immediately and gives the validator
 *   a clean way to detect “not grounded”.
 *
 * NOTE: If we decide to enforce “ONLY ONE basis row per nutrient”, this file is the place
 * to do it — but that requires a coordinated change:
 *   - update FoodEditorData / FoodNutrientRepository.getForFood() to request a specific basis
 *   - update any computations (recipes/logging) to use canonical basis only
 *   - update imports so they feed only canonical basis when possible
 *
 * KNOWN PITFALL: UI “DUPLICATE NUTRIENTS”
 * ---------------------------------------
 * If the editor loads nutrients via FoodNutrientRepository.getForFood(foodId) and
 * that repository returns *all* basis types, the UI will show duplicates in the
 * nutrients LazyColumn.
 *
 * The fix is NOT here unless we change policy.
 * The fix is usually:
 *   - Choose/display ONE basis in GetFoodEditorDataUseCase / FoodNutrientRepository.getForFood()
 *     (e.g., prefer PER_100G/PER_100ML; fallback to USDA_REPORTED_SERVING)
 *
 * WHY replaceForFood() IS A DELETE + UPSERT
 * ----------------------------------------
 * replaceForFood() deletes all existing rows then upserts the new list.
 * That ensures:
 * - no stale rows survive basis changes
 * - we don’t have to diff updates
 * - we can change basis policy later without complicated migrations in this layer
 *
 * WHEN TO CHANGE THIS FILE
 * ------------------------
 * Change here only when basis policy changes globally.
 *
 * Examples of legit changes:
 * - stop persisting the “as-is” row when we successfully derive canonical basis
 * - derive PER_100ML for volume units beyond ML once we have density support
 * - enforce “only one basis stored” by filtering/rewriting here
 *
 * Examples of NOT-legit changes:
 * - filtering rows for UI presentation (belongs in query/usecase returning editor data)
 * - adding per-nutrient rounding/formatting (UI concern)
 *
 * ============================================================================
 */
