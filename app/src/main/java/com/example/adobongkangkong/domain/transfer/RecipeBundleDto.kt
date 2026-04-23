package com.example.adobongkangkong.domain.transfer

/**
 * Self-contained recipe transfer bundle for email/file export and import.
 *
 * Design goals:
 * - No raw DB ids across transfer boundaries
 * - StableId-based reconciliation only
 * - Safe for partial / malformed import flows
 * - Enough bundled food data to reconstruct missing foods on recipient side
 *
 * Notes:
 * - The recipe itself is identified by the backing recipe food's stableId.
 * - Ingredient references use food stableId, never local DB ids.
 * - Bundled foods are de-duplicated by stableId and may be incomplete; import
 *   should validate each food payload before attempting reconstruction.
 * - Nutrients are expected to be canonical editor/storage values for the given
 *   [canonicalNutrientBasis] when present.
 */
data class RecipeBundleDto(
    val schemaVersion: Int = SCHEMA_VERSION_1,
    val exportedAtEpochMs: Long,
    val recipe: RecipeBundleRecipeDto,
    val ingredients: List<RecipeBundleIngredientDto>,
    val foods: List<RecipeBundleFoodDto>,
) {
    companion object {
        const val SCHEMA_VERSION_1: Int = 1
    }
}

/**
 * Transfer-safe recipe header.
 *
 * Identity:
 * - [stableId] is the stableId of the backing recipe Food row.
 *
 * Rules:
 * - No local DB ids
 * - [name] should mirror the backing recipe food name at export time
 * - [totalYieldGrams] may be null when the source recipe does not have it
 */
data class RecipeBundleRecipeDto(
    val stableId: String,
    val name: String,
    val servingsYield: Double,
    val totalYieldGrams: Double?,
)

/**
 * Transfer-safe ingredient reference.
 *
 * Rules:
 * - [foodStableId] identifies the ingredient food across devices
 * - [ingredientServings] is the recipe ingredient amount in source recipe terms
 * - Import should ignore or report rows with blank stableId or non-positive
 *   servings rather than crashing
 */
data class RecipeBundleIngredientDto(
    val foodStableId: String,
    val ingredientServings: Double?,
)

/**
 * Bundled food payload used to reconstruct missing foods during import.
 *
 * Rules:
 * - [stableId] is the cross-device identity key
 * - [isRecipe] should normally be false for ingredient foods
 * - [canonicalNutrientBasis] describes how [nutrients] should be interpreted
 * - Missing or incomplete fields are allowed so import can degrade gracefully
 *
 * USDA metadata:
 * - Preserved when available to help future reconciliation / traceability
 * - Not required for successful import
 */
data class RecipeBundleFoodDto(
    val stableId: String,
    val name: String,
    val brand: String? = null,

    val servingSize: Double = 1.0,
    val servingUnit: String,

    val gramsPerServingUnit: Double? = null,
    val mlPerServingUnit: Double? = null,
    val servingsPerPackage: Double? = null,

    val isRecipe: Boolean = false,
    val isLowSodium: Boolean? = null,

    val usdaFdcId: Long? = null,
    val usdaGtinUpc: String? = null,
    val usdaPublishedDate: String? = null,
    val usdaModifiedDate: String? = null,
    val usdaServingSize: Double? = null,
    val usdaServingUnit: String? = null,
    val householdServingText: String? = null,

    /**
     * Canonical basis of the bundled nutrient amounts, when known.
     *
     * Null means:
     * - nutrient payload is missing, incomplete, or informational only
     * - import should not assume it can reconstruct food nutrients safely
     */
    val canonicalNutrientBasis: RecipeBundleFoodNutrientBasis? = null,

    /**
     * Canonical nutrient payload for this food.
     *
     * Each entry is keyed by nutrient code to avoid transfer of local nutrient ids.
     * Amount interpretation depends on [canonicalNutrientBasis].
     */
    val nutrients: List<RecipeBundleFoodNutrientDto> = emptyList(),
)

/**
 * Canonical nutrient basis for bundled food nutrient values.
 *
 * Expected meanings:
 * - [PER_100G]: amounts are canonical per 100 grams
 * - [PER_100ML]: amounts are canonical per 100 milliliters
 */
enum class RecipeBundleFoodNutrientBasis {
    PER_100G,
    PER_100ML,
}

/**
 * Transfer-safe bundled nutrient row.
 *
 * Rules:
 * - [code] must use the app's canonical nutrient code string
 * - [amount] is the canonical amount for the parent food's nutrient basis
 * - Import should ignore blank codes and otherwise malformed rows safely
 */
data class RecipeBundleFoodNutrientDto(
    val code: String,
    val amount: Double,
)