flowchart LR
    %% End-to-End Nutrient Flow
    %% Date: 2026-02-05

    NC[Nutrient Catalog\n(NutrientEntity)]
    FA[Nutrient Aliases\n(NutrientAliasEntity)]
    FN[Food Nutrients\n(FoodNutrientEntity)]
    F[Food / Recipe\n(FoodEntity)]
    LE[Log Entry\n(LogEntryEntity)]
    UT[User Targets\n(UserNutrientTargetEntity)]
    UP[User Pinned\n(UserPinnedNutrientEntity)]

    NC --> FA
    NC --> FN
    F --> FN
    F --> LE
    LE -->|snapshot nutrientsJson| DASH[Dashboard / Timeline]
    NC --> UT
    NC --> UP
    UT --> DASH
    UP --> DASH
