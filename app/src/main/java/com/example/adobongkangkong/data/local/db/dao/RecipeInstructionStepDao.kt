package com.example.adobongkangkong.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.adobongkangkong.data.local.db.entity.RecipeInstructionStepEntity

@Dao
interface RecipeInstructionStepDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(step: RecipeInstructionStepEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(steps: List<RecipeInstructionStepEntity>)

    @Query(
        """
        SELECT * FROM recipe_instruction_steps
        WHERE recipeId = :recipeId
        ORDER BY position ASC
        """
    )
    suspend fun getForRecipe(recipeId: Long): List<RecipeInstructionStepEntity>

    @Query(
        """
        SELECT * FROM recipe_instruction_steps
        WHERE id = :stepId
        LIMIT 1
        """
    )
    suspend fun getById(stepId: Long): RecipeInstructionStepEntity?

    @Query(
        """
        SELECT * FROM recipe_instruction_steps
        WHERE stableId = :stableId
        LIMIT 1
        """
    )
    suspend fun getByStableId(stableId: String): RecipeInstructionStepEntity?

    @Query(
        """
        DELETE FROM recipe_instruction_steps
        WHERE id = :stepId
        """
    )
    suspend fun deleteById(stepId: Long)

    @Query(
        """
        DELETE FROM recipe_instruction_steps
        WHERE recipeId = :recipeId
        """
    )
    suspend fun deleteForRecipe(recipeId: Long)

    @Query(
        """
        UPDATE recipe_instruction_steps
        SET text = :text
        WHERE id = :stepId
        """
    )
    suspend fun updateText(
        stepId: Long,
        text: String
    )

    @Query(
        """
        UPDATE recipe_instruction_steps
        SET position = :position
        WHERE id = :stepId
        """
    )
    suspend fun updatePosition(
        stepId: Long,
        position: Int
    )

    @Query(
        """
        UPDATE recipe_instruction_steps
        SET imagePath = :imagePath
        WHERE id = :stepId
        """
    )
    suspend fun updateImagePath(
        stepId: Long,
        imagePath: String?
    )

    @Query(
        """
        SELECT COUNT(*) FROM recipe_instruction_steps
        WHERE recipeId = :recipeId
        """
    )
    suspend fun countForRecipe(recipeId: Long): Int
}