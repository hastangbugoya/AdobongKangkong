package com.example.adobongkangkong.domain.nutrition

import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity

sealed interface IntakeAmount {
    data class Servings(val value: Double) : IntakeAmount
    data class Grams(val value: Double) : IntakeAmount
}

/**
 * Returns the nutrient quantity consumed for a single nutrient row.
 *
 * Assumptions:
 * - FoodNutrientEntity.amount is "per basis unit" (per serving OR per 100g). :contentReference[oaicite:4]{index=4}
 * - basisGrams represents grams per serving when a conversion is needed. :contentReference[oaicite:5]{index=5}
 */
//fun FoodNutrientEntity.consumedAmount(intake: IntakeAmount): Double {
//    val per = nutrientAmountPerBasis
//
//    return when (basisType) {
//        BasisType.PER_SERVING -> {
//            when (intake) {
//                is IntakeAmount.Servings -> per * intake.value
//                is IntakeAmount.Grams -> {
//                    val gramsPerServing = requireNotNull(basisGrams) {
//                        "basisGrams required to convert grams -> servings for PER_SERVING nutrients"
//                    }
//                    per * (intake.value / gramsPerServing)
//                }
//            }
//        }
//
//        BasisType.PER_100G -> {
//            when (intake) {
//                is IntakeAmount.Grams -> per * (intake.value / 100.0)
//                is IntakeAmount.Servings -> {
//                    val gramsPerServing = requireNotNull(basisGrams) {
//                        "basisGrams required to convert servings -> grams for PER_100G nutrients"
//                    }
//                    val grams = intake.value * gramsPerServing
//                    per * (grams / 100.0)
//                }
//            }
//        }
//    }
//}

