erDiagram
    %% Recipe Batch Schema Links
    %% Date: 2026-02-05

    recipes {
        long id PK
        long foodId
        double servingsYield
        double totalYieldGrams
    }

    foods {
        long id PK
        boolean isRecipe
        string stableId
        string name
    }

    recipe_batches {
        long id PK
        long recipeId
        long batchFoodId
        double cookedYieldGrams
        double servingsYieldUsed
        Instant createdAt
    }

    %% Logical links (no FK annotations in the entity you uploaded)
    recipes ||--o{ recipe_batches : "logical (recipes.id -> recipe_batches.recipeId)"
    foods ||--o{ recipe_batches : "logical snapshot (foods.id -> recipe_batches.batchFoodId)"
