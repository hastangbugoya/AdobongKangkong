erDiagram
    %% User Targets & Pinned Nutrients
    %% Date: 2026-02-05

    nutrients {
        long id PK
        string code
        string displayName
        NutrientUnit unit
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

    nutrients ||--o| user_nutrient_targets : "targeted by"
    nutrients ||--o{ user_pinned_nutrients : "pinned as"
