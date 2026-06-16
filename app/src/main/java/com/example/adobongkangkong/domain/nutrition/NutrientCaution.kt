package com.example.adobongkangkong.domain.nutrition

import javax.inject.Inject

enum class NutrientCautionBasis {
    LOGGED_AMOUNT,
    RECIPE_SERVING,
    RECIPE_BATCH,
}

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
 */
class NutrientCautionCalculator @Inject constructor() {

    fun forLoggedAmount(
        nutrients: NutrientMap?,
    ): List<NutrientCaution> =
        forNutrients(
            nutrients = nutrients,
            basis = NutrientCautionBasis.LOGGED_AMOUNT,
        )

    fun forRecipeServing(
        perServingNutrients: NutrientMap?,
    ): List<NutrientCaution> =
        forNutrients(
            nutrients = perServingNutrients,
            basis = NutrientCautionBasis.RECIPE_SERVING,
        )

    fun forRecipeBatch(
        batchNutrients: NutrientMap?,
    ): List<NutrientCaution> =
        forNutrients(
            nutrients = batchNutrients,
            basis = NutrientCautionBasis.RECIPE_BATCH,
        )

    fun forNutrients(
        nutrients: NutrientMap?,
        basis: NutrientCautionBasis,
    ): List<NutrientCaution> {
        if (nutrients == null || nutrients.isEmpty()) return emptyList()

        val sodiumMg = nutrients[NutrientKey.SODIUM_MG]
        val sugarsG = nutrients[NutrientKey.SUGARS_G]

        return buildList {
            if (sodiumMg > SODIUM_CAUTION_MG) {
                add(
                    NutrientCaution(
                        nutrientKey = NutrientKey.SODIUM_MG,
                        label = "Sodium",
                        amount = sodiumMg,
                        unit = "mg",
                        threshold = SODIUM_CAUTION_MG,
                        basis = basis,
                    )
                )
            }

            if (sugarsG > TOTAL_SUGAR_CAUTION_G) {
                add(
                    NutrientCaution(
                        nutrientKey = NutrientKey.SUGARS_G,
                        label = "Total sugar",
                        amount = sugarsG,
                        unit = "g",
                        threshold = TOTAL_SUGAR_CAUTION_G,
                        basis = basis,
                    )
                )
            }
        }
    }

    private companion object {
        const val SODIUM_CAUTION_MG = 600.0
        const val TOTAL_SUGAR_CAUTION_G = 15.0
    }
}
