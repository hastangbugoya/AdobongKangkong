package com.example.adobongkangkong.domain.usecase

import com.example.adobongkangkong.domain.model.Food
import com.example.adobongkangkong.domain.repository.FoodRepository
import javax.inject.Inject

/**
 * Saves ONLY safe food metadata.
 *
 * ## Purpose
 * Persist a [Food] without touching nutrient rows.
 *
 * ## IMPORTANT
 * - This use case MUST NOT:
 *   - read nutrient rows
 *   - write nutrient rows
 *   - perform canonicalization
 *   - interpret serving as nutrient basis
 *
 * - This is the DEFAULT save path for Food Editor when:
 *   - user edits serving size/unit
 *   - user edits gramsPerServingUnit / mlPerServingUnit
 *   - user edits name/brand/etc
 *
 * ## DO NOT USE for:
 * - nutrient edits
 * - basis reinterpretation
 * - USDA import normalization
 *
 * Those must use dedicated nutrient-aware use cases.
 */
class SaveFoodMetadataUseCase @Inject constructor(
    private val foodRepository: FoodRepository
) {

    suspend operator fun invoke(food: Food): Long {
        return foodRepository.upsert(food)
    }
}