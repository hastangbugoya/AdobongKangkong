package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeVariantDao {

    @Query(
        """
        SELECT *
        FROM recipe_variant
        WHERE recipeFoodId = :recipeFoodId
        ORDER BY isArchived ASC, name COLLATE NOCASE ASC
        """
    )
    fun observeVariantsForRecipe(
        recipeFoodId: Long,
    ): Flow<List<RecipeVariantEntity>>

    @Query(
        """
        SELECT *
        FROM recipe_variant
        WHERE recipeFoodId = :recipeFoodId
          AND isArchived = 0
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun observeActiveVariantsForRecipe(
        recipeFoodId: Long,
    ): Flow<List<RecipeVariantEntity>>

    @Query(
        """
        SELECT *
        FROM recipe_variant
        WHERE id = :variantId
        LIMIT 1
        """
    )
    fun observeVariantById(
        variantId: Long,
    ): Flow<RecipeVariantEntity?>

    @Query(
        """
        SELECT *
        FROM recipe_variant
        WHERE id = :variantId
        LIMIT 1
        """
    )
    suspend fun getVariantById(
        variantId: Long,
    ): RecipeVariantEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertVariant(
        variant: RecipeVariantEntity,
    ): Long

    @Update
    suspend fun updateVariant(
        variant: RecipeVariantEntity,
    )

    @Query(
        """
        UPDATE recipe_variant
        SET isArchived = 1,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :variantId
        """
    )
    suspend fun archiveVariant(
        variantId: Long,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        UPDATE recipe_variant
        SET isArchived = 0,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :variantId
        """
    )
    suspend fun restoreVariant(
        variantId: Long,
        updatedAtEpochMillis: Long,
    )

    @Query(
        """
        DELETE FROM recipe_variant
        WHERE id = :variantId
        """
    )
    suspend fun deleteVariantById(
        variantId: Long,
    )

    @Query(
        """
        SELECT *
        FROM recipe_variant_ingredient_change
        WHERE variantId = :variantId
        ORDER BY sortOrder ASC, id ASC
        """
    )
    fun observeChangesForVariant(
        variantId: Long,
    ): Flow<List<RecipeVariantIngredientChangeEntity>>

    @Query(
        """
        SELECT *
        FROM recipe_variant_ingredient_change
        WHERE variantId = :variantId
        ORDER BY sortOrder ASC, id ASC
        """
    )
    suspend fun getChangesForVariant(
        variantId: Long,
    ): List<RecipeVariantIngredientChangeEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChange(
        change: RecipeVariantIngredientChangeEntity,
    ): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertChanges(
        changes: List<RecipeVariantIngredientChangeEntity>,
    )

    @Update
    suspend fun updateChange(
        change: RecipeVariantIngredientChangeEntity,
    )

    @Query(
        """
        DELETE FROM recipe_variant_ingredient_change
        WHERE id = :changeId
        """
    )
    suspend fun deleteChangeById(
        changeId: Long,
    )

    @Query(
        """
        DELETE FROM recipe_variant_ingredient_change
        WHERE variantId = :variantId
        """
    )
    suspend fun deleteChangesForVariant(
        variantId: Long,
    )

    @Query(
        """
        UPDATE recipe_variant
        SET nutrientsJsonSnapshot = :nutrientsJsonSnapshot,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :variantId
        """
    )
    suspend fun updateVariantNutritionSnapshot(
        variantId: Long,
        nutrientsJsonSnapshot: String?,
        updatedAtEpochMillis: Long,
    )
}