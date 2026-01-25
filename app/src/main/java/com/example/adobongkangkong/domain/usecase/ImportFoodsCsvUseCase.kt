package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.data.csvimport.FoodsCsvImporter
import javax.inject.Inject

class ImportFoodsCsvUseCase @Inject constructor(
    private val importer: FoodsCsvImporter
) {
    suspend operator fun invoke(
        assetFileName: String = "foods.csv",
        skipIfFoodsExist: Boolean = true
    ): FoodsCsvImporter.Report {
        return importer.importFromAssets(
            assetFileName = assetFileName,
            skipIfFoodsExist = skipIfFoodsExist
        )
    }
}