erDiagram
    %% Nutrient Catalog & Aliases
    %% Date: 2026-02-05

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

    foods {
        long id PK
        string name
    }

    food_nutrients {
        long foodId PK, FK
        long nutrientId PK, FK
        BasisType basisType PK
        double nutrientAmountPerBasis
        NutrientUnit unit
    }

    nutrients ||--o{ nutrient_aliases : "has aliases"
    nutrients ||--o{ food_nutrients : "referenced by"
    foods ||--o{ food_nutrients : "contains"
