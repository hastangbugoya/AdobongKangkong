erDiagram
%% Logging / Timeline Extension (incremental)
%% Date: 2026-02-05

    foods {
        long id PK
        string stableId
        boolean isRecipe
        string name
    }

    log_entries {
        long id PK
        Instant timestamp
        string itemName
        string foodStableId
        double amount
        LogUnit unit
        long recipeBatchId
        double gramsPerServingCooked
        string nutrientsJson
    }

    recipe_batches {
        long id PK
        long recipeId
        long batchFoodId
        double cookedYieldGrams
        double servingsYieldUsed
        Instant createdAt
    }

    %% Relationships (as implemented today)
    %% NOTE: These are logical references (not Room ForeignKey in uploaded entities)
    foods ||--o{ log_entries : "logical (stableId -> foodStableId)"
    recipe_batches ||--o{ log_entries : "logical (id -> recipeBatchId)"
