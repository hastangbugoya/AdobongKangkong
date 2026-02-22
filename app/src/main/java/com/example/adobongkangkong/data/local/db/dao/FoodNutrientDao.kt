package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.adobongkangkong.data.local.db.entity.BasisType
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity

data class MacroNutrientRow(
    val nutrientCode: String,
    val amountPerServing: Double
)

data class FoodNutrientWithMetaRow(
    val foodId: Long,
    val nutrientId: Long,
    val code: String,
    val displayName: String,
    val unit: String,
    val category: String,
    val amount: Double,
    val basisType: BasisType
)
data class FoodNutrientCodeRow(
    val code: String,
    val amount: Double,
    val basisType: BasisType
)

@Dao
interface FoodNutrientDao {

    @Upsert
    suspend fun upsertAll(rows: List<FoodNutrientEntity>)

    @Query("SELECT * FROM food_nutrients WHERE foodId = :foodId")
    suspend fun getForFood(foodId: Long): List<FoodNutrientEntity>

    @Query("SELECT * FROM food_nutrients WHERE foodId IN (:foodIds)")
    suspend fun getForFoods(foodIds: List<Long>): List<FoodNutrientEntity>

    @Query("DELETE FROM food_nutrients WHERE foodId = :foodId")
    suspend fun deleteForFood(foodId: Long)

    @Query("""
    SELECT n.code AS nutrientCode, fn.nutrientAmountPerBasis AS amountPerServing
    FROM food_nutrients fn
    JOIN nutrients n ON n.id = fn.nutrientId
    WHERE fn.foodId = :foodId
      AND n.code IN ('CALORIES_KCAL', 'PROTEIN_G', 'CARBS_G', 'FAT_G')
      AND fn.basisType = (
        SELECT f2.basisType
        FROM food_nutrients f2
        WHERE f2.foodId = fn.foodId AND f2.nutrientId = fn.nutrientId
        ORDER BY CASE f2.basisType
          WHEN 'PER_100G' THEN 0
          WHEN 'PER_100ML' THEN 1
          WHEN 'USDA_REPORTED_SERVING' THEN 2
          ELSE 3
        END
        LIMIT 1
      )
""")
    suspend fun getMacrosForFood(foodId: Long): List<MacroNutrientRow>

    @Query("""
    SELECT 
      fn.foodId AS foodId,
      fn.nutrientId AS nutrientId,
      fn.nutrientAmountPerBasis AS amount,
      fn.basisType as basisType,
      n.code AS code,
      n.displayName AS displayName,
      n.unit AS unit,
      n.category AS category
    FROM food_nutrients fn
    JOIN nutrients n ON n.id = fn.nutrientId
    WHERE fn.foodId = :foodId
      AND fn.basisType = (
        SELECT f2.basisType
        FROM food_nutrients f2
        WHERE f2.foodId = fn.foodId AND f2.nutrientId = fn.nutrientId
        ORDER BY CASE f2.basisType
          WHEN 'PER_100G' THEN 0
          WHEN 'PER_100ML' THEN 1
          WHEN 'USDA_REPORTED_SERVING' THEN 2
          ELSE 3
        END
        LIMIT 1
      )
    ORDER BY n.category ASC, n.displayName ASC
""")
    suspend fun getForFoodWithMeta(foodId: Long): List<FoodNutrientWithMetaRow>

    @Query("DELETE FROM food_nutrients WHERE foodId = :foodId AND nutrientId = :nutrientId")
    suspend fun deleteOne(foodId: Long, nutrientId: Long)

    @Query("UPDATE food_nutrients SET nutrientId = :newId WHERE nutrientId = :oldId")
    suspend fun reassignFoodNutrients(oldId: Long, newId: Long)

    @Query(
        """
    SELECT n.code AS code,
           fn.nutrientAmountPerBasis AS amount,
           fn.basisType AS basisType
    FROM food_nutrients fn
    JOIN nutrients n ON n.id = fn.nutrientId
    WHERE fn.foodId = :foodId
    """
    )
    suspend fun getCodesForFood(foodId: Long): List<FoodNutrientCodeRow>
}

/**
 * ============================
 * FOOD_NUTRIENT_DAO – FUTURE-ME NOTES
 * ============================
 *
 * Why this file exists:
 * - The DB schema allows multiple rows per nutrient per food because the PK includes `basisType`.
 *   (`foodId + nutrientId + basisType`)
 * - The UI + editor logic, however, assumes ONE row per nutrientId (e.g., LazyColumn key = nutrientId).
 * - So if writers (CSV import, USDA import, manual save) insert BOTH `USDA_REPORTED_SERVING` and `PER_100G`,
 *   the UI will crash or show duplicates.
 *
 * Locked-down rule (canonical mindset):
 * - We want ONE canonical basis per food+nute in practice:
 *   - Prefer PER_100G (if mass-backed).
 *   - Else PER_100ML (if volume-backed).
 *   - Else USDA_REPORTED_SERVING only as a temporary “blocked until grounded” state.
 *
 * Why DAO may contain “seatbelt” queries:
 * - Even after we fix writers, users may already have old duplicate rows in DB.
 * - This DAO can enforce a preference order when reading:
 *     PER_100G > PER_100ML > USDA_REPORTED_SERVING
 *   so the UI receives a de-duplicated view without rewriting all consumers immediately.
 *
 * IMPORTANT:
 * - The “seatbelt” is not the ideal end-state; it’s an insurance policy.
 * - The real fix is to stop generating multi-basis rows at WRITE time.
 * - If you find yourself adding more and more query filters here, step back: the writers are probably wrong.
 *
 * Edge cases / nuance:
 * - Some foods (packet/box/bunch/etc) may legitimately only have USDA_REPORTED_SERVING until user adds grams/ml.
 * - That’s okay—DAO preference still returns that single “raw” row when no canonical row exists.
 *
 * Smell tests:
 * - If you see more than one row with same nutrientId in editor state → writer logic regressed.
 * - If UI uses nutrientId as key and crashes → DAO preference isn’t applied OR queries return all basis rows.
 *
 * If changing this file:
 * - Do not invent new identifiers or ad-hoc basis enums.
 * - Keep preference ordering consistent everywhere.
 * - Keep it boring: deterministic ordering beats cleverness.
 */

