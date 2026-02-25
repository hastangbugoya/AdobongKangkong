package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

/**
 * FoodEditorData
 *
 * Simple aggregation container for the Food Editor screen.
 *
 * ## Fields
 * @property food The [Food] being edited, or null when creating a new food.
 * @property nutrients The nutrient rows associated with the food. Empty when creating a new food
 *                     or when the food has no nutrient rows.
 */
data class FoodEditorData(
    val food: Food?,
    val nutrients: List<FoodNutrientRow>
)

/**
 * GetFoodEditorDataUseCase
 *
 * ## Purpose
 * Loads the minimum data required to render the Food Editor screen:
 * - The [Food] (if editing an existing food), and
 * - The list of [FoodNutrientRow] records for that food.
 *
 * ## Rationale
 * The Food Editor UI needs a single "bundle" of data to initialize fields and render nutrient rows
 * without performing multiple independent repository calls at the UI layer.
 * Centralizing this fetch keeps UI code thin and makes the fetch behavior testable.
 *
 * ## Behavior
 * - If [foodId] is null:
 *   - returns [FoodEditorData] with `food = null` and `nutrients = emptyList()`
 *   - This corresponds to "create new food" mode.
 * - If [foodId] is non-null:
 *   - fetches the [Food] via [FoodRepository.getById]
 *   - fetches nutrients via [FoodNutrientRepository.getForFood]
 *   - returns both in a single [FoodEditorData]
 *
 * ## Parameters
 * @param foodId The database id of the food to edit, or null to create a new food.
 *
 * ## Return
 * @return [FoodEditorData] containing the (possibly null) food and its nutrient rows.
 *
 * ## Notes / pitfalls
 * - If the food row is missing (deleted, bad id, race), [food] may be null while [nutrients] still
 *   returns rows depending on repository behavior. The UI should treat `food == null` as "not found"
 *   (or fallback to create mode) and should not assume nutrients imply the food exists.
 * - This use case performs no joins and no additional enrichment (brands, aliases, USDA metadata
 *   resolution, etc.). It returns exactly what the repositories provide.
 * - This use case is intentionally synchronous (single suspend function). If you later want the Food
 *   Editor to live-update as nutrients change, create a separate `ObserveFoodEditorDataUseCase`
 *   returning `Flow<FoodEditorData>` rather than changing this behavior silently.
 */
class GetFoodEditorDataUseCase @Inject constructor(
    private val foods: FoodRepository,
    private val foodNutrients: FoodNutrientRepository
) {

    suspend operator fun invoke(foodId: Long?): FoodEditorData {
        if (foodId == null) return FoodEditorData(food = null, nutrients = emptyList())
        val food = foods.getById(foodId)
        val rows = foodNutrients.getForFood(foodId)
        return FoodEditorData(food = food, nutrients = rows)
    }
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - Standard use case documentation format in this codebase:
 *   1) Top KDoc: dev-facing purpose/rationale/behavior/params/return/pitfalls.
 *   2) Bottom KDoc: constraints and invariants for automated edits.
 *
 * - Do NOT refactor this into Flow or add caching unless explicitly requested.
 *   Call sites likely expect a one-time load for screen initialization.
 *
 * - Keep UI concerns out:
 *   - No Compose/Android imports
 *   - No navigation decisions
 *   - No Snackbar/Toast logic
 *
 * - If adding more editor data later (e.g., barcode mappings, media, pinned nutrients):
 *   - Extend [FoodEditorData] with new fields in a backward-compatible way.
 *   - Avoid turning this into a "god fetch" that loads everything in the world.
 *
 * - If introducing "food not found" handling:
 *   - Prefer returning `food = null` (current behavior) and let UI decide how to react.
 *   - Alternatively, introduce a sealed Result type in a NEW use case to avoid breaking callers.
 */