package com.example.adobongkangkong.data.csvimport

import android.content.res.AssetManager
import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity
import com.example.adobongkangkong.data.local.db.entity.ImportIssueEntity
import com.example.adobongkangkong.data.local.db.entity.ImportRunEntity
import com.example.adobongkangkong.data.local.db.entity.NutrientEntity
import com.example.adobongkangkong.domain.importing.model.ImportIssueCode
import com.example.adobongkangkong.domain.model.NutrientCategory
import com.example.adobongkangkong.domain.model.NutrientUnit
import com.example.adobongkangkong.domain.model.ServingUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FoodsCsvImporter @Inject constructor(
    private val assets: AssetManager,
    private val db: NutriDatabase
) {
    data class Report(
        val runId: Long,
        val foodsInserted: Int,
        val nutrientsInserted: Int,
        val foodNutrientsInserted: Int,
        val skippedRows: Int,
        val warningCount: Int,
        val errorCount: Int,
        val notes: List<String>
    )

    /**
     * Returns true if this serving unit is NOT already a weight unit.
     *
     * Layman: if the serving unit is "cup/tbsp/serving/etc", we need a grams-per-serving value
     * to convert servings into grams. If the serving unit is already grams/oz/lb/mg, we don’t.
     */
    private fun ServingUnit.requiresGramsPerServing(): Boolean = when (this) {
        ServingUnit.G,
        ServingUnit.MG,
        ServingUnit.OZ,
        ServingUnit.LB -> false
        else -> true
    }

    /**
     * Helper to record a warning in a minimal way (rowIndex + code).
     *
     * We keep this minimal because your existing run/reporting already expects:
     * - a list of (rowIndex -> issueCode)
     * - a few counters (like warnMissingGrams)
     */
    private fun MutableList<Pair<Int, ImportIssueCode>>.warn(rowIndex: Int, code: ImportIssueCode) {
        add(rowIndex to code)
    }

    suspend fun importFromAssets(
        assetFileName: String,
        skipIfFoodsExist: Boolean = true
    ): Report = withContext(Dispatchers.IO) {
        val foodDao = db.foodDao()
        val nutrientDao = db.nutrientDao()
        val foodNutrientDao = db.foodNutrientDao()

        val importRunDao = db.importRunDao()
        val importIssueDao = db.importIssueDao()

        if (skipIfFoodsExist) {
            val count = foodDao.countFoods()
            if (count > 0) {
                return@withContext Report(
                    runId = -1L,
                    foodsInserted = 0,
                    nutrientsInserted = 0,
                    foodNutrientsInserted = 0,
                    skippedRows = 0,
                    warningCount = 0,
                    errorCount = 0,
                    notes = listOf("Skipped import because foods table already has $count rows.")
                )
            }
        }

        val lines = assets.open(assetFileName).bufferedReader().use { it.readLines() }
            .filter { it.isNotBlank() }

        val totalRows = (lines.size - 1).coerceAtLeast(0)
        val startedAt = System.currentTimeMillis()

        val runId = importRunDao.insert(
            ImportRunEntity(
                startedAt = startedAt,
                finishedAt = null,
                source = "assets:$assetFileName",
                totalRows = totalRows,
                foodsInserted = 0,
                nutrientsUpserted = 0,
                foodNutrientsUpserted = 0,
                skippedRows = 0,
                warningCount = 0,
                errorCount = 0
            )
        )

        if (lines.isEmpty()) {
            val finishedAt = System.currentTimeMillis()
            importRunDao.update(
                ImportRunEntity(
                    id = runId,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    source = "assets:$assetFileName",
                    totalRows = totalRows,
                    foodsInserted = 0,
                    nutrientsUpserted = 0,
                    foodNutrientsUpserted = 0,
                    skippedRows = 0,
                    warningCount = 0,
                    errorCount = 0
                )
            )
            return@withContext Report(
                runId = runId,
                foodsInserted = 0,
                nutrientsInserted = 0,
                foodNutrientsInserted = 0,
                skippedRows = 0,
                warningCount = 0,
                errorCount = 0,
                notes = listOf("CSV was empty.")
            )
        }

        val header = CsvParser.parseLine(lines.first())
        val headerIndex: Map<String, Int> =
            header.mapIndexed { idx, h -> h.trim() to idx }.toMap()

        fun cell(row: List<String>, col: String): String? {
            val idx = headerIndex[col] ?: return null
            return row.getOrNull(idx)
        }

        // 1) Upsert nutrients for any headers we recognize
        val nutrientDefsByHeader = CsvNutrientCatalog.defs.associateBy { it.csvHeader }

        val nutrientsToUpsert = header.mapNotNull { h ->
            val def = nutrientDefsByHeader[h.trim()] ?: return@mapNotNull null
            NutrientEntity(
                code = def.code,
                displayName = def.displayName,
                unit = NutrientUnit.fromDb(def.unit),
                category = NutrientCategory.fromDb(def.categoryDbValue)
            )
        }

        nutrientDao.upsertAll(nutrientsToUpsert)

        // Resolve nutrient ids by code
        val nutrientIdByHeader: Map<String, Long> = buildMap {
            for (def in CsvNutrientCatalog.defs) {
                if (!headerIndex.containsKey(def.csvHeader)) continue
                val id = nutrientDao.getIdByCode(def.code)
                if (id != null) put(def.csvHeader, id)
            }
        }

        val notes = mutableListOf<String>()

        // 2) Parse foods + food_nutrients
        val foods = ArrayList<FoodEntity>(lines.size)
        val foodNutrients = ArrayList<FoodNutrientEntity>(lines.size * 10)
        var skipped = 0

        // Copper duplicate handling
        val cuPositions = header.mapIndexedNotNull { idx, h -> if (h.trim() == "Cu") idx else null }
        val hasCuDuplicate = cuPositions.size >= 2
        if (hasCuDuplicate) {
            notes += "Detected duplicate 'Cu' column in CSV; importer will take the FIRST non-empty Cu value."
        }

        // Keep issues as (rowIndex -> code) for minimal change
        val issues = mutableListOf<Pair<Int, ImportIssueCode>>() // rowIndex -> code

        /**
         * WARNING CONDITION:
         * Some foods use a serving unit that is NOT a weight unit (ex: cup, tbsp, serving).
         * If grams-per-serving is missing, we still import the food but mark it as incomplete.
         *
         * Layman: "We imported the food, but you must set its weight before you can log/recipe it by servings."
         */
        var warnMissingGrams = 0

        /**
         * ERROR CONDITION (reserved):
         * You currently skip missing food names (row dropped), but you are not counting it as an error yet.
         * If you later want to count it as an error, increment errorCount and persist an ImportIssue.
         */
        var errorCount = 0

        /**
         * WARNING CONDITION:
         * Duplicate Cu columns exist in the CSV and the importer must pick one.
         *
         * Layman: "The file had two Copper columns; we chose one value."
         */
        var warnDuplicateCuResolved = 0

        for (i in 1 until lines.size) {
            val row = CsvParser.parseLine(lines[i])

            /**
             * ERROR CONDITION:
             * Missing food name.
             *
             * Layman: "This row has no food name, so we can't import it."
             *
             * Current behavior: skip the row (no import).
             * (You can optionally record an ImportIssue later if you want.)
             */
            val name = cell(row, "food")?.trim().orEmpty()
            if (name.isBlank()) {
                skipped++
                // Optional future: issues.warn(i, ImportIssueCode.MISSING_FOOD_NAME)
                // Optional future: errorCount++
                continue
            }

            val servRaw = cell(row, "serv")
            val weightRaw = cell(row, "Weight")

            // Serving unit parse (CsvUnits decides default/fallback behavior)
            val servingUnit: ServingUnit = CsvUnits.parseServingUnit(servRaw)

            // Weight parse: parsedWeight.grams may be null if missing/invalid
            val parsedWeight = CsvUnits.parseWeightToGrams(weightRaw)

            /**
             * WARNING CONDITION (the one you were looking for):
             * Non-weight serving unit requires grams-per-serving, but Weight is missing/invalid.
             *
             * Layman: "We imported the food, but it doesn't know how many grams are in one serving."
             *
             * IMPORTANT: we DO NOT skip the row.
             * We keep gramsPerServing = null and rely on point-of-use blocking (ServingPolicy).
             */
            if (servingUnit.requiresGramsPerServing() && parsedWeight.grams == null) {
                issues.warn(i, ImportIssueCode.MISSING_GRAMS_FOR_VOLUME_UNIT)
                warnMissingGrams++ // keep your counter exactly as requested
            }

            val servingSize = 1.0
            val foodId = CsvUnits.stableFoodId(name, servRaw, weightRaw)

            val foodEntity = FoodEntity(
                id = foodId,
                name = name,
                brand = null,
                servingSize = servingSize,
                servingUnit = servingUnit,
                servingsPerPackage = null,
                gramsPerServing = parsedWeight.grams, // may be null
                isRecipe = false
            )
            foods.add(foodEntity)

            for ((csvHeader, nutrientId) in nutrientIdByHeader) {
                val amountStr = when {
                    csvHeader == "Cu" && hasCuDuplicate -> {
                        val rawValues = cuPositions.mapNotNull { pos -> row.getOrNull(pos)?.trim() }
                        val nonEmpty = rawValues.filter { it.isNotEmpty() }

                        /**
                         * WARNING CONDITION:
                         * Two Copper columns existed. If both are non-empty and differ, we pick the first.
                         *
                         * Layman: "The file had two Copper values. We picked one."
                         */
                        if (nonEmpty.size >= 2 && nonEmpty[0] != nonEmpty[1]) {
                            issues.warn(i, ImportIssueCode.DUPLICATE_NUTRIENT_COLUMN_RESOLVED)
                            warnDuplicateCuResolved++
                        }

                        nonEmpty.firstOrNull()
                    }
                    else -> cell(row, csvHeader)?.trim()
                }

                /**
                 * WARNING CONDITION (soft):
                 * Nutrient cell isn't a valid number (blank or not parseable).
                 *
                 * Layman: "This nutrient value was missing or invalid, so it wasn't imported for this food."
                 *
                 * Current behavior: silently skip the nutrient for that row.
                 * (We can promote this to a recorded warning later if you want.)
                 */
                val amt = amountStr?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                if (amt == null) continue

                foodNutrients.add(
                    FoodNutrientEntity(
                        foodId = foodId,
                        nutrientId = nutrientId,
                        nutrientAmountPerBasis = amt,
                    )
                )
            }
        }

        if (warnMissingGrams > 0) {
            notes += "Imported $warnMissingGrams foods missing grams-per-serving for a non-weight serving unit. These foods will be BLOCKED when logging/recipes by servings until you set grams-per-serving."
            val sampleRows = issues
                .filter { it.second == ImportIssueCode.MISSING_GRAMS_FOR_VOLUME_UNIT }
                .take(5)
                .joinToString { "row ${it.first}" }
            if (sampleRows.isNotBlank()) {
                notes += "Missing grams-per-serving sample: $sampleRows"
            }
        }

        if (warnDuplicateCuResolved > 0) {
            notes += "Resolved duplicate Copper (Cu) values on $warnDuplicateCuResolved rows by choosing the first non-empty value."
        }

        val finishedAt = System.currentTimeMillis()

        db.withTransaction {
            foodDao.upsertAll(foods)
            foodNutrientDao.upsertAll(foodNutrients)

            // Persist issues for this run
            if (issues.isNotEmpty()) {
                val issueEntities = issues.map { (rowIndex, code) ->
                    ImportIssueEntity(
                        runId = runId,
                        severity = "WARNING",
                        code = code.name,
                        rowIndex = rowIndex,
                        field = when (code) {
                            ImportIssueCode.MISSING_GRAMS_FOR_VOLUME_UNIT -> "Weight"
                            ImportIssueCode.DUPLICATE_NUTRIENT_COLUMN_RESOLVED -> "Cu"
                            else -> null
                        },
                        /**
                         * Layman message per condition:
                         * keep these friendly and action-oriented.
                         */
                        message = when (code) {
                            ImportIssueCode.MISSING_GRAMS_FOR_VOLUME_UNIT ->
                                "This food uses a serving unit like cup/tbsp/serving, but its Weight (grams per serving) was missing or invalid. The food was imported, but you must set grams-per-serving before you can log or use it in recipes by servings."
                            ImportIssueCode.DUPLICATE_NUTRIENT_COLUMN_RESOLVED ->
                                "The CSV had two Copper (Cu) columns with different values. The importer chose the first non-empty Copper value."
                            else -> code.name
                        },
                        rawValue = null, // future: store the raw cell text
                        foodId = null    // future: store computed foodId for easier UI linking
                    )
                }
                importIssueDao.insertAll(issueEntities)
            }

            // Finish run summary
            importRunDao.update(
                ImportRunEntity(
                    id = runId,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    source = "assets:$assetFileName",
                    totalRows = totalRows,
                    foodsInserted = foods.size,
                    nutrientsUpserted = nutrientsToUpsert.size,
                    foodNutrientsUpserted = foodNutrients.size,
                    skippedRows = skipped,
                    // keep behavior: run warningCount currently equals warnMissingGrams
                    warningCount = warnMissingGrams,
                    errorCount = errorCount
                )
            )
        }

        Report(
            runId = runId,
            foodsInserted = foods.size,
            nutrientsInserted = nutrientsToUpsert.size,
            foodNutrientsInserted = foodNutrients.size,
            skippedRows = skipped,
            warningCount = warnMissingGrams,
            errorCount = errorCount,
            notes = notes
        )
    }

    suspend fun peekRowCount(assetFileName: String): Int = withContext(Dispatchers.IO) {
        val lines = assets.open(assetFileName)
            .bufferedReader()
            .use { it.readLines() }
            .filter { it.isNotBlank() }

        (lines.size - 1).coerceAtLeast(0) // subtract header
    }
}
