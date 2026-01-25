package com.example.adobongkangkong.domain.importing

import com.example.adobongkangkong.data.csvimport.FoodsCsvImporter
import com.example.adobongkangkong.domain.importing.model.ImportProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class RunFoodsCsvImportUseCase @Inject constructor(
    private val importer: FoodsCsvImporter
) {
    fun execute(assetFileName: String = "foods.csv"): Flow<ImportProgress> = flow {
        val totalRows = importer.peekRowCount(assetFileName)

        emit(
            ImportProgress(
                totalRows = totalRows,
                processedRows = 0,
                insertedFoods = 0,
                warnings = 0,
                errors = 0
            )
        )

        val result = importer.importFromAssets(
            assetFileName = assetFileName,
            skipIfFoodsExist = false
        )

        emit(
            ImportProgress(
                totalRows = totalRows,
                processedRows = totalRows,
                insertedFoods = result.foodsInserted,
                warnings = result.warningCount,
                errors = result.errorCount
            )
        )
    }
}
