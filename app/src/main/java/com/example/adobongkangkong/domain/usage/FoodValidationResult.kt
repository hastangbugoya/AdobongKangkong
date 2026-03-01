package com.example.adobongkangkong.domain.usage
/**
 * Result of validating whether a food can be used in a given context.
 *
 * This is returned by ValidateFoodForUsageUseCase and consumed by:
 *
 * - Foods list (to show fix banners)
 * - Food editor (needs-fix banner)
 * - Logging use cases (CreateLogEntryUseCase)
 * - Recipe ingredient pickers (future)
 *
 * The validator is the single source of truth for food completeness.
 */
sealed interface FoodValidationResult {

    /**
     * Food is fully usable for the requested context and amount mode.
     */
    data object Ok : FoodValidationResult

    /**
     * Food is usable, but incomplete or risky.
     *
     * WARNING is not currently heavily used, but exists to support future cases such as:
     *
     * - nutrients present but very sparse
     * - missing micronutrients but macros present
     * - approximate or inferred bridges
     *
     * UI may show yellow indicators but must NOT block logging.
     */
    data class Warning(
        val reason: Reason,
        val message: String
    ) : FoodValidationResult

    /**
     * Food cannot be used for the requested operation.
     *
     * Logging, recipe usage, or serving-based entry must be blocked until fixed.
     */
    data class Blocked(
        val reason: Reason,
        val message: String
    ) : FoodValidationResult

    /**
     * Canonical reasons explaining why validation failed or warned.
     *
     * These reasons are domain-level invariants and must remain stable because they
     * may be used by UI, analytics, migrations, or future automation.
     *
     * DO NOT overload meanings. Add new enum values instead.
     */
    enum class Reason {

        /**
         * Food has no nutrition snapshot at all.
         *
         * This means the snapshot pipeline could not produce canonical nutrition maps.
         *
         * Causes:
         *
         * - Newly created food with no nutrients saved
         * - Snapshot repository failed
         * - Recipe batch snapshot missing
         *
         * Effects:
         *
         * Logging must be blocked.
         *
         * Example:
         *
         * User creates a food "My Homemade Sauce" but never enters nutrients.
         */
        MissingSnapshot,

        /**
         * Snapshot exists but contains no usable nutrient basis.
         *
         * Both are missing or empty:
         *
         * nutrientsPerGram == null OR empty
         * nutrientsPerMilliliter == null OR empty
         *
         * This means nutrition math cannot be performed.
         *
         * Effects:
         *
         * Logging and recipe usage must be blocked.
         *
         * Example:
         *
         * Imported USDA food missing nutrient rows due to parsing issue.
         */
        MissingNutrients,

        /**
         * Nutrient basis exists, but all values are zero.
         *
         * This indicates incomplete or placeholder data.
         *
         * Zero-only nutrient maps cannot safely represent real foods.
         *
         * Effects:
         *
         * Logging and recipe usage must be blocked.
         *
         * Example:
         *
         * Calories=0, Protein=0, Fat=0, Carbs=0 for all nutrients.
         */
        AllNutrientsZero,

        /**
         * Serving-based entry requires grams-per-serving but none exists.
         *
         * Triggered when:
         *
         * - servingUnit is container-type or ambiguous (CAN, BOTTLE, SERVING, etc)
         * - amountInput is ByServings
         * - gramsPerServingUnit is null or <= 0
         * - AND food is not volume-grounded
         *
         * Volume-grounded foods do NOT require grams bridge.
         *
         * Effects:
         *
         * Logging by servings must be blocked.
         *
         * Example:
         *
         * ServingUnit = CAN
         * gramsPerServingUnit = null
         * mlPerServingUnit = null
         *
         * System cannot determine grams represented by "1 can".
         */
        MissingGramsPerServing,

        /**
         * Volume-based food cannot compute milliliters from servings.
         *
         * Triggered when:
         *
         * - snapshot has only nutrientsPerMilliliter
         * - amountInput is ByServings
         * - servingUnit is not deterministic volume
         * - mlPerServingUnit is missing
         *
         * Effects:
         *
         * Logging by servings must be blocked.
         *
         * Example:
         *
         * ServingUnit = CAN
         * mlPerServingUnit = null
         * snapshot only has nutrientsPerMilliliter
         *
         * System cannot determine how many mL "1 can" represents.
         */
        MissingMlPerServing,

        /**
         * Food has only volume-based nutrients, but user attempted gram-based logging.
         *
         * Triggered when:
         *
         * nutrientsPerMilliliter exists
         * nutrientsPerGram missing
         * amountInput = ByGrams
         *
         * Density is unknown, so conversion would require guessing.
         *
         * Effects:
         *
         * Gram-based logging must be blocked.
         *
         * Example:
         *
         * Orange juice imported per 100 mL only.
         * User tries logging "200 grams".
         */
        BasisMismatchVolumeNeedsServings,

        /**
         * Food has only mass-based nutrients, but serving-based logging cannot compute grams.
         *
         * Triggered when:
         *
         * nutrientsPerGram exists
         * nutrientsPerMilliliter missing
         * amountInput = ByServings
         * gramsPerServingUnit missing
         * AND servingUnit is not a deterministic mass unit
         *
         * Effects:
         *
         * Serving-based logging must be blocked.
         *
         * Example:
         *
         * ServingUnit = CAN
         * gramsPerServingUnit = null
         * nutrients stored per 100 g
         *
         * System cannot determine grams in one "can".
         */
        BasisMismatchMassNeedsGrams,
        /**
         * Draft/editor only: nutrient rows exist but user has not entered numbers.
         *
         * Example:
         * Protein: ""
         * Fat: ""
         * Calories: ""
         *
         * Snapshot does not yet exist, so this must be detected from draft state.
         */
        BlankNutrients,
    }
}