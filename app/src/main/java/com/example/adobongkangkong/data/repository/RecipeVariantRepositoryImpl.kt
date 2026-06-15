package com.example.adobongkangkong.data.repository

import androidx.room.withTransaction
import com.example.adobongkangkong.data.local.db.NutriDatabase
import com.example.adobongkangkong.data.local.db.dao.RecipeVariantDao
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeEntity
import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeVariantRepositoryImpl @Inject constructor(
    private val db: NutriDatabase,
    private val recipeVariantDao: RecipeVariantDao,
) : RecipeVariantRepository {

    override fun observeVariantsForRecipe(
        recipeFoodId: Long,
    ): Flow<List<RecipeVariantEntity>> {
        return recipeVariantDao.observeVariantsForRecipe(recipeFoodId)
    }

    override fun observeActiveVariantsForRecipe(
        recipeFoodId: Long,
    ): Flow<List<RecipeVariantEntity>> {
        return recipeVariantDao.observeActiveVariantsForRecipe(recipeFoodId)
    }

    override suspend fun getVariantById(
        variantId: Long,
    ): RecipeVariantEntity? {
        return recipeVariantDao.getVariantById(variantId)
    }

    override suspend fun createVariant(
        recipeFoodId: Long,
        name: String,
        notes: String?,
        nowEpochMillis: Long,
    ): Long {
        return recipeVariantDao.insertVariant(
            RecipeVariantEntity(
                recipeFoodId = recipeFoodId,
                name = name.trim(),
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                isArchived = false,
                servingsYieldOverride = null,
                totalYieldGramsOverride = null,
                nutrientsJsonSnapshot = null,
                createdAtEpochMillis = nowEpochMillis,
                updatedAtEpochMillis = nowEpochMillis,
            )
        )
    }

    override suspend fun updateVariant(
        variant: RecipeVariantEntity,
    ) {
        recipeVariantDao.updateVariant(variant)
    }

    override suspend fun archiveVariant(
        variantId: Long,
        nowEpochMillis: Long,
    ) {
        recipeVariantDao.archiveVariant(
            variantId = variantId,
            updatedAtEpochMillis = nowEpochMillis,
        )
    }

    override suspend fun restoreVariant(
        variantId: Long,
        nowEpochMillis: Long,
    ) {
        recipeVariantDao.restoreVariant(
            variantId = variantId,
            updatedAtEpochMillis = nowEpochMillis,
        )
    }

    override suspend fun deleteArchivedVariant(
        variantId: Long,
    ) {
        db.withTransaction {
            val variant = recipeVariantDao.getVariantById(variantId)
                ?: return@withTransaction

            require(variant.isArchived) {
                "Only archived variants can be permanently deleted."
            }

            recipeVariantDao.deleteChangesForVariant(variantId)
            recipeVariantDao.deleteVariantById(variantId)
        }
    }

    override suspend fun getChangesForVariant(
        variantId: Long,
    ): List<RecipeVariantIngredientChangeEntity> {
        return recipeVariantDao.getChangesForVariant(variantId)
    }

    override suspend fun replaceChangesForVariant(
        variantId: Long,
        changes: List<RecipeVariantIngredientChangeEntity>,
    ) {
        db.withTransaction {
            recipeVariantDao.deleteChangesForVariant(variantId)

            if (changes.isNotEmpty()) {
                recipeVariantDao.insertChanges(changes)
            }
        }
    }

    override suspend fun updateVariantNutritionSnapshot(
        variantId: Long,
        nutrientsJsonSnapshot: String?,
        nowEpochMillis: Long,
    ) {
        recipeVariantDao.updateVariantNutritionSnapshot(
            variantId = variantId,
            nutrientsJsonSnapshot = nutrientsJsonSnapshot,
            updatedAtEpochMillis = nowEpochMillis,
        )
    }
}
