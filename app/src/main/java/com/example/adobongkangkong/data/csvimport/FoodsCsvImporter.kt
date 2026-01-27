package com.example.adobongkangkong.data.csvimport

import android.content.res.AssetManager
import android.util.Log
import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.BasisType
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

/**
 * Imports foods + nutrients from a CSV asset into Room.
 *
 * Design goals:
 * - **Lax import**: warn instead of failing when data is missing or malformed.
 * - **No density guessing**: volume units are permitted only when grams-per-serving is known.
 * - **Enforce correctness at point-of-use**: missing grams-per-serving is allowed in DB,
 *   but logging/recipe flows must block when that conversion is required.
 *
 * Notes:
 * - Nutrient headers are recognized via [CsvNutrientCatalog].
 * - Nutrients are upserted by code, then foods and food_nutrients are upserted by stable IDs.
 * - The importer records user-friendly [ImportIssueEntity] warnings for later review.
 */
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

    private fun normalizeHeader(raw: String): String = raw.trim().lowercase()

    private fun buildHeaderIndexNormalized(header: List<String>): Map<String, Int> =
        header.mapIndexed { idx, h -> normalizeHeader(h) to idx }.toMap()

    private fun cellNormalized(
        row: List<String>,
        headerIndex: Map<String, Int>,
        headerName: String
    ): String? {
        val idx = headerIndex[normalizeHeader(headerName)] ?: return null
        return row.getOrNull(idx)
    }

    /**
     * Returns true if this serving unit is NOT already a weight unit.
     *
     * If serving unit is "cup/tbsp/serving/etc", we need grams-per-serving to convert servings → grams.
     * If serving unit is already grams/oz/lb/mg, we do not need grams-per-serving.
     */
    private fun ServingUnit.requiresGramsPerServing(): Boolean = when (this) {
        ServingUnit.G,
        ServingUnit.MG,
        ServingUnit.OZ,
        ServingUnit.LB -> false
        else -> true
    }

    /**
     * Adds an import warning keyed by CSV row index.
     *
     * The importer keeps issues minimal (rowIndex + code) and later persists them into ImportIssueEntity.
     */
    private fun MutableList<Pair<Int, ImportIssueCode>>.warn(rowIndex: Int, code: ImportIssueCode) {
        add(rowIndex to code)
    }

    /**
     * Convenience to read all non-blank lines from an asset CSV.
     */
    private fun readNonBlankLines(assetFileName: String): List<String> =
        assets.open(assetFileName).bufferedReader().use { it.readLines() }
            .filter { it.isNotBlank() }

    /**
     * Builds a header index map from a parsed CSV header line: headerName -> columnIndex.
     */
    private fun buildHeaderIndex(header: List<String>): Map<String, Int> =
        header.mapIndexed { idx, h -> h.trim() to idx }.toMap()

    /**
     * Reads a cell by column name using [headerIndex]. Returns null if header is missing.
     */
    private fun cell(row: List<String>, headerIndex: Map<String, Int>, col: String): String? {
        val idx = headerIndex[col] ?: return null
        return row.getOrNull(idx)
    }

    /**
     * Determines the stored nutrient basis for this row.
     *
     * Rule:
     * - If grams-per-serving is known → store nutrients PER_SERVING (CSV values are interpreted per serving).
     * - Otherwise → store nutrients PER_100G (CSV values are interpreted per 100g).
     *
     * This keeps the DB semantically unambiguous and allows domain to normalize to per-gram later.
     */
    private fun decideBasisType(gramsPerServing: Double?): BasisType =
        if (gramsPerServing != null && gramsPerServing > 0.0) BasisType.PER_SERVING else BasisType.PER_100G

    /**
     * Returns the list of column indices for a given header name.
     * Used for handling duplicate columns such as "Cu".
     */
    private fun findHeaderPositions(header: List<String>, headerName: String): List<Int> =
        header.mapIndexedNotNull { idx, h -> if (h.trim() == headerName) idx else null }

    /**
     * Picks the copper (Cu) value for a row when duplicate Cu columns exist:
     * - Take the first non-empty value.
     * - If there are 2+ non-empty values and they differ, record a warning.
     */
    private fun pickCuValue(
        row: List<String>,
        cuPositions: List<Int>,
        rowIndex: Int,
        issues: MutableList<Pair<Int, ImportIssueCode>>
    ): String? {
        val rawValues = cuPositions.mapNotNull { pos -> row.getOrNull(pos)?.trim() }
        val nonEmpty = rawValues.filter { it.isNotEmpty() }

        if (nonEmpty.size >= 2 && nonEmpty[0] != nonEmpty[1]) {
            issues.warn(rowIndex, ImportIssueCode.DUPLICATE_NUTRIENT_COLUMN_RESOLVED)
        }

        return nonEmpty.firstOrNull()
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

        val lines = readNonBlankLines(assetFileName)
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
//        val headerIndex = buildHeaderIndex(header)
        val headerIndex = buildHeaderIndexNormalized(header)

        // 1) Upsert nutrients for any headers we recognize
        val nutrientDefsByHeader = CsvNutrientCatalog.defs.associateBy { normalizeHeader(it.csvHeader) }

        val nutrientsToUpsert = header.mapNotNull { h ->
            val def = nutrientDefsByHeader[normalizeHeader(h.trim())] ?: return@mapNotNull null
            NutrientEntity(
                code = def.code,
                displayName = def.displayName,
                unit = NutrientUnit.fromDb(def.unit),
                category = NutrientCategory.fromDb(def.categoryDbValue)
            )
        }

        nutrientDao.upsertAll(nutrientsToUpsert)

        // Resolve nutrient ids by header
        val nutrientIdByHeader: Map<String, Long> = buildMap {
            for (def in CsvNutrientCatalog.defs) {
                val headerKey = def.csvHeader.trim().lowercase() // normalize to match headerIndexNormalized
                if (!headerIndex.containsKey(headerKey)) continue

                val id = nutrientDao.getIdByCode(def.code)
                if (id != null) put(headerKey, id)
            }
        }

        val notes = mutableListOf<String>()

        // Duplicate Cu handling
        val cuPositions = findHeaderPositions(header, "Cu")
        val hasCuDuplicate = cuPositions.size >= 2
        if (hasCuDuplicate) {
            notes += "Detected duplicate 'Cu' column in CSV; importer will take the FIRST non-empty Cu value."
        }

        // Issues collected as (rowIndex -> code); persisted later
        val issues = mutableListOf<Pair<Int, ImportIssueCode>>()

        // Output buffers
        val foods = ArrayList<FoodEntity>(lines.size)
        val foodNutrients = ArrayList<FoodNutrientEntity>(lines.size * 10)

        var skipped = 0
        var warnMissingGrams = 0
        var warnDuplicateCuResolved = 0 // for notes only (run warningCount remains warnMissingGrams)
        var errorCount = 0










        for (i in 1 until lines.size) {
            val row = CsvParser.parseLine(lines[i])

            val name = cellNormalized(row, headerIndex, "food")?.trim().orEmpty()
            if (name.isBlank()) {
                skipped++
                continue
            }

            val servRaw = cellNormalized(row, headerIndex, "serv")
            val weightRaw = cellNormalized(row, headerIndex, "weight")

            val servingUnit: ServingUnit = CsvUnits.parseServingUnit(servRaw)
            val parsedWeight = CsvUnits.parseWeightToGrams(weightRaw)

            // Warn but import anyway if volume-like serving unit requires grams-per-serving and it is missing.
            if (servingUnit.requiresGramsPerServing() && parsedWeight.grams == null) {
                issues.warn(i, ImportIssueCode.MISSING_GRAMS_FOR_VOLUME_UNIT)
                warnMissingGrams++
            }

            val servingSize = 1.0
            val foodId = CsvUnits.stableFoodId(name, servRaw, weightRaw)

            foods += FoodEntity(
                id = foodId,
                name = name,
                brand = null,
                servingSize = servingSize,
                servingUnit = servingUnit,
                servingsPerPackage = null,
                gramsPerServing = parsedWeight.grams, // ✅ Strawberry "144g" becomes 144.0 now
                isRecipe = false
            )

            val basisType = decideBasisType(parsedWeight.grams)

            for ((headerKey, nutrientId) in nutrientIdByHeader) {
                val raw = cellNormalized(row, headerIndex, headerKey) ?: continue
                val amount = parseAmount(raw) ?: continue

                // Handle duplicate Cu columns by choosing the first non-empty Cu value
                val finalAmount = if (headerKey == "cu" && hasCuDuplicate) {
                    val chosen = pickCuValue(row, cuPositions, i, issues)
                    if (
                        issues.lastOrNull()?.first == i &&
                        issues.lastOrNull()?.second == ImportIssueCode.DUPLICATE_NUTRIENT_COLUMN_RESOLVED
                    ) {
                        warnDuplicateCuResolved++
                    }
                    parseAmount(chosen ?: "") // chosen is a string, parse it
                } else {
                    amount
                } ?: continue

                foodNutrients += FoodNutrientEntity(
                    foodId = foodId,
                    nutrientId = nutrientId,
                    nutrientAmountPerBasis = finalAmount,
                    basisType = basisType
                )
            }
        }




















//        for (i in 1 until lines.size) {
//            val row = CsvParser.parseLine(lines[i])
//
//            val name = cell(row, headerIndex, "food")?.trim().orEmpty()
//            if (name.isBlank()) {
//                skipped++
//                continue
//            }
//
//            val servRaw = cell(row, headerIndex, "serv")
//            val weightRaw = cell(row, headerIndex, "Weight")
//
//            val servingUnit: ServingUnit = CsvUnits.parseServingUnit(servRaw)
//            val parsedWeight = CsvUnits.parseWeightToGrams(weightRaw)
//
//            // Warn but import anyway if volume-like serving unit requires grams-per-serving and it is missing.
//            if (servingUnit.requiresGramsPerServing() && parsedWeight.grams == null) {
//                issues.warn(i, ImportIssueCode.MISSING_GRAMS_FOR_VOLUME_UNIT)
//                warnMissingGrams++
//            }
//
//            val servingSize = 1.0
//            val foodId = CsvUnits.stableFoodId(name, servRaw, weightRaw)
//
//            foods += FoodEntity(
//                id = foodId,
//                name = name,
//                brand = null,
//                servingSize = servingSize,
//                servingUnit = servingUnit,
//                servingsPerPackage = null,
//                gramsPerServing = parsedWeight.grams, // may be null
//                isRecipe = false
//            )
//
//            // Decide basis once per row; apply to all nutrient cells
//            val basisType = decideBasisType(parsedWeight.grams)
//
//            for ((csvHeader, nutrientId) in nutrientIdByHeader) {
//                val raw = cellNormalized(row, headerIndex, csvHeader) ?: continue
//                val amount =parseAmount(raw) ?: continue
//                if (csvHeader == "cal" && name.contains("chicken", ignoreCase = true)) {
//                    Log.d("NUTRI_DEBUG", "Chicken cal raw='$raw' parsed=${raw.toDoubleOrNull()}")
//                }
//                val amountStr = when {
//                    csvHeader == "Cu" && hasCuDuplicate -> {
//                        val chosen = pickCuValue(row, cuPositions, i, issues)
//                        // Track note count only when a warning was actually added for this row.
//                        // (We treat this as a separate note; not part of run.warningCount.)
//                        if (issues.lastOrNull()?.first == i &&
//                            issues.lastOrNull()?.second == ImportIssueCode.DUPLICATE_NUTRIENT_COLUMN_RESOLVED
//                        ) {
//                            warnDuplicateCuResolved++
//                        }
//                        chosen
//                    }
//                    else -> cellNormalized(row, headerIndex, csvHeader)?.trim()
//                }
//
//                val amt = amountStr?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
//
//                if (csvHeader == "cal" && name.contains("chicken", ignoreCase = true)) {
//                    android.util.Log.d(
//                        "CSV_DEBUG",
//                        "Chicken cal debug: raw='$raw' parsed=$amount headerIndex(cal)=${headerIndex["cal"]}"
//                    )
//                }
//
//                if (amt == null) continue
//
//                foodNutrients += FoodNutrientEntity(
//                    foodId = foodId,
//                    nutrientId = nutrientId,
//                    nutrientAmountPerBasis = amt,
//                    basisType = basisType
//                )
//            }
//        }

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
                        message = when (code) {
                            ImportIssueCode.MISSING_GRAMS_FOR_VOLUME_UNIT ->
                                "This food uses a serving unit like cup/tbsp/serving, but its Weight (grams per serving) was missing or invalid. The food was imported, but you must set grams-per-serving before you can log or use it in recipes by servings."
                            ImportIssueCode.DUPLICATE_NUTRIENT_COLUMN_RESOLVED ->
                                "The CSV had two Copper (Cu) columns with different values. The importer chose the first non-empty Copper value."
                            else -> code.name
                        },
                        rawValue = null,
                        foodId = null
                    )
                }
                importIssueDao.insertAll(issueEntities)
            }

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
                    // preserve existing behavior: warningCount currently equals warnMissingGrams only
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

    private fun parseAmount(raw: String): Double? {
        val cleaned = raw.trim()
            .replace(",", "")       // remove thousands separators
            .removeSuffix("g")
            .removeSuffix("mg")
            .removeSuffix("mcg")
            .removeSuffix("kcal")
            .trim()

        return cleaned.toDoubleOrNull()
    }

    /**
     * Fast path for UI progress: reads CSV line count (minus header) without importing.
     */
    suspend fun peekRowCount(assetFileName: String): Int = withContext(Dispatchers.IO) {
        val lines = readNonBlankLines(assetFileName)
        (lines.size - 1).coerceAtLeast(0)
    }
}
