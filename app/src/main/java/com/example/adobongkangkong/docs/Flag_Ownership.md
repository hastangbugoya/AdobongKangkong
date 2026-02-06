flowchart LR
    %% Flag Ownership (FoodGoalFlags)
    %% Date: 2026-02-05

    F[foods\n(id PK)\n(isRecipe=true means: "this food is a recipe")] -->|identity| R[recipes\n(foodId FK -> foods.id)\n(id PK)]

    F -->|keyed by foodId| G[food_goal_flags\n(foodId PK)\nfavorite / eatMore / limit]

    subgraph Contract["Locked-in contract"]
      C1[FoodGoalFlagsEntity is keyed by foodId]
      C2[Recipes are represented by Food row (isRecipe=true)]
      C3[Therefore recipe flags MUST be saved/loaded using recipe.foodId]
    end

    C1 --> G
    C2 --> F
    C3 --> G

    U[CreateRecipeUseCase (Option A)\nreturns new recipe FOOD ID] -->|use this id| G
