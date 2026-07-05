package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.adobongkangkong.data.local.db.entity.RecipeMeasuredYieldEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeMeasuredYieldDao {

    @Query(
        """
        SELECT *
        FROM recipe_measured_yields
        WHERE recipeId = :recipeId
          AND (
                (:variantId IS NULL AND variantId IS NULL)
                OR variantId = :variantId
          )
          AND isActive = 1
        ORDER BY updatedAtEpochMs DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getActiveYield(
        recipeId: Long,
        variantId: Long?
    ): RecipeMeasuredYieldEntity?

    @Query(
        """
        SELECT *
        FROM recipe_measured_yields
        WHERE recipeId = :recipeId
          AND (
                (:variantId IS NULL AND variantId IS NULL)
                OR variantId = :variantId
          )
          AND isActive = 1
        ORDER BY updatedAtEpochMs DESC, id DESC
        LIMIT 1
        """
    )
    fun observeActiveYield(
        recipeId: Long,
        variantId: Long?
    ): Flow<RecipeMeasuredYieldEntity?>

    @Query(
        """
        UPDATE recipe_measured_yields
        SET isActive = 0
        WHERE recipeId = :recipeId
          AND (
                (:variantId IS NULL AND variantId IS NULL)
                OR variantId = :variantId
          )
          AND isActive = 1
        """
    )
    suspend fun deactivateActiveYieldsForRecipeForm(
        recipeId: Long,
        variantId: Long?
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: RecipeMeasuredYieldEntity): Long

    /**
     * Replaces the active measured yield for one recipe form.
     *
     * A recipe form is:
     * - recipeId + null variantId for the base recipe
     * - recipeId + non-null variantId for a specific variant
     */
    @Transaction
    suspend fun replaceActiveYield(entity: RecipeMeasuredYieldEntity): Long {
        deactivateActiveYieldsForRecipeForm(
            recipeId = entity.recipeId,
            variantId = entity.variantId
        )
        return insert(entity.copy(isActive = true))
    }
}