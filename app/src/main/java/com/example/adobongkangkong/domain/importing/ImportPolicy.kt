package com.example.adobongkangkong.domain.importing

import com.example.adobongkangkong.domain.importing.model.ImportIssueCode


/**
 * ImportPolicy centralizes "what do we do when data is imperfect?"
 *
 * CSV parsing should focus on reading values.
 * Policy decides:
 *  - block vs allow
 *  - warn vs error
 *  - how to sanitize (clamp negatives, default blanks, etc.)
 *
 * Keep this conservative: avoid guessing densities or fabricating data.
 */
object ImportPolicy {

    /**
     * What to do when a food row has a volume serving unit (cup/tbsp/etc)
     * but grams-per-serving is missing/invalid.
     *
     * Your current product decision:
     * - DO NOT skip the row
     * - Keep gramsPerServing = null
     * - Emit a WARNING
     * - Enforce later at point-of-use (ServingPolicy)
     */
    val missingGramsForVolume: Decision = Decision.KeepAsWarning(
        code = ImportIssueCode.MISSING_GRAMS_FOR_VOLUME_UNIT,
        message = "Serving unit is volume-based but grams-per-serving is missing/invalid. Food will be importable but blocked at point-of-use until weight is set."
    )

    /**
     * Negative nutrient numbers: clamp to zero and warn.
     */
    val negativeNumber: Decision = Decision.ClampToZero(
        code = ImportIssueCode.NEGATIVE_NUMBER_CLAMPED,
        message = "Negative number detected and clamped to 0."
    )

    /**
     * Missing food name: skip row (this is usually not useful to keep).
     * (You can choose KeepAsError instead if you want “import anyway but unusable”.)
     */
    val missingFoodName: Decision = Decision.SkipRow(
        code = ImportIssueCode.MISSING_FOOD_NAME,
        message = "Food name missing."
    )

    /**
     * Unknown nutrient column: skip that nutrient field and warn (keep row).
     */
    val unknownNutrientColumn: Decision = Decision.SkipFieldAsWarning(
        code = ImportIssueCode.UNKNOWN_NUTRIENT_COLUMN_SKIPPED,
        message = "Unknown nutrient column skipped."
    )

    /**
     * Duplicate nutrient column (e.g. Cu appears twice): pick best/last and warn.
     */
    val duplicateNutrientColumn: Decision = Decision.KeepAsWarning(
        code = ImportIssueCode.DUPLICATE_NUTRIENT_COLUMN_RESOLVED,
        message = "Duplicate nutrient column resolved."
    )
}

/**
 * ImportPolicy outputs decisions that your importer can apply consistently.
 */
sealed interface Decision {
    val code: ImportIssueCode
    val message: String

    /** Keep row and emit WARNING */
    data class KeepAsWarning(
        override val code: ImportIssueCode,
        override val message: String
    ) : Decision

    /** Keep row and emit ERROR (row is kept but flagged as problematic) */
    data class KeepAsError(
        override val code: ImportIssueCode,
        override val message: String
    ) : Decision

    /** Skip row entirely */
    data class SkipRow(
        override val code: ImportIssueCode,
        override val message: String
    ) : Decision

    /** Clamp numeric field to zero and emit WARNING */
    data class ClampToZero(
        override val code: ImportIssueCode,
        override val message: String
    ) : Decision

    /** Skip only the field and emit WARNING */
    data class SkipFieldAsWarning(
        override val code: ImportIssueCode,
        override val message: String
    ) : Decision
}

/**
 * Issue codes that your importer/reporting understands.
 * (Use your existing enum; included here for completeness.)
 */