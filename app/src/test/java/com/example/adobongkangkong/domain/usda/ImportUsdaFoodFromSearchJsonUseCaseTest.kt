package com.example.adobongkangkong.domain.usda

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.usda.UsdaFoodsSearchParser
import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.model.FoodNutrientRow
import com.example.adobongkangkong.domain.model.Nutrient
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.model.fromUsda
import com.example.adobongkangkong.domain.model.isMassUnit
import com.example.adobongkangkong.domain.model.isVolumeUnit
import com.example.adobongkangkong.domain.model.toGrams
import com.example.adobongkangkong.domain.model.toMilliliters
import com.example.adobongkangkong.domain.nutrition.NutrientCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID
import kotlin.math.abs

/**
 * Pure JVM correctness tests for the USDA import conversion boundary:
 *
 * USDA /foods/search JSON
 *   -> parsed DTO (UsdaFoodsSearchParser)
 *   -> serving grounding rules (household text + raw mass/volume)
 *   -> canonicalization basis math (USDA_REPORTED_SERVING -> PER_100G)
 *   -> round-trip invariant:
 *        (per100g / 100) * gramsPerServing == perServing
 *
 * NOTE:
 * - We intentionally do NOT call ImportUsdaFoodFromSearchJsonUseCase.invoke()
 *   because that requires RoomDatabase.withTransaction + NutriDatabase.
 * - This test focuses on conversion correctness “up to domain food”.
 */
class ImportUsdaFoodFromSearchJsonUseCaseTest {

    // -------------------------
    // Tests
    // -------------------------

    @Test
    fun nutella_household_2_tbsp_37g_grounding_and_per100g_and_roundtrip_are_correct() {
        val json = foodsSearchJson(
            fdcId = 111L,
            description = "nutella hazelnut spread",
            gtinUpc = "009800895151",
            brandOwner = "Ferrero",
            brandName = "Nutella",
            servingSize = 37.0,
            servingSizeUnit = "g",
            householdServingFullText = "2 tbsp (37 g)",
            nutrients = listOf(
                nutrient(number = "208", value = 200.0), // calories kcal
                nutrient(number = "203", value = 2.0),   // protein g
                nutrient(number = "205", value = 21.0),  // carbs g
                nutrient(number = "204", value = 11.0),  // fat g
            )
        )

        val parsed = UsdaFoodsSearchParser.parse(json)
        val item = parsed.foods.first()

        // ---- replicate the use case serving grounding rules ----
        val rawServingUnit = ServingUnit.fromUsda(item.servingSizeUnit)
        assertNotNull("rawServingUnit should map from servingSizeUnit", rawServingUnit)

        val rawServingSize = item.servingSize ?: 1.0
        val household = parseHouseholdServing(item.householdServingFullText)

        val finalServingSize = household?.size ?: rawServingSize
        val finalServingUnit = household?.unit ?: rawServingUnit!!

        val gramsPerServingUnit = computeGramsBridgePer1Unit(
            householdSize = household?.size,
            displayUnit = finalServingUnit,
            rawSize = rawServingSize,
            rawUnit = rawServingUnit!!
        )

        val mlPerServingUnit = computeMlBridgePer1Unit(
            householdSize = household?.size,
            displayUnit = finalServingUnit,
            rawSize = rawServingSize,
            rawUnit = rawServingUnit
        )

        val food = Food(
            id = 0L,
            name = item.description?.trim()?.toTitleCase().orEmpty().ifBlank { "Unnamed USDA Food" },
            servingSize = finalServingSize,
            servingUnit = finalServingUnit,
            servingsPerPackage = null,
            gramsPerServingUnit = gramsPerServingUnit,
            mlPerServingUnit = mlPerServingUnit,
            stableId = UUID.randomUUID().toString(),
            brand = item.brandName?.trim()?.toTitleCase().takeIf { !it.isNullOrBlank() }
                ?: item.brandOwner?.trim()?.takeIf { !it.isNullOrBlank() },
            isRecipe = false,
            isLowSodium = null,
            usdaFdcId = item.fdcId,
            usdaGtinUpc = item.gtinUpc?.trim()?.takeIf { it.isNotBlank() },
            usdaPublishedDate = item.publishedDate?.trim()?.takeIf { it.isNotBlank() },
            usdaModifiedDate = item.modifiedDate?.trim()?.takeIf { it.isNotBlank() }
        )

        // Expected domain food model:
        // servingSize = 2, servingUnit = TBSP, gramsPerServingUnit = 18.5
        assertEquals(2.0, food.servingSize, 0.0)
        assertEquals(ServingUnit.TBSP, food.servingUnit) // legacy TBSP mapping
        assertNearlyEquals(18.5, food.gramsPerServingUnit!!)

        val gramsPerServingResolved = computeGramsPerServing(food)
        assertNearlyEquals(37.0, gramsPerServingResolved!!)

        // ---- create serving rows like the use case does ----
        val nutrientCatalog = fakeNutrientCatalog()
        val servingRows: List<FoodNutrientRow> = item.foodNutrients.mapNotNull { n ->
            val usdaNumber = n.nutrientNumber ?: return@mapNotNull null
            val csvCode = UsdaToCsvNutrientMap.byUsdaNumber[usdaNumber] ?: return@mapNotNull null
            val nutrient = nutrientCatalog[csvCode] ?: return@mapNotNull null
            val amt = n.value ?: return@mapNotNull null
            FoodNutrientRow(
                nutrient = nutrient,
                amount = amt,
                basisType = BasisType.USDA_REPORTED_SERVING,
                basisGrams = null
            )
        }

        // ---- canonicalize to PER_100G exactly like the use case does ----
        val canonicalRows = canonicalizeRows(food, servingRows)

        // calories per 100g should be: 200 * (100/37) = 540.5405405...
        val caloriesRow = canonicalRows.first { it.nutrient.code == NutrientCodes.CALORIES_KCAL }
        assertEquals(BasisType.PER_100G, caloriesRow.basisType)
        assertNearlyEquals(540.5405405405405, caloriesRow.amount, eps = 1e-12)

        // ---- round-trip invariant (per100g -> perGram -> * gramsPerServing == perServing) ----
        val factorPerGram = 1.0 / 100.0
        canonicalRows.forEach { row ->
            // stored per100g
            val perGram = row.amount * factorPerGram
            val roundTripPerServing = perGram * gramsPerServingResolved

            // find original per-serving
            val original = servingRows.first { it.nutrient.id == row.nutrient.id }.amount
            assertNearlyEquals(original, roundTripPerServing!!, eps = 1e-9)
        }
    }

    @Test
    fun edge_case_household_1_unit_45g_bridge_is_45_and_per100g_roundtrip_holds() {
        val json = foodsSearchJson(
            fdcId = 222L,
            description = "test bar",
            gtinUpc = null,
            brandOwner = "BrandOwner",
            brandName = null,
            servingSize = 45.0,
            servingSizeUnit = "g",
            householdServingFullText = "1 tbsp (45 g)",
            nutrients = listOf(
                nutrient(number = "208", value = 100.0),
                nutrient(number = "203", value = 3.0),
            )
        )

        val item = UsdaFoodsSearchParser.parse(json).foods.first()
        val rawUnit = ServingUnit.fromUsda(item.servingSizeUnit)!!
        val rawSize = item.servingSize ?: 1.0
        val household = parseHouseholdServing(item.householdServingFullText)
        val finalSize = household?.size ?: rawSize
        val finalUnit = household?.unit ?: rawUnit

        val gramsPerUnit = computeGramsBridgePer1Unit(
            householdSize = household?.size,
            displayUnit = finalUnit,
            rawSize = rawSize,
            rawUnit = rawUnit
        )

        val food = Food(
            id = 0L,
            name = item.description?.trim()?.toTitleCase().orEmpty(),
            servingSize = finalSize,
            servingUnit = finalUnit,
            servingsPerPackage = null,
            gramsPerServingUnit = gramsPerUnit,
            mlPerServingUnit = null,
            stableId = "test",
            brand = "BrandOwner",
            isRecipe = false,
            isLowSodium = null,
            usdaFdcId = item.fdcId,
            usdaGtinUpc = null,
            usdaPublishedDate = null,
            usdaModifiedDate = null
        )

        assertEquals(1.0, food.servingSize, 0.0)
        assertEquals(ServingUnit.TBSP, food.servingUnit)
        assertNearlyEquals(45.0, food.gramsPerServingUnit!!)

        val gramsPerServing = computeGramsPerServing(food)!!
        assertNearlyEquals(45.0, gramsPerServing)

        val nutrientCatalog = fakeNutrientCatalog()
        val servingRows = item.foodNutrients.mapNotNull { n ->
            val usdaNumber = n.nutrientNumber ?: return@mapNotNull null
            val csvCode = UsdaToCsvNutrientMap.byUsdaNumber[usdaNumber] ?: return@mapNotNull null
            val nutrient = nutrientCatalog[csvCode] ?: return@mapNotNull null
            val amt = n.value ?: return@mapNotNull null
            FoodNutrientRow(nutrient, amt, BasisType.USDA_REPORTED_SERVING, null)
        }

        val canonical = canonicalizeRows(food, servingRows)
        canonical.forEach { row ->
            val perGram = row.amount / 100.0
            val roundTrip = perGram * gramsPerServing
            val original = servingRows.first { it.nutrient.id == row.nutrient.id }.amount
            assertNearlyEquals(original, roundTrip, eps = 1e-9)
        }
    }

    @Test
    fun grams_present_but_household_missing_still_mass_grounded_and_canonicalizes() {
        // Household missing => finalServingSize = rawServingSize and finalServingUnit = rawServingUnit.
        // If raw is mass (g), we canonicalize to PER_100G using ServingUnit conversion.
        val json = foodsSearchJson(
            fdcId = 333L,
            description = "test grams only",
            gtinUpc = null,
            brandOwner = null,
            brandName = null,
            servingSize = 37.0,
            servingSizeUnit = "g",
            householdServingFullText = null,
            nutrients = listOf(
                nutrient(number = "208", value = 200.0),
            )
        )

        val item = UsdaFoodsSearchParser.parse(json).foods.first()
        val rawUnit = ServingUnit.fromUsda(item.servingSizeUnit)!!
        val rawSize = item.servingSize ?: 1.0

        val food = Food(
            id = 0L,
            name = "Test",
            servingSize = rawSize,
            servingUnit = rawUnit,
            servingsPerPackage = null,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            stableId = "test",
            brand = null,
            isRecipe = false,
            isLowSodium = null,
            usdaFdcId = item.fdcId,
            usdaGtinUpc = null,
            usdaPublishedDate = null,
            usdaModifiedDate = null
        )

        val gramsPerServing = computeGramsPerServing(food)
        assertNearlyEquals(37.0, gramsPerServing!!)

        val nutrientCatalog = fakeNutrientCatalog()
        val servingRows = item.foodNutrients.mapNotNull { n ->
            val csvCode = UsdaToCsvNutrientMap.byUsdaNumber[n.nutrientNumber ?: return@mapNotNull null]
                ?: return@mapNotNull null
            val nutrient = nutrientCatalog[csvCode] ?: return@mapNotNull null
            val amt = n.value ?: return@mapNotNull null
            FoodNutrientRow(nutrient, amt, BasisType.USDA_REPORTED_SERVING, null)
        }

        val canonical = canonicalizeRows(food, servingRows)
        val calories = canonical.first()
        assertEquals(BasisType.PER_100G, calories.basisType)
        assertNearlyEquals(540.5405405405405, calories.amount, eps = 1e-12)
    }

    @Test
    fun unsupported_or_missing_serving_unit_blocks_in_real_usecase_so_conversion_is_not_attempted() {
        // In the real use case:
        // ServingUnit.fromUsda(null or unknown) => null => Result.Blocked.
        // We assert that fromUsda returns null so conversion should not proceed.
        assertNull(ServingUnit.fromUsda(null))
        assertNull(ServingUnit.fromUsda("BAR")) // not mapped in your fromUsda()
    }

    // -------------------------
    // Helpers (mirror use case logic)
    // -------------------------

    private data class HouseholdServing(val size: Double, val unit: ServingUnit)

    private fun parseHouseholdServing(text: String?): HouseholdServing? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        val head = trimmed.substringBefore("(").trim()
        val parts = head.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size < 2) return null

        val size = parts[0].toDoubleOrNull()?.takeIf { it > 0.0 } ?: return null
        val unitText = parts.drop(1).joinToString(" ").trim()
        val unit = ServingUnit.fromUsda(unitText) ?: return null
        return HouseholdServing(size, unit)
    }

    private fun computeGramsBridgePer1Unit(
        householdSize: Double?,
        displayUnit: ServingUnit,
        rawSize: Double,
        rawUnit: ServingUnit
    ): Double? {
        if (householdSize == null) return null
        if (!rawUnit.isMassUnit()) return null
        if (displayUnit.isMassUnit()) return null
        val gramsTotal = rawUnit.toGrams(rawSize)?.takeIf { it > 0.0 } ?: return null
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
        val mlTotal = rawUnit.toMilliliters(rawSize)?.takeIf { it > 0.0 } ?: return null
        return (mlTotal / householdSize).takeIf { it > 0.0 }
    }

    private fun isMassGrounded(food: Food): Boolean =
        food.servingUnit.isMassUnit() || (food.gramsPerServingUnit?.takeIf { it > 0.0 } != null)

    private fun isVolumeGrounded(food: Food): Boolean {
        if (isMassGrounded(food)) return false
        return food.servingUnit.isVolumeUnit() || (food.mlPerServingUnit?.takeIf { it > 0.0 } != null)
    }

    private fun computeGramsPerServing(food: Food): Double? {
        val bridgedGPer1 = food.gramsPerServingUnit?.takeIf { it > 0.0 }
        val grams = when {
            bridgedGPer1 != null -> food.servingSize * bridgedGPer1
            food.servingUnit.isMassUnit() -> food.servingUnit.toGrams(food.servingSize)
            else -> null
        }
        return grams?.takeIf { it > 0.0 }
    }

    private fun computeMlPerServing(food: Food): Double? {
        val bridgedMlPer1 = food.mlPerServingUnit?.takeIf { it > 0.0 }
        val ml = when {
            bridgedMlPer1 != null -> food.servingSize * bridgedMlPer1
            food.servingUnit.isVolumeUnit() -> food.servingUnit.toMilliliters(food.servingSize)
            else -> null
        }
        return ml?.takeIf { it > 0.0 }
    }

    private fun canonicalizeRows(food: Food, servingRows: List<FoodNutrientRow>): List<FoodNutrientRow> {
        val canonical = when {
            isMassGrounded(food) -> {
                val gramsPerServing = computeGramsPerServing(food)
                    ?: throw AssertionError("Failed to compute grams-per-serving for mass-grounded food")
                val factor = 100.0 / gramsPerServing
                servingRows.map { r ->
                    r.copy(
                        basisType = BasisType.PER_100G,
                        amount = r.amount * factor,
                        basisGrams = 100.0
                    )
                }
            }

            isVolumeGrounded(food) -> {
                val mlPerServing = computeMlPerServing(food)
                    ?: throw AssertionError("Failed to compute ml-per-serving for volume-grounded food")
                val factor = 100.0 / mlPerServing
                servingRows.map { r ->
                    r.copy(
                        basisType = BasisType.PER_100ML,
                        amount = r.amount * factor,
                        basisGrams = null
                    )
                }
            }

            else -> servingRows
        }

        // match use case de-dupe behavior
        return canonical
            .groupBy { it.nutrient.id }
            .mapNotNull { (_, group) -> group.firstOrNull() }
    }

    private fun fakeNutrientCatalog(): Map<String, Nutrient> {
        // Only the 4 macros we test, using your canonical codes
        return mapOf(
            NutrientCodes.CALORIES_KCAL to Nutrient(id = 1L, code = NutrientCodes.CALORIES_KCAL, displayName = "Calories"),
            NutrientCodes.PROTEIN_G to Nutrient(id = 2L, code = NutrientCodes.PROTEIN_G, displayName = "Protein"),
            NutrientCodes.CARBS_G to Nutrient(id = 3L, code = NutrientCodes.CARBS_G, displayName = "Carbs"),
            NutrientCodes.FAT_G to Nutrient(id = 4L, code = NutrientCodes.FAT_G, displayName = "Fat"),
        )
    }

    private fun assertNearlyEquals(expected: Double, actual: Double, eps: Double = 1e-9) {
        if (abs(expected - actual) > eps) {
            throw AssertionError("Expected $expected but was $actual (eps=$eps)")
        }
    }

    /**
     * Title-cases a string for display.
     * (Copied from your use case file so the assertion matches.)
     */
    private fun String.toTitleCase(): String =
        lowercase()
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }

    // -------------------------
    // JSON fixture builder
    // -------------------------

    private data class UsdaNutrientFixture(val nutrientNumber: String, val value: Double)

    private fun nutrient(number: String, value: Double): UsdaNutrientFixture =
        UsdaNutrientFixture(number, value)

    private fun foodsSearchJson(
        fdcId: Long,
        description: String?,
        gtinUpc: String?,
        brandOwner: String?,
        brandName: String?,
        servingSize: Double?,
        servingSizeUnit: String?,
        householdServingFullText: String?,
        nutrients: List<UsdaNutrientFixture>,
    ): String {
        val nutrientsJson = nutrients.joinToString(",") {
            """
            {
              "nutrientId": 0,
              "nutrientNumber": "${it.nutrientNumber}",
              "value": ${it.value},
              "unitName": null,
              "nutrientName": null
            }
            """.trimIndent()
        }

        fun j(s: String?): String = if (s == null) "null" else "\"${s.replace("\"", "\\\"")}\""

        return """
        {
          "totalHits": 1,
          "foods": [
            {
              "fdcId": $fdcId,
              "description": ${j(description)},
              "gtinUpc": ${j(gtinUpc)},
              "brandOwner": ${j(brandOwner)},
              "brandName": ${j(brandName)},
              "publishedDate": "2024-01-01",
              "modifiedDate": "2024-02-01",
              "servingSize": ${servingSize ?: "null"},
              "servingSizeUnit": ${j(servingSizeUnit)},
              "householdServingFullText": ${j(householdServingFullText)},
              "foodNutrients": [
                $nutrientsJson
              ]
            }
          ]
        }
        """.trimIndent()
    }

    @Test
    fun volume_grounded_food_canonicalizes_to_per100ml_and_roundtrip_holds() {

        val json = foodsSearchJson(
            fdcId = 444L,
            description = "test liquid",
            gtinUpc = null,
            brandOwner = null,
            brandName = null,
            servingSize = 240.0,
            servingSizeUnit = "ml",
            householdServingFullText = null,
            nutrients = listOf(
                nutrient(number = "208", value = 120.0) // kcal
            )
        )

        val item = UsdaFoodsSearchParser.parse(json).foods.first()

        val rawUnit = ServingUnit.fromUsda(item.servingSizeUnit)!!
        val rawSize = item.servingSize ?: 1.0

        val food = Food(
            id = 0L,
            name = "Liquid",
            servingSize = rawSize,
            servingUnit = rawUnit,
            servingsPerPackage = null,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            stableId = "test",
            brand = null,
            isRecipe = false,
            isLowSodium = null,
            usdaFdcId = item.fdcId,
            usdaGtinUpc = null,
            usdaPublishedDate = null,
            usdaModifiedDate = null
        )

        val mlPerServing = computeMlPerServing(food)!!
        assertNearlyEquals(240.0, mlPerServing)

        val nutrientCatalog = fakeNutrientCatalog()

        val servingRows = item.foodNutrients.mapNotNull { n ->
            val csvCode = UsdaToCsvNutrientMap.byUsdaNumber[n.nutrientNumber ?: return@mapNotNull null]
                ?: return@mapNotNull null
            val nutrient = nutrientCatalog[csvCode] ?: return@mapNotNull null
            val amt = n.value ?: return@mapNotNull null
            FoodNutrientRow(nutrient, amt, BasisType.USDA_REPORTED_SERVING, null)
        }

        val canonical = canonicalizeRows(food, servingRows)

        val calories = canonical.first()

        assertEquals(BasisType.PER_100ML, calories.basisType)

        val perMl = calories.amount / 100.0
        val roundTrip = perMl * mlPerServing

        assertNearlyEquals(servingRows.first().amount, roundTrip)
    }

    @Test
    fun duplicate_nutrients_are_deduped() {

        val nutrient = Nutrient(
            id = 1L,
            code = NutrientCodes.CALORIES_KCAL,
            displayName = "Calories"
        )

        val food = Food(
            id = 0L,
            name = "Test Food",
            servingSize = 1.0,
            servingUnit = ServingUnit.G,
            servingsPerPackage = null,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            stableId = "test",
            brand = null,
            isRecipe = false,
            isLowSodium = null,
            usdaFdcId = null,
            usdaGtinUpc = null,
            usdaPublishedDate = null,
            usdaModifiedDate = null
        )

        val rows = listOf(
            FoodNutrientRow(nutrient, 10.0, BasisType.USDA_REPORTED_SERVING, null),
            FoodNutrientRow(nutrient, 12.0, BasisType.USDA_REPORTED_SERVING, null)
        )

        val canonical = canonicalizeRows(food, rows)

        assertEquals(1, canonical.size)
    }

    @Test
    fun parser_reads_minimum_fields_required_for_import() {

        val json = """
    {
      "totalHits": 1,
      "foods": [
        {
          "fdcId": 999,
          "description": "Parser Test Food",
          "gtinUpc": "012345678901",
          "brandOwner": "TestBrand",
          "servingSize": 30.0,
          "servingSizeUnit": "g",
          "foodNutrients": [
            {
              "nutrientNumber": "208",
              "value": 120.0
            }
          ]
        }
      ]
    }
    """.trimIndent()

        val parsed = UsdaFoodsSearchParser.parse(json)

        assertEquals(1, parsed.foods.size)

        val food = parsed.foods.first()

        assertEquals(999L, food.fdcId)
        assertEquals("Parser Test Food", food.description)
        assertEquals("012345678901", food.gtinUpc)
        assertEquals(30.0, food.servingSize)
        assertEquals("g", food.servingSizeUnit)

        val nutrient = food.foodNutrients.first()

        assertEquals("208", nutrient.nutrientNumber)
        assertEquals(120.0, nutrient.value)
    }
}