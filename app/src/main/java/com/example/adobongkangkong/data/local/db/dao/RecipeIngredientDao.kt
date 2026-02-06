package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity

@Dao
interface RecipeIngredientDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RecipeIngredientEntity>)

    @Query("SELECT * FROM recipe_ingredient WHERE recipeId = :recipeId ORDER BY sortOrder ASC")
    suspend fun getForRecipe(recipeId: Long): List<RecipeIngredientEntity>

    // Edit Recipe
    @Query("DELETE FROM recipe_ingredient WHERE recipeId = :recipeId")
    suspend fun deleteForRecipe(recipeId: Long)

    /**
     * Used for referential-integrity checks (e.g., block deleting foods that are used by recipes).
     */
    @Query("SELECT COUNT(*) FROM recipe_ingredient WHERE foodId = :foodId")
    suspend fun countRecipesUsingFood(foodId: Long): Int

    /**
     * Sets ingredient amount using grams.
     * Enforces mutual exclusivity by nulling [amountServings].
     */
    @Query("""
        UPDATE recipe_ingredient
        SET amountGrams = :grams,
            amountServings = NULL
        WHERE id = :ingredientId
    """)
    suspend fun setAmountGrams(
        ingredientId: Long,
        grams: Double?
    )

    /**
     * Sets ingredient amount using servings.
     * Enforces mutual exclusivity by nulling [amountGrams].
     */
    @Query("""
        UPDATE recipe_ingredient
        SET amountServings = :servings,
            amountGrams = NULL
        WHERE id = :ingredientId
    """)
    suspend fun setAmountServings(
        ingredientId: Long,
        servings: Double?
    )
}
