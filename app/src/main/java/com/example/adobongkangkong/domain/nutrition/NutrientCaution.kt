package com.example.adobongkangkong.domain.nutrition

import javax.inject.Inject

enum class NutrientCautionBasis {
    LOGGED_AMOUNT,
    RECIPE_SERVING,
    RECIPE_BATCH,
}

data class NutrientCautionThresholds(
    val sodiumMg: Double,
    val totalSugarG: Double,
)

data class NutrientCaution(
    val nutrientKey: NutrientKey,
    val label: String,
    val amount: Double,
    val unit: String,
    val threshold: Double,
    val basis: NutrientCautionBasis,
)

/**
 * Shared sodium/sugar caution rules.
 *
 * This is intentionally domain-side and NutrientMap-based so it can be reused by:
 * - Quick Add logged amount warnings
 * - Recipe Builder serving/batch warnings
 * - Recipe Variant serving/batch warnings
 *
 * Thresholds are passed in by callers so app preferences remain the source of truth.
 */
class NutrientCautionCalculator @Inject constructor() {

    fun forLoggedAmount(
        nutrients: NutrientMap?,
        thresholds: NutrientCautionThresholds,
    ): List<NutrientCaution> =
        forNutrients(
            nutrients = nutrients,
            basis = NutrientCautionBasis.LOGGED_AMOUNT,
            thresholds = thresholds,
        )

    fun forRecipeServing(
        perServingNutrients: NutrientMap?,
        thresholds: NutrientCautionThresholds,
    ): List<NutrientCaution> =
        forNutrients(
            nutrients = perServingNutrients,
            basis = NutrientCautionBasis.RECIPE_SERVING,
            thresholds = thresholds,
        )

    fun forRecipeBatch(
        batchNutrients: NutrientMap?,
        thresholds: NutrientCautionThresholds,
    ): List<NutrientCaution> =
        forNutrients(
            nutrients = batchNutrients,
            basis = NutrientCautionBasis.RECIPE_BATCH,
            thresholds = thresholds,
        )

    fun forNutrients(
        nutrients: NutrientMap?,
        basis: NutrientCautionBasis,
        thresholds: NutrientCautionThresholds,
    ): List<NutrientCaution> {
        if (nutrients == null || nutrients.isEmpty()) return emptyList()

        val sodiumMg = nutrients[NutrientKey.SODIUM_MG]
        val sugarsG = nutrients[NutrientKey.SUGARS_G]

        return buildList {
            if (sodiumMg > thresholds.sodiumMg) {
                add(
                    NutrientCaution(
                        nutrientKey = NutrientKey.SODIUM_MG,
                        label = "Sodium",
                        amount = sodiumMg,
                        unit = "mg",
                        threshold = thresholds.sodiumMg,
                        basis = basis,
                    )
                )
            }

            if (sugarsG > thresholds.totalSugarG) {
                add(
                    NutrientCaution(
                        nutrientKey = NutrientKey.SUGARS_G,
                        label = "Total sugar",
                        amount = sugarsG,
                        unit = "g",
                        threshold = thresholds.totalSugarG,
                        basis = basis,
                    )
                )
            }
        }
    }
}
