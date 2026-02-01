package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.csvimport.FoodsCsvImporter
import javax.inject.Inject
import android.util.Log


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
