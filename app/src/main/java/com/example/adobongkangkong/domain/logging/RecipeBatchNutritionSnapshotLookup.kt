package com.example.adobongkangkong.domain.logging

import com.example.adobongkangkong.domain.nutrition.NutrientMap

/**
 * Minimal read surface for nutrition basis for a cooked recipe batch.
 *
 * Important: this should return the snapshot that makes math trivial for logging.
 * Recommended: store nutrients per cooked gram (not per serving).
 */
interface RecipeBatchNutritionSnapshotLookup {
    suspend fun getBatchSnapshot(batchId: Long): RecipeBatchNutritionSnapshot?
}

/**
 * Mirrors your food snapshot flow, but for a recipe batch.
 *
 * nutrientsPerCookedGram should be the same type you already use for
 * snapshot.nutrientsPerGram (i.e., supports scaledBy()).
 */
data class RecipeBatchNutritionSnapshot(
    val batchId: Long,
    val nutrientsPerCookedGram: NutrientMap?
)

fun RecipeBatchNutritionSnapshot.nutrientsForCookedGrams(gramsCooked: Double): NutrientMap =
    (nutrientsPerCookedGram ?: NutrientMap.EMPTY).scaledBy(gramsCooked)