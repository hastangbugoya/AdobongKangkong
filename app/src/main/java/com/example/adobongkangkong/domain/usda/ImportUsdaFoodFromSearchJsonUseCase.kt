package com.example.adobongkangkong.domain.usda

import android.util.Log
import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.fromUsda
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.repository.FoodNutrientRepository
import com.example.adobongkangkong.domain.repository.FoodRepository
import com.example.adobongkangkong.domain.repository.NutrientRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Imports one USDA `/foods/search` item into the local food database.
 *
 * This use case is the domain boundary for USDA search-result import. It selects a USDA item,
 * builds or revives a local [Food], resolves the serving display/bridge, maps USDA nutrients into
 * AK's nutrient catalog, and decides whether nutrient rows can be saved immediately or must wait
 * for explicit user interpretation.
 *
 * ## Why interpretation is needed
 *
 * USDA branded search results can contain both a serving definition and nutrient values. The
 * nutrient values are not always obvious from the UI perspective: they may represent PER_100G /
 * PER_100ML values even though the item also has a serving size.
 *
 * Example:
 *
 * ```text
 * GTIN: 027000500439
 * Food: Hunt's Four Cheese Pasta Sauce
 * USDA serving: 0.5 cup = 126 g
 * USDA Energy value: 48
 * ```
 *
 * Correct PER_100G interpretation:
 *
 * ```text
 * 48 kcal per 100 g
 * displayed serving calories = 48 * 126 / 100 = 60.48 kcal
 * ```
 *
 * Incorrect PER_SERVING interpretation:
 *
 * ```text
 * 48 kcal per 0.5 cup serving
 * ```
 *
 * Because both interpretations are possible from the app/user perspective, ambiguous branded
 * imports use a two-step flow.
 *
 * ## Flow summary
 *
 * ### Step 1: initial import
 *
 * Caller invokes this use case without a forced interpretation:
 *
 * ```kotlin
 * importUsdaFromSearchJson(
 *     searchJson = json,
 *     selectedFdcId = selectedFdcId,
 *     forcedInterpretation = null
 * )
 * ```
 *
 * The use case parses the USDA JSON, selects the target item, builds a local [Food], maps nutrient
 * rows, and checks whether interpretation is required.
 *
 * If the item has mapped nutrient rows and valid mass/volume grounding, this returns
 * [Result.NeedsInterpretationChoice] so the user can choose PER_100 or PER_SERVING.
 *
 * If there are no mapped nutrients, or PER_100 interpretation is not safely available because
 * the food has no mass/volume grounding, this may return [Result.Success] without prompting. In that path, this use
 * case intentionally persists only the food shell:
 *
 * - name
 * - brand
 * - serving size/unit
 * - gram or mL bridge
 * - USDA FDC id / GTIN metadata
 *
 * Nutrient rows are not persisted yet. An empty `food_nutrients` table after
 * [Result.NeedsInterpretationChoice] is expected and should not be treated as a DAO/repository
 * failure.
 *
 * ### Step 2: caller shows interpretation prompt
 *
 * Any caller that receives [Result.NeedsInterpretationChoice] must preserve:
 *
 * - the original USDA search JSON
 * - the selected FDC id
 * - the preview values returned by [Result.NeedsInterpretationChoice]
 *
 * The caller must then show a user choice:
 *
 * - treat USDA nutrient values as PER_100G / PER_100ML
 * - treat USDA nutrient values as PER serving
 *
 * Do not navigate away as if the import is complete. Do not clear the pending USDA JSON before the
 * user chooses.
 *
 * Required caller state is conceptually:
 *
 * ```kotlin
 * pendingUsdaSearchJson = originalJson
 * pendingUsdaInterpretationPrompt = PendingUsdaInterpretationPromptState(...)
 * ```
 *
 * ### Step 3: user confirms interpretation
 *
 * After the user chooses, the caller invokes this same use case again with [forcedInterpretation]:
 *
 * ```kotlin
 * importUsdaFromSearchJson(
 *     searchJson = pendingUsdaSearchJson,
 *     selectedFdcId = prompt.selectedFdcId,
 *     forcedInterpretation = PER_100_STYLE or PER_SERVING_STYLE
 * )
 * ```
 *
 * This second call finalizes and persists nutrient rows.
 *
 * ## Interpretation semantics
 *
 * [InterpretationChoice.PER_100_STYLE]
 *
 * - USDA nutrient values are treated as already normalized per 100 g or per 100 mL.
 * - Values are relabeled as [BasisType.PER_100G] or [BasisType.PER_100ML].
 * - Amounts are not scaled.
 *
 * [InterpretationChoice.PER_SERVING_STYLE]
 *
 * - USDA nutrient values are treated as values for the reported serving.
 * - Rows remain [BasisType.USDA_REPORTED_SERVING] in this use case.
 *
 * ## Serving and bridge rules
 *
 * USDA often provides both a raw serving and a household serving.
 *
 * Example:
 *
 * ```text
 * servingSize = 126
 * servingSizeUnit = "g"
 * householdServingFullText = "0.5 cup"
 * ```
 *
 * AK stores this as:
 *
 * ```text
 * servingSize = 0.5
 * servingUnit = CUP
 * gramsPerServingUnit = 252.0
 * mlPerServingUnit = null
 * ```
 *
 * Meaning:
 *
 * ```text
 * 0.5 cup = 126 g
 * 1 cup = 252 g
 * ```
 *
 * Deterministic serving units do not need redundant bridges:
 *
 * - mass units already know grams per unit
 * - volume units already know mL per unit
 *
 * Non-deterministic display units such as CUP, CAN, PIECE, SERVING, or PACKAGE may need an explicit
 * gram or mL bridge when USDA provides one.
 *
 * ## Safety invariants
 *
 * - Never infer grams from mL.
 * - Never infer mL from grams.
 * - Never guess density.
 * - Mass grounding takes precedence over volume grounding.
 * - Exactly one persisted nutrient row should exist per food/nutrient after finalization.
 * - Preserve `stableId` when reviving an existing USDA food so logs and references do not break.
 * - All database writes in this use case must happen inside a transaction.
 *
 * ## Revive-on-import behavior
 *
 * If a local food already exists with the same USDA FDC id:
 *
 * - reuse the existing row id
 * - preserve its existing `stableId`
 * - update/import against that food instead of creating a duplicate
 *
 * This protects existing logs and avoids unique-constraint collisions.
 *
 * ## Caller contract
 *
 * Barcode import and direct USDA search import must both handle [Result.NeedsInterpretationChoice]
 * the same way.
 *
 * Barcode import usually has a scanned barcode in UI state. Direct USDA search import may not.
 * Therefore, finalizing nutrient interpretation must not depend on barcode mapping.
 *
 * Correct caller behavior after successful finalization:
 *
 * - if a scanned barcode exists, map it
 * - else if [Result.Success.gtinUpc] exists, map that if desired
 * - else finalize nutrients anyway and skip barcode mapping
 *
 * Nutrient persistence must never fail just because no barcode is available.
 *
 * ## Result meaning
 *
 * [Result.Success]
 * - Food metadata and nutrient rows are finalized.
 *
 * [Result.NeedsInterpretationChoice]
 * - Food shell was persisted.
 * - Nutrient rows were intentionally not persisted yet.
 * - Caller must show the interpretation prompt and rerun this use case with [forcedInterpretation].
 *
 * [Result.Blocked]
 * - Import could not continue safely.
 * - Typical causes include unsupported serving units, missing selected FDC id, or impossible
 *   interpretation for the available serving grounding.
 */
class ImportUsdaFoodFromSearchJsonUseCase @Inject constructor(
    private val db: NutriDatabase,
    private val foods: FoodRepository,
    private val foodNutrients: FoodNutrientRepository,
    private val nutrients: NutrientRepository
) {
    suspend operator fun invoke(
        searchJson: String,
        selectedFdcId: Long? = null,
        forcedInterpretation: InterpretationChoice? = null
    ): Result {
        val parsed = UsdaFoodsSearchParser.parse(searchJson)

        val item = when (selectedFdcId) {
            null -> parsed.foods.firstOrNull()
            else -> parsed.foods.firstOrNull { it.fdcId == selectedFdcId }
        } ?: return Result.Blocked(
            if (selectedFdcId == null) "No foods in USDA response."
            else "Selected item not found in USDA response (fdcId=$selectedFdcId)."
        )

        val computedStableId = when {
            !item.gtinUpc.isNullOrBlank() -> "usda:gtin:${item.gtinUpc.trim()}"
            else -> "usda:fdc:${item.fdcId}"
        }

        // ♻️ Revive-on-import (critical for UNIQUE usdaFdcId)
        val existing = db.foodDao().getByUsdaFdcId(item.fdcId)
        val revivedId = existing?.id ?: 0L
        val finalStableId = existing?.stableId ?: computedStableId

        val brand: String? =
            item.brandName?.trim()?.toTitleCase().takeIf { !it.isNullOrBlank() }
                ?: item.brandOwner?.trim().takeIf { !it.isNullOrBlank() }

        val normalizedName = item.description
            ?.trim()
            ?.toTitleCase()
            .orEmpty()
            .ifBlank { "Unnamed USDA Food" }

        val isGenericUsdaItem = item.gtinUpc.isNullOrBlank() &&
                item.brandName.isNullOrBlank() &&
                item.brandOwner.isNullOrBlank()

        val resolvedServing: Pair<Double, ServingUnit> = run {
            val parsedUnit = ServingUnit.fromUsda(item.servingSizeUnit)

            if (parsedUnit != null) {
                parsedUnit to parsedUnit // placeholder shape not used
            }

            when {
                parsedUnit != null -> {
                    val rawServingSize = item.servingSize ?: 1.0
                    rawServingSize to parsedUnit
                }

                isGenericUsdaItem -> {
                    Log.w(
                        "USDA_IMPORT",
                        "Missing USDA servingSizeUnit for generic item fdcId=${item.fdcId}; defaulting to 100 g."
                    )
                    100.0 to ServingUnit.G
                }

                else -> {
                    return Result.Blocked("Unsupported USDA servingSizeUnit='${item.servingSizeUnit}'")
                }
            }
        }

        val rawServingSize: Double = resolvedServing.first
        val rawServingUnit: ServingUnit = resolvedServing.second

        val household = parseHouseholdServing(item.householdServingFullText)

        // LOCKED-IN USDA RULE:
        // If household text is parseable, we store servingSize=household.size and servingUnit=household.unit for display.
        val finalServingSize: Double = household?.size ?: rawServingSize
        val finalServingUnit: ServingUnit = household?.unit ?: rawServingUnit

        Log.d(
            "USDA_IMPORT",
            "household=$household rawServingSize=$rawServingSize rawServingUnit=$rawServingUnit"
        )

        val rawGramsBridgePerUnit: Double? = computeGramsBridgePer1Unit(
            householdSize = household?.size,
            displayUnit = finalServingUnit,
            rawSize = rawServingSize,
            rawUnit = rawServingUnit
        )

        val rawMlBridgePerUnit: Double? = computeMlBridgePer1Unit(
            householdSize = household?.size,
            displayUnit = finalServingUnit,
            rawSize = rawServingSize,
            rawUnit = rawServingUnit
        )

        // Deterministic-unit normalization:
        // - mass units already know grams per 1 unit via ServingUnit.asG
        // - volume units already know mL per 1 unit via ServingUnit.asMl
        // Therefore we must not persist redundant bridges for those cases.
        val gramsPerServingUnit: Double? =
            if (finalServingUnit.isMassUnit()) {
                null
            } else {
                rawGramsBridgePerUnit?.takeIf { it > 0.0 }
            }

        val mlPerServingUnit: Double? =
            if (finalServingUnit.isVolumeUnit()) {
                null
            } else {
                rawMlBridgePerUnit?.takeIf { it > 0.0 }
            }

        val food = Food(
            id = revivedId,
            name = normalizedName,
            servingSize = finalServingSize,
            servingUnit = finalServingUnit,
            servingsPerPackage = null,
            gramsPerServingUnit = gramsPerServingUnit,
            mlPerServingUnit = mlPerServingUnit,
            stableId = finalStableId.ifBlank { UUID.randomUUID().toString() },
            brand = brand,
            isRecipe = false,
            isLowSodium = null,
            usdaFdcId = item.fdcId,
            usdaGtinUpc = item.gtinUpc?.trim()?.takeIf { it.isNotBlank() },
            usdaPublishedDate = item.publishedDate?.trim()?.takeIf { it.isNotBlank() },
            usdaModifiedDate = item.modifiedDate?.trim()?.takeIf { it.isNotBlank() }
        )

        val servingRows: List<FoodNutrientRow> = item.foodNutrients.mapNotNull { n ->
            val usdaNumber = n.nutrientNumber ?: return@mapNotNull null
            val csvCode = UsdaToCsvNutrientMap.byUsdaNumber[usdaNumber] ?: return@mapNotNull null

            val nutrient = nutrients.getByCode(csvCode) ?: return@mapNotNull null
            val amt = n.value ?: return@mapNotNull null

            FoodNutrientRow(
                nutrient = nutrient,
                amount = amt,
                basisType = BasisType.USDA_REPORTED_SERVING,
                basisGrams = null
            )
        }

        val requiresInterpretationPrompt = shouldRequireInterpretationPrompt(
            food = food,
            servingRows = servingRows
        )

        if (requiresInterpretationPrompt && forcedInterpretation == null) {
            val foodId = db.withTransaction {
                foods.upsert(food)
            }

            return Result.NeedsInterpretationChoice(
                foodId = foodId,
                fdcId = item.fdcId,
                gtinUpc = item.gtinUpc?.trim()?.takeIf { it.isNotBlank() },
                publishedDateIso = item.publishedDate?.trim()?.takeIf { it.isNotBlank() },
                modifiedDateIso = item.modifiedDate?.trim()?.takeIf { it.isNotBlank() },
                candidateLabel = buildCandidateLabel(
                    description = normalizedName,
                    brand = brand
                ),
                servingText = buildServingPreviewText(
                    displayServingSize = finalServingSize,
                    displayServingUnit = finalServingUnit,
                    rawServingSize = rawServingSize,
                    rawServingUnit = rawServingUnit,
                    householdServingText = item.householdServingFullText
                ),
                calories = amountForAnyCode(servingRows, "CALORIES_KCAL"),
                carbs = amountForAnyCode(servingRows, "CARBS_G"),
                protein = amountForAnyCode(servingRows, "PROTEIN_G"),
                fat = amountForAnyCode(servingRows, "FAT_G"),
                sodiumMg = amountForAnyCode(servingRows, "SODIUM_MG"),
                totalSugarG = amountForAnyCode(servingRows, "TOTAL_SUGARS_G", "SUGARS_G")
            )
        }

        val canonicalRows: List<FoodNutrientRow> = when {
            forcedInterpretation == InterpretationChoice.PER_SERVING_STYLE -> {
                dedupeRows(servingRows)
            }

            forcedInterpretation == InterpretationChoice.PER_100_STYLE -> {
                relabelAsPer100WithoutScaling(food, servingRows)
                    ?: return Result.Blocked(
                        "USDA import: cannot use per-100 interpretation because this food is not safely mass- or volume-grounded."
                    )
            }

            isMassGrounded(food) -> {
                canonicalizeAsPer100(food, servingRows)
                    ?: return Result.Blocked(
                        "USDA import: failed to compute grams-per-serving for mass-grounded food."
                    )
            }

            isVolumeGrounded(food) -> {
                canonicalizeAsPer100(food, servingRows)
                    ?: return Result.Blocked(
                        "USDA import: failed to compute ml-per-serving for volume-grounded food."
                    )
            }

            else -> dedupeRows(servingRows)
        }

        val foodId = db.withTransaction {
            val id = foods.upsert(food)
            foodNutrients.replaceForFood(id, canonicalRows)
            id
        }

        return Result.Success(
            foodId = foodId,
            fdcId = item.fdcId,
            gtinUpc = item.gtinUpc?.trim()?.takeIf { it.isNotBlank() },
            publishedDateIso = item.publishedDate?.trim()?.takeIf { it.isNotBlank() },
            modifiedDateIso = item.modifiedDate?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun buildCandidateLabel(
        description: String,
        brand: String?
    ): String {
        val safeName = description.trim()
        val safeBrand = brand?.trim().orEmpty()

        return when {
            safeName.isBlank() && safeBrand.isBlank() -> "USDA item"
            safeBrand.isBlank() -> safeName
            safeName.isBlank() -> safeBrand
            else -> "$safeName ($safeBrand)"
        }
    }

    private fun buildServingPreviewText(
        displayServingSize: Double,
        displayServingUnit: ServingUnit,
        rawServingSize: Double,
        rawServingUnit: ServingUnit,
        householdServingText: String?
    ): String? {
        val household = householdServingText?.trim().takeIf { !it.isNullOrBlank() }
        if (household != null) return household

        val display = "${trimTrailingZero(displayServingSize)} ${displayServingUnit.name}"
        val raw = "${trimTrailingZero(rawServingSize)} ${rawServingUnit.name}"

        return if (display == raw) display else "$display (USDA raw: $raw)"
    }

    private fun trimTrailingZero(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    private fun amountForAnyCode(rows: List<FoodNutrientRow>, vararg codes: String): Double? {
        if (rows.isEmpty()) return null
        return rows.firstOrNull { row ->
            codes.any { code -> row.nutrient.code.equals(code, ignoreCase = true) }
        }?.amount
    }

    private fun dedupeRows(rows: List<FoodNutrientRow>): List<FoodNutrientRow> {
        return rows
            .groupBy { it.nutrient.id }
            .mapNotNull { (_, group) -> group.firstOrNull() }
    }

    private fun canonicalizeAsPer100(
        food: Food,
        servingRows: List<FoodNutrientRow>
    ): List<FoodNutrientRow>? {
        val canonicalRows: List<FoodNutrientRow> = when {
            isMassGrounded(food) -> {
                val gramsPerServing = computeGramsPerServing(food) ?: return null
                val factor = 100.0 / gramsPerServing
                servingRows.map { r ->
                    r.copy(
                        basisType = BasisType.PER_100G,
                        amount = r.amount * factor,
                        basisGrams = 100.0,
                    )
                }
            }

            isVolumeGrounded(food) -> {
                val mlPerServing = computeMlPerServing(food) ?: return null
                val factor = 100.0 / mlPerServing
                servingRows.map { r ->
                    r.copy(
                        basisType = BasisType.PER_100ML,
                        amount = r.amount * factor,
                        basisGrams = null
                    )
                }
            }

            else -> return null
        }

        return dedupeRows(canonicalRows)
    }

    private fun relabelAsPer100WithoutScaling(
        food: Food,
        servingRows: List<FoodNutrientRow>
    ): List<FoodNutrientRow>? {
        val canonicalRows: List<FoodNutrientRow> = when {
            isMassGrounded(food) -> {
                servingRows.map { r ->
                    r.copy(
                        basisType = BasisType.PER_100G,
                        amount = r.amount,
                        basisGrams = 100.0
                    )
                }
            }

            isVolumeGrounded(food) -> {
                servingRows.map { r ->
                    r.copy(
                        basisType = BasisType.PER_100ML,
                        amount = r.amount,
                        basisGrams = null
                    )
                }
            }

            else -> return null
        }

        return dedupeRows(canonicalRows)
    }

    /**
     * Returns true when this USDA item has nutrient rows and enough serving grounding for the
     * user to choose how the raw USDA nutrient numbers should be interpreted.
     *
     * Deliberately conservative:
     * - Do not guess PER_100 versus PER_SERVING from USDA payload shape.
     * - Do not auto-canonicalize branded/search imports just because the raw serving is 100 g/mL.
     * - Do not auto-canonicalize generic items either when a valid choice exists.
     *
     * If the item has no mapped nutrient rows, there is nothing to interpret.
     * If the item has no mass or volume grounding, PER_100 interpretation is not safely available;
     * those rows fall back to USDA_REPORTED_SERVING.
     */
    private fun shouldRequireInterpretationPrompt(
        food: Food,
        servingRows: List<FoodNutrientRow>
    ): Boolean {
        if (servingRows.isEmpty()) return false
        if (!isMassGrounded(food) && !isVolumeGrounded(food)) return false
        return true
    }

    private fun isMassGrounded(food: Food): Boolean {
        return food.servingUnit.isMassUnit() || (food.gramsPerServingUnit?.takeIf { it > 0.0 } != null)
    }

    private fun isVolumeGrounded(food: Food): Boolean {
        // Must not claim volume grounding if mass grounding exists (single-grounding invariant).
        if (isMassGrounded(food)) return false
        return food.servingUnit.isVolumeUnit() || (food.mlPerServingUnit?.takeIf { it > 0.0 } != null)
    }

    private fun computeGramsPerServing(food: Food): Double? {
        // Truth-first: if an explicit bridge exists, it wins over unit-based conversions.
        val bridgedGPer1 = food.gramsPerServingUnit?.takeIf { it > 0.0 }
        val grams = when {
            bridgedGPer1 != null -> food.servingSize * bridgedGPer1
            food.servingUnit.isMassUnit() -> food.servingUnit.toGrams(food.servingSize)
            else -> null
        }
        return grams?.takeIf { it > 0.0 }
    }

    private fun computeMlPerServing(food: Food): Double? {
        // Truth-first: if an explicit bridge exists, it wins over unit-based conversions.
        // This is required for USDA liquids where servingUnit may be CUP/FLOZ/etc but USDA servingSize in mL is authoritative.
        val bridgedMlPer1 = food.mlPerServingUnit?.takeIf { it > 0.0 }
        val ml = when {
            bridgedMlPer1 != null -> food.servingSize * bridgedMlPer1
            food.servingUnit.isVolumeUnit() -> food.servingUnit.toMilliliters(food.servingSize)
            else -> null
        }
        return ml?.takeIf { it > 0.0 }
    }

    private data class HouseholdServing(
        val size: Double,
        val unit: ServingUnit
    )

    /**
     * Parses USDA householdServingFullText like:
     * - "1 can"
     * - "1 can (473 mL)"
     * - "2 tbsp"
     *
     * We only care about the leading "{number} {unitWord...}" part.
     */
    private fun parseHouseholdServing(text: String?): HouseholdServing? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()

        // Keep only the part before '(' if present: "1 can (473 mL)" -> "1 can"
        val head = trimmed.substringBefore("(").trim()

        // Very small parser: number + unit token(s)
        val parts = head.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size < 2) return null

        val size = parts[0].toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        val unitText = parts.drop(1).joinToString(" ").trim()

        val unit = ServingUnit.fromUsda(unitText) ?: return null
        return HouseholdServing(size = size, unit = unit)
    }

    private fun computeGramsBridgePer1Unit(
        householdSize: Double?,
        displayUnit: ServingUnit,
        rawSize: Double,
        rawUnit: ServingUnit
    ): Double? {
        if (householdSize == null) return null
        if (!rawUnit.isMassUnit()) return null

        // If the display unit itself is a mass unit, deterministic conversion is enough; no bridge needed.
        if (displayUnit.isMassUnit()) return null

        val gramsTotal = rawUnit.toGrams(rawSize)?.takeIf { it > 0.0 } ?: return null

        // Bridge must be per 1 display-unit.
        return (gramsTotal / householdSize).takeIf { it > 0.0 }
    }

    private fun computeMlBridgePer1Unit(
        householdSize: Double?,
        displayUnit: ServingUnit,
        rawSize: Double,
        rawUnit: ServingUnit
    ): Double? {
        if (householdSize == null) return null
        if (!rawUnit.isVolumeUnit()) return null

        // If the display unit itself is a volume unit, deterministic conversion is enough; no bridge needed.
        if (displayUnit.isVolumeUnit()) return null

        val mlTotal = rawUnit.toMilliliters(rawSize)?.takeIf { it > 0.0 } ?: return null

        // Bridge must be per 1 display-unit.
        return (mlTotal / householdSize).takeIf { it > 0.0 }
    }

    enum class InterpretationChoice {
        PER_100_STYLE,
        PER_SERVING_STYLE
    }

    sealed class Result {
        data class Success(
            val foodId: Long,
            val fdcId: Long,
            val gtinUpc: String?,
            val publishedDateIso: String?,
            val modifiedDateIso: String?
        ) : Result()

        data class NeedsInterpretationChoice(
            val foodId: Long,
            val fdcId: Long,
            val gtinUpc: String?,
            val publishedDateIso: String?,
            val modifiedDateIso: String?,
            val candidateLabel: String,
            val servingText: String?,
            val calories: Double?,
            val carbs: Double?,
            val protein: Double?,
            val fat: Double?,
            val sodiumMg: Double?,
            val totalSugarG: Double?,
        ) : Result()

        data class Blocked(val reason: String) : Result()
    }
}

/**
 * Title-cases a string for display.
 *
 * NOTE:
 * - This is intentionally simple and locale-agnostic.
 * - It preserves your existing behavior; do not “fix” it without auditing UI diffs.
 */
fun String.toTitleCase(): String =
    lowercase()
        .split(" ")
        .joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
