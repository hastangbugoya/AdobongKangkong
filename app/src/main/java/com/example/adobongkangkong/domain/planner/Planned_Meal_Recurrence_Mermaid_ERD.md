# Planned Meal / Item Recurrence Refactor --- Mermaid ERD

**Generated:** 2026-02-22 21:41 UTC

> This ERD is conceptual and intended for Room-friendly relational
> modeling. Field lists are representative (not exhaustive).

``` mermaid
erDiagram
    PLANNED_SERIES ||--o{ PLANNED_SERIES_SLOT_RULE : defines
    PLANNED_SERIES ||--o{ PLANNED_MEAL_OCCURRENCE : expands_to
    PLANNED_MEAL_OCCURRENCE ||--o{ PLANNED_ITEM_OCCURRENCE : contains
    PLANNED_SERIES ||--o{ PLANNED_SERIES_EXCEPTION : has

    FOOD ||--o{ PLANNED_ITEM_OCCURRENCE : references
    RECIPE ||--o{ PLANNED_ITEM_OCCURRENCE : references

    %% =========================
    %% Core entities
    %% =========================

    PLANNED_SERIES {
        long id PK
        string title
        date effectiveStartDate
        date effectiveEndDate "nullable"
        string endConditionType "UNTIL_DATE|REPEAT_COUNT|INDEFINITE"
        string endConditionValue "date or int, depending on type"
        long createdAtEpochMs
        long updatedAtEpochMs
    }

    PLANNED_SERIES_SLOT_RULE {
        long id PK
        long seriesId FK
        int weekday "1=Mon..7=Sun"
        string slot "BREAKFAST|LUNCH|DINNER|SNACK|CUSTOM"
        string customSlotLabel "nullable"
        long createdAtEpochMs
    }

    %% Materialized, queryable occurrences used by PlannerDay / Shopping List
    PLANNED_MEAL_OCCURRENCE {
        long id PK
        long seriesId FK "nullable for one-offs"
        date date
        string slot "BREAKFAST|LUNCH|DINNER|SNACK|CUSTOM"
        string customSlotLabel "nullable"
        string status "ACTIVE|CANCELLED|OVERRIDDEN"
        long createdAtEpochMs
        long updatedAtEpochMs
    }

    PLANNED_ITEM_OCCURRENCE {
        long id PK
        long mealOccurrenceId FK
        long foodId FK "nullable"
        long recipeId FK "nullable"
        double quantity
        string quantityBasis "G|ML|SERVING|CUSTOM"
        string notes "nullable"
        long createdAtEpochMs
        long updatedAtEpochMs
    }

    %% Exceptions enable skipping/overriding a specific date without rewriting the series
    PLANNED_SERIES_EXCEPTION {
        long id PK
        long seriesId FK
        date date
        string exceptionType "SKIP|OVERRIDE_SLOT|OVERRIDE_ITEMS"
        string payloadJson "nullable (for future)"
        long createdAtEpochMs
    }

    %% Reference entities (existing)
    FOOD {
        long id PK
        string name
    }

    RECIPE {
        long id PK
        string name
    }
```

## Notes

-   **PlannerDay / ObservePlannedDay** should read only
    `PLANNED_MEAL_OCCURRENCE` + `PLANNED_ITEM_OCCURRENCE` for a given
    date.
-   Recurrence rule evaluation happens only during **bounded expansion**
    into occurrences (e.g., ensure horizon up to 180 days).
-   Series "history" is preserved via **series versioning** (ending old
    series, creating new) rather than a separate audit table.
-   Exceptions can be implemented minimally at first (e.g., mark
    occurrence `CANCELLED`) and evolve into `PLANNED_SERIES_EXCEPTION`
    as Phase 2.
