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
 * Imports a USDA `/foods/search` result into the local database, canonicalizing nutrients and serving bridges.
 *
 * ============================================================
 * DATA FLOW DIAGRAM (DFD)
 * ============================================================
 *
 * USDA `/foods/search` JSON
 *        │
 *        ▼
 * UsdaFoodsSearchParser.parse()
 *        │
 *        ▼
 * Select USDA item (fdcId)
 *        │
 *        ▼
 * Build Food draft
 *   - servingSize
 *   - servingUnit
 *   - gramsPerServingUnit (mass bridge)
 *   - mlPerServingUnit (volume bridge)
 *   - stableId (gtin or fdc)
 *        │
 *        ▼
 * Map USDA nutrients → internal nutrient catalog
 *        │
 *        ▼
 * Decide whether interpretation is safe or user-confirmation is required
 *   ├─ safe → canonicalize/persist immediately
 *   └─ ambiguous → persist Food shell only, return preview so UI can ask user
 *        │
 *        ▼
 * DB transaction
 *   ├─ FoodRepository.upsert()
 *   └─ FoodNutrientRepository.replaceForFood() only after interpretation is known
 *        │
 *        ▼
 * RETURN foodId + USDA metadata (or pending interpretation preview)
 *
 * ============================================================
 *
 * Purpose
 * - Convert USDA search response JSON into a usable Food draft plus nutrient snapshot.
 * - Normalize nutrients into a canonical storage basis only when interpretation is safe.
 * - When USDA payload semantics are ambiguous, stop and require explicit user confirmation.
 *
 * Rationale (why this use case exists)
 * - Some USDA/branded search results expose a serving size while nutrient numbers may behave more
 *   like label-style per-100 values.
 * - In those cases the app must not silently force PER_100G / PER_100ML.
 * - This use case now supports a two-step flow:
 *   1) import/adopt enough Food data to continue
 *   2) ask user how to interpret nutrients
 *   3) only then persist nutrient rows
 *
 * Core invariants enforced here
 *
 * ONE-basis rule
 * - Exactly one row per (foodId, nutrientId).
 * - Never store multiple basis rows for same nutrient.
 *
 * Mass grounding rule
 * - If USDA serving unit is mass-based OR grams bridge exists:
 *     mass grounding is available
 *
 * Volume grounding rule
 * - If USDA serving unit is volume-based OR mL bridge exists:
 *     volume grounding is available
 *
 * Raw fallback rule
 * - If neither mass nor volume grounding exists:
 *     store USDA_REPORTED_SERVING only.
 *
 * Absolute safety rules
 * - NEVER infer grams from mL.
 * - NEVER infer mL from grams.
 * - NEVER guess density.
 *
 * User-confirmation rule
 * - If the USDA payload appears ambiguous for a grounded item and no explicit interpretation was
 *   provided, persist the Food shell only and return a prompt payload.
 * - Do NOT persist nutrient rows until interpretation is explicit.
 *
 * Revive-on-import behavior
 * - If a food with same usdaFdcId already exists:
 *     reuse its row id and stableId.
 *
 * This prevents:
 * - duplicate foods
 * - broken log references
 *
 * Household serving bridge logic
 *
 * USDA example:
 *
 *     servingSize = 473
 *     servingUnit = MLT
 *     householdServingFullText = "1 can (473 mL)"
 *
 * Stored as:
 *
 *     servingSize = 1
 *     servingUnit = CAN
 *     mlPerServingUnit = 473
 *
 * This preserves:
 *
 * UI display:
 *     "1 can"
 *
 * mathematical truth:
 *     1 can = 473 mL
 *
 * Interpretation flow
 *
 * Step 1 — Parse USDA JSON
 * Uses UsdaFoodsSearchParser.
 *
 * Step 2 — Select target USDA item
 * Either first item or selectedFdcId.
 *
 * Step 3 — Detect revive scenario
 * Existing usdaFdcId → reuse id and stableId.
 *
 * Step 4 — Resolve serving model
 * Determine:
 *     servingSize
 *     servingUnit
 *     gramsPerServingUnit bridge
 *     mlPerServingUnit bridge
 *
 * Step 5 — Map USDA nutrients
 * USDA nutrientNumber → CSV code → internal nutrient.
 *
 * Step 6 — Decide interpretation handling
 * - safe immediate canonicalization
 * - or pending user choice
 *
 * Step 7 — Transactional persistence
 * Upsert FoodEntity.
 * Persist nutrient rows only when interpretation is final.
 *
 * Parameters
 * - searchJson:
 *   USDA foods/search JSON string.
 *
 * - selectedFdcId:
 *   Optional specific USDA food to import.
 *
 * - forcedInterpretation:
 *   Optional explicit interpretation chosen by the user for an otherwise ambiguous item.
 *
 * Return
 * - Success:
 *   Food imported and nutrient interpretation finalized.
 *
 * - NeedsInterpretationChoice:
 *   Food shell persisted, but nutrient rows were intentionally not finalized yet.
 *
 * - Blocked:
 *   Unsupported serving unit
 *   USDA parsing/select failure
 *   Invalid forced interpretation for available grounding
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
            itemFdcId = item.fdcId,
            food = food,
            rawServingSize = rawServingSize,
            rawServingUnit = rawServingUnit,
            servingRows = servingRows,
            isGenericUsdaItem = isGenericUsdaItem
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
                fat = amountForAnyCode(servingRows, "FAT_G")
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

    private fun shouldRequireInterpretationPrompt(
        itemFdcId: Long,
        food: Food,
        rawServingSize: Double,
        rawServingUnit: ServingUnit,
        servingRows: List<FoodNutrientRow>,
        isGenericUsdaItem: Boolean
    ): Boolean {
        if (servingRows.isEmpty()) return false
        if (!isMassGrounded(food) && !isVolumeGrounded(food)) return false

        // Generic/foundation-ish items are usually safer to canonicalize directly, especially when
        // USDA is effectively already giving us standard-basis data.
        if (isGenericUsdaItem) return false

        val servingLooksAlreadyPer100 = when {
            rawUnitLooksPer100Mass(rawServingUnit, rawServingSize) -> true
            rawUnitLooksPer100Volume(rawServingUnit, rawServingSize) -> true
            else -> false
        }

        if (servingLooksAlreadyPer100) return false

        // Strong ambiguity signal for mass-grounded foods:
        // if macro grams exceed serving mass, the USDA values cannot literally be per-serving.
        val gramsPerServing = computeGramsPerServing(food)
        val macroGramTotal = listOfNotNull(
            amountForAnyCode(servingRows, "CARBS_G"),
            amountForAnyCode(servingRows, "PROTEIN_G"),
            amountForAnyCode(servingRows, "FAT_G")
        ).sum()

        val impossibleAsPerServingForMass = gramsPerServing != null &&
                gramsPerServing > 0.0 &&
                macroGramTotal > (gramsPerServing + 0.5)

        if (impossibleAsPerServingForMass) {
            Log.w(
                "USDA_IMPORT",
                "Ambiguous USDA nutrient interpretation detected for fdcId=$itemFdcId: " +
                        "macroGramTotal=$macroGramTotal gramsPerServing=$gramsPerServing"
            )
            return true
        }

        // Conservative branded-item rule:
        // if a branded item is grounded and USDA's raw serving is not a clean 100 g / 100 mL basis,
        // we should not silently force per-serving vs per-100 interpretation.
        return true
    }

    private fun rawUnitLooksPer100Mass(rawUnit: ServingUnit, rawServingSize: Double): Boolean {
        if (!rawUnit.isMassUnit()) return false
        val grams = rawUnit.toGrams(rawServingSize) ?: return false
        return kotlin.math.abs(grams - 100.0) < 0.001
    }

    private fun rawUnitLooksPer100Volume(rawUnit: ServingUnit, rawServingSize: Double): Boolean {
        if (!rawUnit.isVolumeUnit()) return false
        val ml = rawUnit.toMilliliters(rawServingSize) ?: return false
        return kotlin.math.abs(ml - 100.0) < 0.001
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

/**
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Invariants (must never change)
 * - Exactly ONE nutrient row per (foodId, nutrientId) is persisted.
 * - Canonical basis after interpretation is finalized:
 *   - PER_100G when user/safe logic resolves to per-100 and food is mass-grounded
 *   - PER_100ML when user/safe logic resolves to per-100 and food is volume-grounded
 *   - USDA_REPORTED_SERVING when user chooses per-serving or when no grounding exists
 * - Never infer density; never convert grams <-> mL.
 * - Preserve stableId on revive to avoid breaking log references.
 * - All writes must occur inside a single DB transaction.
 * - Deterministic serving units must not persist redundant bridges:
 *   - mass units -> gramsPerServingUnit = null
 *   - volume units -> mlPerServingUnit = null
 *
 * Critical behavior added for ambiguous USDA items
 * - This use case may now stop after persisting the Food shell and return
 *   Result.NeedsInterpretationChoice.
 * - In that result path, nutrient rows are intentionally NOT persisted yet.
 * - Caller must ask user whether USDA numbers should be interpreted as:
 *   - per-100 style
 *   - per-serving style
 * - Caller can then invoke this use case again with forcedInterpretation to finalize rows.
 *
 * Interpretation semantics
 * - PER_SERVING_STYLE means the USDA raw numbers are treated as per-serving values and may be
 *   canonicalized to per-100 only when the importer itself chooses that path safely.
 * - PER_100_STYLE means the USDA raw numbers are already per-100-style values.
 *   In that path, this use case must relabel basis to PER_100G / PER_100ML without rescaling the amounts.
 *
 * Do not refactor notes
 * - Keep grounding decision order: mass grounding takes precedence over volume grounding.
 * - Keep revive-on-import logic (unique usdaFdcId constraint safety).
 * - Keep groupBy(nutrient.id).firstOrNull() de-dupe behavior unless you also update DB constraints and readers.
 * - Generic USDA items with missing servingSizeUnit may default to 100 g mass-grounded import.
 *   This is intentional to avoid blocking Foundation/generic foods that are effectively reported on a 100 g basis.
 *
 * Architectural boundaries
 * - This use case is a domain/persistence orchestration boundary:
 *   - parsing + mapping is domain logic
 *   - writes happen through repositories inside a transaction
 * - InterpretationChoice is defined here intentionally so domain does not depend on UI-layer enums.
 *
 * Migration notes (KMP / time APIs)
 * - This file currently depends on android.util.Log; replace with injected logger for KMP.
 *
 * Performance considerations
 * - Conversion is in-memory; DB writes are batched via replaceForFood.
 *
 * Maintenance recommendations
 * - If title casing changes, audit UI snapshots (names/brands will change).
 * - Consider a single shared title-case util if multiple USDA import paths need identical behavior.
 */