%% =====================================================================
%% AdobongKangkong – Consolidated Canonical ERD + Appendices (Mermaid)
%% Source-of-truth: Room @Entity files + NutriDatabase registration
%% Date: 2026-02-05
%% =====================================================================

%% ---------------------------------------------------------------------
%% 1) Canonical ERD (core schema)
%% ---------------------------------------------------------------------
erDiagram
foods ||--o{ food_nutrients : has
foods ||--o| food_goal_flags : has
foods ||--o{ recipes : represents
recipes ||--o{ recipe_ingredients : contains
foods ||--o{ recipe_ingredients : used_as

    %% Nutrient catalog / aliasing / user settings
    nutrients ||--o{ food_nutrients : referenced_by
    nutrients ||--o{ nutrient_aliases : has
    nutrients ||--o| user_nutrient_targets : targeted_by
    nutrients ||--o{ user_pinned_nutrients : pinned_as

    %% Logging + batches (logical refs)
    foods ||--o{ log_entries : "logical (stableId -> foodStableId)"
    recipe_batches ||--o{ log_entries : "logical (id -> recipeBatchId)"
    recipes ||--o{ recipe_batches : "logical (recipes.id -> recipe_batches.recipeId)"
    foods ||--o{ recipe_batches : "logical snapshot (foods.id -> recipe_batches.batchFoodId)"

    foods {
        long id PK
        string stableId
        string name
        string brand
        double servingSize
        ServingUnit servingUnit
        string householdServingText
        double servingsPerPackage
        double gramsPerServingUnit
        boolean isRecipe
        boolean isLowSodium
        long usdaFdcId
        string usdaGtinUpc
        string usdaPublishedDate
        double usdaServingSize
        ServingUnit usdaServingUnit
        string usdaHouseholdServingText
    }

    food_goal_flags {
        long foodId PK
        boolean eatMore
        boolean limit
        boolean favorite
        Instant updatedAt
    }

    food_nutrients {
        long foodId PK, FK
        long nutrientId PK, FK
        BasisType basisType PK
        double nutrientAmountPerBasis
        NutrientUnit unit
    }

    nutrients {
        long id PK
        string code
        string displayName
        NutrientUnit unit
        NutrientCategory category
        boolean isEnergy
        boolean isMacro
        boolean isVitamin
        boolean isMineral
        boolean isTrackedByDefault
    }

    nutrient_aliases {
        long id PK
        long nutrientId FK
        string aliasKey
        string aliasDisplay
    }

    user_nutrient_targets {
        long nutrientId PK, FK
        double minValue
        double targetValue
        double maxValue
        boolean enabled
        Instant updatedAt
    }

    user_pinned_nutrients {
        long slotIndex PK
        long nutrientId FK
        Instant updatedAt
    }

    recipes {
        long id PK
        string stableId
        long foodId FK
        string name
        double servingsYield
        double totalYieldGrams
        Instant createdAt
    }

    recipe_ingredients {
        long id PK
        long recipeId FK
        long foodId FK
        double amountServings
        double amountGrams
        int sortOrder
    }

    recipe_batches {
        long id PK
        long recipeId
        long batchFoodId
        double cookedYieldGrams
        double servingsYieldUsed
        Instant createdAt
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

%% ---------------------------------------------------------------------
%% 2) Delete rules (manual vs cascade) + "block if referenced"
%% ---------------------------------------------------------------------
flowchart TD
%% Legend:
%%   solid arrow = manual delete operations (repo/use case)
%%   dotted arrow = DB cascade (Room FK onDelete=CASCADE)
%%   block = guard condition prevents deletion

    A[Request: deleteFood(foodId)] --> G{Referenced in\nrecipe_ingredients.foodId ?}
    G -- YES --> B[BLOCK DELETE\nReturn false]
    G -- NO --> N[Proceed]

    N --> M1[MANUAL: food_goal_flags\nDELETE WHERE foodId=:foodId]
    N --> M2[MANUAL: food_nutrients\nDELETE WHERE foodId=:foodId\n(optional redundancy)]
    N --> M3[MANUAL: foods\nDELETE WHERE id=:foodId]

    %% Actual cascade you have:
    M3 -. DB CASCADE .-> C[CASCADE: food_nutrients\nFK food_nutrients.foodId -> foods.id\nonDelete=CASCADE]

    %% Not cascaded today (logical only):
    L1[NOT CASCADED (logical refs only):\nrecipes.foodId\nrecipe_batches.batchFoodId\nlog_entries.foodStableId]:::note

    classDef note fill:#f6f6f6,stroke:#999,color:#333,stroke-dasharray: 3 3;

%% ---------------------------------------------------------------------
%% 3) Flag ownership (why flags are keyed by foodId)
%% ---------------------------------------------------------------------
flowchart LR
F[foods\n(id PK)\n(isRecipe=true => this food is a recipe)] -->|identity bridge| R[recipes\n(foodId -> foods.id)]
F -->|keyed by foodId| GF[food_goal_flags\n(foodId PK)\nfavorite/eatMore/limit]

    subgraph Contract["Locked-in contract"]
      C1[FoodGoalFlagsEntity keyed by foodId]
      C2[Recipes are represented by Food row]
      C3[Therefore recipe flags saved/loaded using recipe.foodId]
    end

    C1 --> GF
    C2 --> F
    C3 --> GF

    U[CreateRecipeUseCase (Option A)\nreturns new recipe FOOD ID] -->|use returned foodId| GF

%% ---------------------------------------------------------------------
%% 4) Recipe batch creation flow
%% ---------------------------------------------------------------------
flowchart TD
X[CreateRecipeBatchUseCase.invoke(recipeId, cookedYieldGrams, servingsYieldUsed)] --> V{cookedYieldGrams > 0 ?}
V -- NO --> E[throw IllegalArgumentException]
V -- YES --> S1[1) CreateSnapshotFoodFromRecipeUseCase.execute(...)]
S1 --> BF[Result: batchFoodId (snapshot FOOD ID)]
BF --> S2[2) Insert RecipeBatchEntity\n(recipeId, batchFoodId, cookedYieldGrams, servingsYieldUsed, createdAt)]
S2 --> OUT[return batchId (recipe_batches.id)]

%% ---------------------------------------------------------------------
%% 5) End-to-end nutrient flow (catalog → food → log → dashboard)
%% ---------------------------------------------------------------------
flowchart LR
NC[Nutrient Catalog\n(NutrientEntity)] --> FA[Nutrient Aliases\n(NutrientAliasEntity)]
NC --> FN[Food Nutrients\n(FoodNutrientEntity)]
F2[Food / Recipe\n(FoodEntity)] --> FN
F2 --> LE[Log Entry\n(LogEntryEntity)]
LE -->|snapshot nutrientsJson| DASH[Dashboard / Timeline]
NC --> UT[User Targets\n(UserNutrientTargetEntity)]
NC --> UP[User Pinned\n(UserPinnedNutrientEntity)]
UT --> DASH
UP --> DASH
