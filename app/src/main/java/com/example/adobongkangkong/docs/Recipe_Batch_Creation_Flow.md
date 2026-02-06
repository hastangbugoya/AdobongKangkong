flowchart TD
    %% Recipe Batch Creation Flow
    %% Source: CreateRecipeBatchUseCase.kt
    %% Date: 2026-02-05

    A[CreateRecipeBatchUseCase.invoke(recipeId, cookedYieldGrams, servingsYieldUsed)] --> V{cookedYieldGrams > 0 ?}
    V -- NO --> E[throw IllegalArgumentException]
    V -- YES --> S1[1) CreateSnapshotFoodFromRecipeUseCase.execute(...)]
    S1 --> BF[Result: batchFoodId (FOOD ID of snapshot food)]
    BF --> S2[2) Insert RecipeBatchEntity\n(recipeId, batchFoodId, cookedYieldGrams, servingsYieldUsed, createdAt)]
    S2 --> OUT[return recipe_batches.id (batchId)]
