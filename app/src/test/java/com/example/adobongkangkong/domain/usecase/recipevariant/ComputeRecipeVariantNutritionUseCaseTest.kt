package com.example.adobongkangkong.domain.usecase.recipevariant

import com.example.adobongkangkong.data.local.db.dao.FoodDao
import com.example.adobongkangkong.data.local.db.dao.FoodIdNameRow
import com.example.adobongkangkong.data.local.db.dao.RecipeDao
import com.example.adobongkangkong.data.local.db.dao.RecipeIngredientDao
import com.example.adobongkangkong.data.local.db.entity.FoodEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeIngredientEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantEntity
import com.example.adobongkangkong.data.local.db.entity.RecipeVariantIngredientChangeEntity
import com.example.adobongkangkong.domain.model.ServingUnit
import com.example.adobongkangkong.domain.nutrition.NutrientKey
import com.example.adobongkangkong.domain.nutrition.NutrientMap
import com.example.adobongkangkong.domain.recipes.FoodNutritionSnapshot
import com.example.adobongkangkong.domain.repository.FoodNutritionSnapshotRepository
import com.example.adobongkangkong.domain.repository.RecipeVariantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.Instant

class ComputeRecipeVariantNutritionUseCaseTest {

    @Test
    fun servingBasedIngredient_doesNotMultiplyByFoodServingSizeAgain() = runBlocking {
        val recipeFood = recipeFood()
        val ingredientFood = ingredientFood(
            id = 10L,

            // This intentionally differs from 1.0.
            // The regression: variant math used to multiply by this too,
            // causing serving-based ingredients to be counted twice.
            servingSize = 2.0,
        )

        val recipe = recipe(recipeFoodId = recipeFood.id, servingsYield = 6.0)
        val variant = variant(recipeFoodId = recipeFood.id)

        val ingredient = RecipeIngredientEntity(
            id = 300L,
            recipeId = recipe.id,
            foodId = ingredientFood.id,

            // Existing recipe math treats this as the ingredient amount in serving units.
            // Correct grams = 2.0 * 100g = 200g.
            // Incorrect old bug = 2.0 * food.servingSize(2.0) * 100g = 400g.
            amountServings = 2.0,
            amountGrams = null,
            sortOrder = 0,
        )

        val useCase = buildUseCase(
            foods = listOf(recipeFood, ingredientFood),
            recipe = recipe,
            ingredients = listOf(ingredient),
            variant = variant,
            snapshots = listOf(
                snapshot(
                    foodId = ingredientFood.id,
                    gramsPerServingUnit = 100.0,
                    caloriesPerGram = 1.0,
                    proteinPerGram = 0.10,
                )
            ),
        )

        val result = useCase(variant.id)

        assertEquals(200.0, result.totals[NutrientKey.CALORIES_KCAL], 0.0001)
        assertEquals(20.0, result.totals[NutrientKey.PROTEIN_G], 0.0001)
        assertEquals(200.0 / 6.0, result.perServing!![NutrientKey.CALORIES_KCAL], 0.0001)
        assertEquals(20.0 / 6.0, result.perServing!![NutrientKey.PROTEIN_G], 0.0001)
    }

    @Test
    fun gramsBasedIngredient_usesExactGrams() = runBlocking {
        val recipeFood = recipeFood()
        val ingredientFood = ingredientFood(id = 11L, servingSize = 99.0)
        val recipe = recipe(recipeFoodId = recipeFood.id, servingsYield = 3.0)
        val variant = variant(recipeFoodId = recipeFood.id)

        val ingredient = RecipeIngredientEntity(
            id = 301L,
            recipeId = recipe.id,
            foodId = ingredientFood.id,
            amountServings = null,
            amountGrams = 150.0,
            sortOrder = 0,
        )

        val useCase = buildUseCase(
            foods = listOf(recipeFood, ingredientFood),
            recipe = recipe,
            ingredients = listOf(ingredient),
            variant = variant,
            snapshots = listOf(
                snapshot(
                    foodId = ingredientFood.id,
                    gramsPerServingUnit = null,
                    caloriesPerGram = 2.0,
                    proteinPerGram = 0.20,
                )
            ),
        )

        val result = useCase(variant.id)

        assertEquals(300.0, result.totals[NutrientKey.CALORIES_KCAL], 0.0001)
        assertEquals(30.0, result.totals[NutrientKey.PROTEIN_G], 0.0001)
        assertEquals(100.0, result.perServing!![NutrientKey.CALORIES_KCAL], 0.0001)
        assertEquals(10.0, result.perServing!![NutrientKey.PROTEIN_G], 0.0001)
    }

    @Test
    fun variantBatchTotal_dividesByVariantServingYield() = runBlocking {
        val recipeFood = recipeFood()
        val ingredientFood = ingredientFood(id = 12L)
        val recipe = recipe(recipeFoodId = recipeFood.id, servingsYield = 6.0)
        val variant = variant(
            recipeFoodId = recipeFood.id,
            servingsYieldOverride = 6.0,
        )

        val ingredient = RecipeIngredientEntity(
            id = 302L,
            recipeId = recipe.id,
            foodId = ingredientFood.id,
            amountServings = null,
            amountGrams = 5262.0,
            sortOrder = 0,
        )

        val useCase = buildUseCase(
            foods = listOf(recipeFood, ingredientFood),
            recipe = recipe,
            ingredients = listOf(ingredient),
            variant = variant,
            snapshots = listOf(
                snapshot(
                    foodId = ingredientFood.id,
                    gramsPerServingUnit = null,
                    caloriesPerGram = 1.0,
                    proteinPerGram = 0.0,
                )
            ),
        )

        val result = useCase(variant.id)

        assertEquals(5262.0, result.totals[NutrientKey.CALORIES_KCAL], 0.0001)
        assertEquals(877.0, result.perServing!![NutrientKey.CALORIES_KCAL], 0.0001)
    }

    @Test
    fun totalYieldGrams_producesPerCookedGramAndGramsPerServing() = runBlocking {
        val recipeFood = recipeFood()
        val ingredientFood = ingredientFood(id = 13L)
        val recipe = recipe(
            recipeFoodId = recipeFood.id,
            servingsYield = 4.0,
            totalYieldGrams = 800.0,
        )
        val variant = variant(recipeFoodId = recipeFood.id)

        val ingredient = RecipeIngredientEntity(
            id = 303L,
            recipeId = recipe.id,
            foodId = ingredientFood.id,
            amountServings = null,
            amountGrams = 400.0,
            sortOrder = 0,
        )

        val useCase = buildUseCase(
            foods = listOf(recipeFood, ingredientFood),
            recipe = recipe,
            ingredients = listOf(ingredient),
            variant = variant,
            snapshots = listOf(
                snapshot(
                    foodId = ingredientFood.id,
                    gramsPerServingUnit = null,
                    caloriesPerGram = 2.0,
                    proteinPerGram = 0.10,
                )
            ),
        )

        val result = useCase(variant.id)

        assertNotNull(result.perCookedGram)
        assertEquals(800.0, result.totals[NutrientKey.CALORIES_KCAL], 0.0001)
        assertEquals(1.0, result.perCookedGram!![NutrientKey.CALORIES_KCAL], 0.0001)
        assertEquals(200.0, result.gramsPerServingCooked!!, 0.0001)
    }

    private fun buildUseCase(
        foods: List<FoodEntity>,
        recipe: RecipeEntity,
        ingredients: List<RecipeIngredientEntity>,
        variant: RecipeVariantEntity,
        changes: List<RecipeVariantIngredientChangeEntity> = emptyList(),
        snapshots: List<FoodNutritionSnapshot>,
    ): ComputeRecipeVariantNutritionUseCase {
        val assembleRecipeVariant = AssembleRecipeVariantUseCase(
            recipeVariantRepository = FakeRecipeVariantRepository(
                variant = variant,
                changes = changes,
            ),
            recipeDao = FakeRecipeDao(recipe),
            recipeIngredientDao = FakeRecipeIngredientDao(ingredients),
            foodDao = FakeFoodDao(foods),
        )

        return ComputeRecipeVariantNutritionUseCase(
            assembleRecipeVariant = assembleRecipeVariant,
            snapshotRepository = FakeSnapshotRepository(snapshots),
        )
    }

    private fun recipeFood(): FoodEntity =
        FoodEntity(
            id = 100L,
            stableId = "recipe-food-100",
            name = "Chicken Gochugang",
            brand = null,
            servingSize = 1.0,
            servingUnit = ServingUnit.SERVING,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            servingsPerPackage = null,
            isRecipe = true,
        )

    private fun ingredientFood(
        id: Long,
        servingSize: Double = 1.0,
    ): FoodEntity =
        FoodEntity(
            id = id,
            stableId = "ingredient-food-$id",
            name = "Ingredient $id",
            brand = null,
            servingSize = servingSize,
            servingUnit = ServingUnit.SERVING,
            gramsPerServingUnit = null,
            mlPerServingUnit = null,
            servingsPerPackage = null,
            isRecipe = false,
        )

    private fun recipe(
        recipeFoodId: Long,
        servingsYield: Double,
        totalYieldGrams: Double? = null,
    ): RecipeEntity =
        RecipeEntity(
            id = 200L,
            stableId = "recipe-200",
            foodId = recipeFoodId,
            name = "Chicken Gochugang",
            servingsYield = servingsYield,
            totalYieldGrams = totalYieldGrams,
            isDeleted = false,
            deletedAtEpochMs = null,
            createdAt = Instant.EPOCH,
        )

    private fun variant(
        recipeFoodId: Long,
        servingsYieldOverride: Double? = null,
        totalYieldGramsOverride: Double? = null,
    ): RecipeVariantEntity =
        RecipeVariantEntity(
            id = 400L,
            recipeFoodId = recipeFoodId,
            name = "Test",
            notes = null,
            isArchived = false,
            servingsYieldOverride = servingsYieldOverride,
            totalYieldGramsOverride = totalYieldGramsOverride,
            nutrientsJsonSnapshot = null,
            createdAtEpochMillis = 0L,
            updatedAtEpochMillis = 0L,
        )

    private fun snapshot(
        foodId: Long,
        gramsPerServingUnit: Double?,
        caloriesPerGram: Double,
        proteinPerGram: Double,
    ): FoodNutritionSnapshot =
        FoodNutritionSnapshot(
            foodId = foodId,
            gramsPerServingUnit = gramsPerServingUnit,
            mlPerServingUnit = null,
            nutrientsPerGram = NutrientMap(
                mapOf(
                    NutrientKey.CALORIES_KCAL to caloriesPerGram,
                    NutrientKey.PROTEIN_G to proteinPerGram,
                )
            ),
            nutrientsPerMilliliter = null,
        )

    private class FakeSnapshotRepository(
        snapshots: List<FoodNutritionSnapshot>,
    ) : FoodNutritionSnapshotRepository {
        private val byFoodId = snapshots.associateBy { it.foodId }

        override suspend fun getSnapshot(foodId: Long): FoodNutritionSnapshot? =
            byFoodId[foodId]

        override suspend fun getSnapshots(foodIds: Set<Long>): Map<Long, FoodNutritionSnapshot> =
            byFoodId.filterKeys { it in foodIds }
    }

    private class FakeRecipeVariantRepository(
        private val variant: RecipeVariantEntity,
        private val changes: List<RecipeVariantIngredientChangeEntity>,
    ) : RecipeVariantRepository {

        override fun observeVariantsForRecipe(recipeFoodId: Long): Flow<List<RecipeVariantEntity>> =
            flowOf(if (recipeFoodId == variant.recipeFoodId) listOf(variant) else emptyList())

        override fun observeActiveVariantsForRecipe(recipeFoodId: Long): Flow<List<RecipeVariantEntity>> =
            flowOf(
                if (recipeFoodId == variant.recipeFoodId && !variant.isArchived) {
                    listOf(variant)
                } else {
                    emptyList()
                }
            )

        override suspend fun getVariantById(variantId: Long): RecipeVariantEntity? =
            if (variantId == variant.id) variant else null

        override suspend fun createVariant(
            recipeFoodId: Long,
            name: String,
            notes: String?,
            nowEpochMillis: Long,
        ): Long {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun updateVariant(variant: RecipeVariantEntity) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun archiveVariant(variantId: Long, nowEpochMillis: Long) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun restoreVariant(variantId: Long, nowEpochMillis: Long) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun deleteArchivedVariant(variantId: Long) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun getChangesForVariant(variantId: Long): List<RecipeVariantIngredientChangeEntity> =
            if (variantId == variant.id) changes else emptyList()

        override suspend fun replaceChangesForVariant(
            variantId: Long,
            changes: List<RecipeVariantIngredientChangeEntity>,
        ) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun updateVariantNutritionSnapshot(
            variantId: Long,
            nutrientsJsonSnapshot: String?,
            nowEpochMillis: Long,
        ) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }
    }

    private class FakeRecipeDao(
        private val recipe: RecipeEntity,
    ) : RecipeDao {

        override suspend fun insert(recipe: RecipeEntity): Long =
            recipe.id

        override fun observeAll(): Flow<List<RecipeEntity>> =
            flowOf(listOf(recipe))

        override suspend fun getAll(): List<RecipeEntity> =
            listOf(recipe)

        override suspend fun getById(recipeId: Long): RecipeEntity? =
            recipe.takeIf { it.id == recipeId }

        override suspend fun getByIds(recipeIds: List<Long>): List<RecipeEntity> =
            listOf(recipe).filter { it.id in recipeIds }

        override suspend fun getByFoodId(foodId: Long): RecipeEntity? =
            recipe.takeIf { it.foodId == foodId }

        override suspend fun getByFoodIds(foodIds: List<Long>): List<RecipeEntity> =
            listOf(recipe).filter { it.foodId in foodIds }

        override suspend fun getIdByStableId(stableId: String): Long? =
            recipe.id.takeIf { recipe.stableId == stableId }

        override suspend fun updateCore(
            id: Long,
            foodId: Long,
            name: String,
            servingsYield: Double,
        ) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun softDeleteById(
            recipeId: Long,
            deletedAtEpochMs: Long,
        ) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun softDeleteByFoodId(
            foodId: Long,
            deletedAtEpochMs: Long,
        ) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun countRecipes(): Int =
            1

        override fun observeByFoodId(foodId: Long): Flow<RecipeEntity?> =
            flowOf(recipe.takeIf { it.foodId == foodId })
    }

    private class FakeRecipeIngredientDao(
        private val ingredients: List<RecipeIngredientEntity>,
    ) : RecipeIngredientDao {

        override suspend fun insertAll(items: List<RecipeIngredientEntity>) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun getForRecipe(recipeId: Long): List<RecipeIngredientEntity> =
            ingredients
                .filter { it.recipeId == recipeId }
                .sortedBy { it.sortOrder }

        override suspend fun deleteForRecipe(recipeId: Long) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun deleteByIds(ingredientIds: List<Long>) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun countRecipesUsingFood(foodId: Long): Int =
            ingredients.count { it.foodId == foodId }

        override suspend fun setAmountGrams(
            ingredientId: Long,
            grams: Double?,
        ) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun setAmountServings(
            ingredientId: Long,
            servings: Double?,
        ) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }
    }

    private class FakeFoodDao(
        foods: List<FoodEntity>,
    ) : FoodDao {
        private val byId = foods.associateBy { it.id }
        private val byStableId = foods.associateBy { it.stableId }

        override suspend fun insert(entity: FoodEntity): Long =
            entity.id

        override suspend fun getByStableId(stableId: String): FoodEntity? =
            byStableId[stableId]

        override suspend fun upsertAll(items: List<FoodEntity>) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun upsert(item: FoodEntity) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun getById(id: Long): FoodEntity? =
            byId[id]

        override suspend fun deleteById(id: Long) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun getByIds(ids: List<Long>): List<FoodEntity> =
            ids.mapNotNull { byId[it] }

        override fun search(query: String, limit: Int): Flow<List<FoodEntity>> =
            flowOf(
                byId.values
                    .filter { it.name.contains(query, ignoreCase = true) }
                    .take(limit)
            )

        override suspend fun countFoods(): Int =
            byId.size

        override suspend fun getAll(): List<FoodEntity> =
            byId.values.toList()

        override suspend fun getIdByStableId(stableId: String): Long? =
            byStableId[stableId]?.id

        override suspend fun getByUsdaFdcId(fdcId: Long): FoodEntity? =
            null

        override suspend fun updateCore(
            id: Long,
            name: String,
            brand: String?,
            servingSize: Double,
            servingUnit: String,
            gramsPerServingUnit: Double?,
            isRecipe: Boolean,
        ) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun updateGramsPerServingUnit(
            id: Long,
            gramsPerServingUnit: Double?,
        ) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun softDeleteById(
            id: Long,
            deletedAtEpochMs: Long,
        ) {
            throw UnsupportedOperationException("Unused in ComputeRecipeVariantNutritionUseCaseTest.")
        }

        override suspend fun getStableIdById(id: Long): String? =
            byId[id]?.stableId

        override suspend fun getExistingFoodIds(ids: List<Long>): List<Long> =
            ids.filter { it in byId }

        override suspend fun getNamesByIds(ids: List<Long>): List<FoodIdNameRow> =
            ids.mapNotNull { id ->
                byId[id]?.let { food ->
                    FoodIdNameRow(id = food.id, name = food.name)
                }
            }
    }
}
