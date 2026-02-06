flowchart TD
    %% Delete Rules (manual vs cascade)
    %% Date: 2026-02-05
    %%
    %% Legend:
    %%   solid arrow = operation performed by deleteFood(foodId) (manual)
    %%   dotted arrow = DB cascade (Room FK onDelete = CASCADE)
    %%   block = guard condition that prevents deletion

    A[Request: deleteFood(foodId)] --> G{Is food referenced in\nrecipe_ingredient.foodId ?}
    G -- YES --> B[BLOCK DELETE\nReturn false\n(UI shows error)]
    G -- NO --> N[Proceed with delete]

    N --> M1[MANUAL: food_goal_flags\nDELETE WHERE foodId = :foodId]
    N --> M2[MANUAL: food_nutrients\nDELETE WHERE foodId = :foodId\n(optional; see note)]
    N --> M3[MANUAL: foods\nDELETE WHERE id = :foodId]

    %% DB-level cascade you have today:
    M3 -. DB CASCADE .-> C[CASCADE: food_nutrients\nFK food_nutrients.foodId -> foods.id\nonDelete=CASCADE]

    %% What is NOT cascaded today (logical only):
    L1[NOT CASCADED:\nrecipes.foodId (logical)\nrecipe_batches.batchFoodId (logical)\nlog_entries.foodStableId (logical)]:::note

    classDef note fill:#f6f6f6,stroke:#999,color:#333,stroke-dasharray: 3 3;
