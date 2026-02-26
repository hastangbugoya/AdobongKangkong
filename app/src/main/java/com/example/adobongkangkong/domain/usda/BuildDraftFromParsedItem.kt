package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.domain.usda.model.UsdaFoodSearchItem
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.fromUsda
import com.example.adobongkangkong.domain.repository.NutrientRepository
import java.util.UUID

/**
 * Builds an in-memory draft (Food + nutrient rows) from a USDA `/foods/search` JSON response.
 *
 * Purpose
 * - Convert USDA search response JSON into the app’s domain models ([Food] + [FoodNutrientRow])
 *   without performing any database writes.
 * - Provide a safe, preview-ready representation that can later be:
 *   - saved,
 *   - edited,
 *   - or merged into an existing food.
 *
 * Rationale (why this exists)
 * - USDA responses often require user confirmation, editing, or collision resolution before persistence.
 * - Creating a draft object allows UI workflows such as:
 *   - preview nutrition before import,
 *   - resolve barcode conflicts,
 *   - merge USDA data into an existing food,
 *   - or create a minimal food when USDA data is incomplete.
 * - Separating "build draft" from "persist draft" ensures database integrity and clearer responsibility boundaries.
 *
 * Where USDA JSON is currently parsed in the system
 * - USDA `/foods/search` JSON is parsed by [UsdaFoodsSearchParser.parse].
 * - That parser produces [UsdaFoodSearchItem] objects used by:
 *   - ImportUsdaFoodFromSearchJsonUseCase (persistence path)
 *   - Barcode scan resolution flows
 *   - (future) manual draft editing flows using this builder
 *
 * This builder mirrors the same mapping logic used by ImportUsdaFoodFromSearchJsonUseCase,
 * but intentionally stops before persistence.
 *
 * Flow overview
 *
 * Step 1 — Parse USDA JSON
 * - Calls [UsdaFoodsSearchParser.parse] to obtain structured USDA items.
 * - Selects either:
 *   - the first item, or
 *   - a specific item matching selectedFdcId.
 *
 * Step 2 — Validate serving unit
 * - Converts USDA servingSizeUnit → [ServingUnit] using [ServingUnit.fromUsda].
 * - If unsupported, returns Blocked.
 *
 * Step 3 — Construct draft Food object
 * - id = 0 (draft placeholder; not persisted yet)
 * - stableId derived from:
 *   gtinUpc → preferred identity
 *   otherwise fdcId
 *   otherwise random UUID fallback
 *
 * - servingSize copied directly from USDA
 * - gramsPerServingUnit set only when serving unit is already grams
 * - mlPerServingUnit intentionally left null (see invariants below)
 *
 * Step 4 — Map nutrients
 * - USDA nutrientNumber → internal CSV nutrient code via [UsdaToCsvNutrientMap]
 * - CSV code → Nutrient via [NutrientRepository]
 * - Rows stored as [BasisType.USDA_REPORTED_SERVING]
 *
 * Step 5 — Return draft
 * - No persistence occurs
 * - Caller decides next action
 *
 * Parameters
 * - searchJson:
 *   Raw USDA `/foods/search` JSON response.
 *
 * - nutrients:
 *   Repository used to resolve CSV nutrient codes into internal nutrient definitions.
 *
 * - selectedFdcId:
 *   Optional specific USDA food to build draft from.
 *   If null, the first USDA result is used.
 *
 * Return
 * - [Result.Success]
 *   Contains in-memory draft:
 *   - Food object
 *   - Nutrient rows
 *
 * - [Result.Blocked]
 *   When:
 *   - no foods returned
 *   - selectedFdcId not found
 *   - unsupported serving unit
 *
 * Edge cases
 * - USDA responses may omit serving size → defaults to 1.0.
 * - Some nutrients may not map to internal CSV codes → silently skipped.
 * - Foods without GTIN use fdcId identity.
 *
 * Pitfalls / gotchas
 * - This builder does NOT normalize nutrients to PER_100G.
 * - Nutrients remain in USDA_REPORTED_SERVING basis until later normalization.
 * - This draft Food must not be logged or snapshot until persisted and normalized.
 *
 * Architectural rules
 * - No database writes allowed here.
 * - Draft objects represent import candidates only.
 * - Barcode mapping and persistence handled by separate use cases.
 * - Snapshot logs must remain immutable and must not reference draft objects.
 *
 * Current usage status
 * - Currently unused directly in production flow.
 * - Retained to support future flows:
 *   - USDA preview before save
 *   - merge-into-existing food
 *   - offline import pipelines
 *   - improved barcode resolution UX
 */
object BuildDraftFromParsedItem {

    data class Draft(
        val food: Food,
        val rows: List<FoodNutrientRow>
    )

    sealed class Result {
        data class Success(val draft: Draft) : Result()
        data class Blocked(val reason: String) : Result()
    }

    suspend fun fromSearchJson(
        searchJson: String,
        nutrients: NutrientRepository,
        selectedFdcId: Long? = null
    ): Result {
        val parsed = UsdaFoodsSearchParser.parse(searchJson)

        val item: UsdaFoodSearchItem = when (selectedFdcId) {
            null -> parsed.foods.firstOrNull()
            else -> parsed.foods.firstOrNull { it.fdcId == selectedFdcId }
        } ?: return Result.Blocked(
            if (selectedFdcId == null) "No foods in USDA response."
            else "Selected item not found in USDA response (fdcId=$selectedFdcId)."
        )

        val servingUnit = ServingUnit.fromUsda(item.servingSizeUnit)
            ?: return Result.Blocked("Unsupported USDA servingSizeUnit='${item.servingSizeUnit}'")

        val servingSize = item.servingSize ?: 1.0

        val gramsPerServingUnit: Double? =
            if (servingUnit == ServingUnit.G) servingSize.takeIf { it > 0.0 } else null

        val stableId = when {
            !item.gtinUpc.isNullOrBlank() -> "usda:gtin:${item.gtinUpc.trim()}"
            else -> "usda:fdc:${item.fdcId}"
        }.ifBlank { UUID.randomUUID().toString() }

        val brand: String? =
            item.brandName?.trim().takeIf { !it.isNullOrBlank() }
                ?: item.brandOwner?.trim().takeIf { !it.isNullOrBlank() }

        val normalizedName = item.description
            ?.trim()
            ?.toTitleCase()
            .orEmpty()
            .ifBlank { "Unnamed USDA Food" }

        val food = Food(
            id = 0L,
            name = normalizedName,
            servingSize = servingSize,
            servingUnit = servingUnit,
            servingsPerPackage = null,
            gramsPerServingUnit = gramsPerServingUnit,
            mlPerServingUnit = null,
            stableId = stableId,
            brand = brand,
            isRecipe = false,
            isLowSodium = null,
        )

        val servingRows = mutableListOf<FoodNutrientRow>()
        for (n in item.foodNutrients) {

            val usdaNumber = n.nutrientNumber ?: continue
            val csvCode = UsdaToCsvNutrientMap.byUsdaNumber[usdaNumber] ?: continue

            val nutrient = nutrients.getByCode(csvCode) ?: continue
            val amt = n.value ?: continue

            servingRows += FoodNutrientRow(
                nutrient = nutrient,
                amount = amt,
                basisType = BasisType.USDA_REPORTED_SERVING,
                basisGrams = null
            )
        }

        return Result.Success(
            Draft(
                food = food,
                rows = servingRows
            )
        )
    }

    private fun String.toTitleCase(): String =
        lowercase()
            .split(("\\s+"))
            .joinToString(" ") {
                it.replaceFirstChar { c -> c.uppercase() }
            }
}

/**
 * ===== Bottom KDoc (for future AI assistant) =====
 *
 * Invariants (must never change)
 * - This builder must NEVER write to database.
 * - Returned Food.id must remain 0L (draft state).
 * - Nutrient rows must remain BasisType.USDA_REPORTED_SERVING.
 * - stableId must remain deterministic when GTIN or FDC ID exists.
 *
 * mlPerServingUnit rules (critical)
 * - Must remain null unless explicitly parsed from USDA householdServingFullText.
 * - Never infer volume from grams.
 * - Never guess density.
 *
 * Authority and identity rules
 * - GTIN is strongest identity signal.
 * - FDC ID used when GTIN unavailable.
 * - Random UUID only as last resort.
 *
 * Architectural boundaries
 * - Parsing handled by UsdaFoodsSearchParser.
 * - Nutrient resolution handled by NutrientRepository.
 * - Persistence handled by ImportUsdaFoodFromSearchJsonUseCase or future save use case.
 *
 * Logging and snapshot rules
 * - Draft foods must never be logged directly.
 * - Draft must be persisted and normalized before becoming loggable.
 *
 * Migration notes (future USDA parsing improvements)
 * - If parsing householdServingFullText, also populate:
 *   mlPerServingUnit
 *   servingUnit conversion
 *   consistent basis conversion
 *
 * Performance considerations
 * - Single pass over USDA nutrients.
 * - No DB writes.
 *
 * Maintenance recommendations
 * - Keep mapping logic identical to ImportUsdaFoodFromSearchJsonUseCase to prevent divergence.
 * - If USDA parser changes structure, update both import and draft builder simultaneously.
 */