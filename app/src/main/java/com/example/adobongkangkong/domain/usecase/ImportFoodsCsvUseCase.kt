package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.csvimport.FoodsCsvImporter
import javax.inject.Inject
import android.util.Log

/**
 * ImportFoodsCsvUseCase
 *
 * ## Purpose
 * Imports food and nutrient data from a bundled CSV asset file into the local database.
 *
 * ## Rationale
 * This use case provides a controlled entry point for bulk food imports, typically used for:
 * - Initial database seeding (e.g., first app launch),
 * - Debug/testing environments,
 * - Manual re-import operations when explicitly triggered.
 *
 * Encapsulating CSV import logic in a use case:
 * - Keeps UI free of importer details,
 * - Centralizes logging and reporting,
 * - Preserves a clean boundary between orchestration and parsing logic.
 *
 * ## Behavior
 * - Delegates actual CSV parsing and DB writes to [FoodsCsvImporter].
 * - Emits debug logs before and after the import.
 * - Returns a structured [FoodsCsvImporter.Report] containing:
 *   - runId
 *   - foodsInserted
 *   - nutrientsInserted
 *   - foodNutrientsInserted
 *   - skippedRows
 *   - warningCount
 *   - errorCount
 *
 * ## Parameters
 * @param assetFileName Name of the asset CSV file. Defaults to `"foods.csv"`.
 * @param skipIfFoodsExist If true, importer may skip execution when food records already exist.
 *
 * ## Return
 * @return [FoodsCsvImporter.Report] summarizing the import operation.
 *
 * ## Assumptions / rules
 * - Import idempotency and skip logic are handled by [FoodsCsvImporter].
 * - This use case does not interpret warnings/errors beyond logging them.
 * - Database transaction integrity is the responsibility of the importer layer.
 *
 * ## Edges / scenarios
 * - If the asset file is missing or malformed, importer determines error behavior.
 * - If `skipIfFoodsExist` is true and foods already exist, importer may return a no-op report.
 * - Logging tag is intentionally stable ("Meow") for debugging consistency.
 */
class ImportFoodsCsvUseCase @Inject constructor(
    private val importer: FoodsCsvImporter
) {
    companion object {
        private const val TAG = "Meow"
    }

    suspend operator fun invoke(
        assetFileName: String = "foods.csv",
        skipIfFoodsExist: Boolean = true
    ): FoodsCsvImporter.Report {
        Log.d(TAG, "invoke(assetFileName='$assetFileName', skipIfFoodsExist=$skipIfFoodsExist)")
        val report = importer.importFromAssets(
            assetFileName = assetFileName,
            skipIfFoodsExist = skipIfFoodsExist
        )
        Log.d(
            TAG,
            "ImportFoodsCsvUseCase > report runId=${report.runId} foodsInserted=${report.foodsInserted} nutrientsInserted=${report.nutrientsInserted} foodNutrientsInserted=${report.foodNutrientsInserted} skippedRows=${report.skippedRows} warningCount=${report.warningCount} errorCount=${report.errorCount}"
        )
        return report
    }
}

/**
 * FUTURE AI ASSISTANT NOTES
 *
 * - Standard documentation pattern:
 *   1) Top KDoc for devs (purpose, rationale, params, behavior, edges).
 *   2) Bottom KDoc for AI constraints and invariants.
 *
 * - Do NOT move CSV parsing logic into this use case.
 *   It must remain a thin orchestration layer over FoodsCsvImporter.
 *
 * - Keep logging stable unless explicitly refactoring logging strategy.
 *   Some debugging flows may rely on consistent tag output.
 *
 * - This use case is Android-dependent due to:
 *   - android.util.Log
 *   - Asset-based importer
 *   If migrating to KMP, refactor logging and asset access to platform abstractions.
 *
 * - Avoid adding UI behavior (toasts, snackbars, navigation) here.
 *   This layer must remain domain/orchestration only.
 */