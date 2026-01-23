package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIngredients(items: List<RecipeIngredientEntity>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeFoodId = :recipeFoodId")
    suspend fun deleteIngredientsForRecipe(recipeFoodId: Long)

    @Query("SELECT * FROM recipe_ingredients WHERE recipeFoodId = :recipeFoodId")
    fun observeIngredients(recipeFoodId: Long): Flow<List<RecipeIngredientEntity>>
}
