package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.FoodNutrientEntity

data class MacroNutrientRow(
    val nutrientCode: String,
    val amountPerServing: Double
)
@Dao
interface FoodNutrientDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FoodNutrientEntity>)

    @Query("SELECT * FROM food_nutrients WHERE foodId = :foodId")
    suspend fun getForFood(foodId: Long): List<FoodNutrientEntity>


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
}
