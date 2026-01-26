package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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
    val amount: Double,
    val code: String,
    val displayName: String,
    val unit: String,
    val category: String
)

data class FoodNutrientDebugRow(
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
      AND n.code IN ('CALORIES', 'PROTEIN_G', 'CARBS_G', 'FAT_G')
""")
    suspend fun getMacrosForFood(foodId: Long): List<MacroNutrientRow>

    @Query("""
    SELECT 
      fn.foodId AS foodId,
      fn.nutrientId AS nutrientId,
      fn.nutrientAmountPerBasis AS amount,
      n.code AS code,
      n.displayName AS displayName,
      n.unit AS unit,
      n.category AS category
    FROM food_nutrients fn
    JOIN nutrients n ON n.id = fn.nutrientId
    WHERE fn.foodId = :foodId
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
    ORDER BY n.code
    """
    )
    suspend fun debugRowsForFood(foodId: Long): List<FoodNutrientDebugRow>

}
