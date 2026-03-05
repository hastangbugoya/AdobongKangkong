# TEMPLATE_PICKER_SYSTEM.md
Project: AdobongKangkong  
Last updated: 2026-03-04

Purpose  
Document the **Meal Template Picker** system: its data flow, architecture, macro computation pipeline, and future improvements.

This document exists to:
- help future development
- help future AI assistants avoid regressions
- prevent incorrect refactors
- clarify how template macros are computed

The Template Picker is tightly integrated with the Planner system but operates independently when selecting templates.

---

# 1) User Flow

Entry point:

PlannerDayScreen  
→ User taps **"+ Add"** on a meal slot  
→ Bottom sheet opens

Options:

```
New empty meal
From template
```

Selecting **From template** navigates to:

```
TemplatePickerScreen
```

User actions in picker:

- Search templates by name
- View macro summary for each template
- Tap **Pick**

Result:

```
CreatePlannedMealFromTemplateUseCase
    ↓
PlannedMeal created
    ↓
PlannedItems copied from template
    ↓
Navigate to PlannedMealEditor
```

---

# 2) High-Level Architecture

Clean Architecture layering:

```
TemplatePickerScreen (UI)
        ↓
MealTemplatePickerViewModel
        ↓
Domain UseCases
        ↓
Repositories
        ↓
Room DAO / Database
```

Key rule:

**Template Picker UI never computes nutrition itself.**  
Macro totals are computed in the **domain layer**.

---

# 3) Core Database Tables

## meal_templates

Stores template metadata.

Fields:

```
id
name
createdAt
```

Important rule:

Templates use **user-defined names**, not meal slots.

Example:

```
"High Protein Breakfast"
"Cutting Lunch"
"Bulk Shake"
```

---

## meal_template_items

Represents template contents.

Structure:

```
id
templateId
type
refId
grams
servings
sortOrder
```

### type (PlannedItemSource)

Defines the item reference:

```
FOOD
RECIPE
RECIPE_BATCH
```

### refId

Depends on type:

```
FOOD         → foodId
RECIPE       → recipeId
RECIPE_BATCH → recipeBatchId
```

### Amount fields

Only one is typically used:

```
grams
servings
```

Rules:

```
grams != null → use grams directly
servings != null → convert using serving bridge
```

---

# 4) Template Picker UI State

ViewModel exposes:

```
MealTemplatePickerUiState
```

Structure:

```
query: String
templates: List<TemplateSummary>
```

Where:

```
TemplateSummary
    templateId
    name
    macroTotals
```

MacroTotals contains:

```
caloriesKcal
proteinG
carbsG
fatG
```

---

# 5) Macro Computation Pipeline

Templates do **not store nutrition values**.

Nutrition is computed dynamically.

Pipeline:

```
meal_templates
        ↓
meal_template_items
        ↓
resolve item source
        ↓
resolve snapshot foodId
        ↓
FoodNutritionSnapshot
        ↓
scale nutrients by grams or servings
        ↓
sum item macros
        ↓
Template macro totals
```

---

# 6) Source Resolution

Each template item must resolve to a **snapshot-backed foodId**.

Mapping:

```
FOOD
    refId = foodId

RECIPE
    recipeId → recipe.foodId

RECIPE_BATCH
    batchId → batchFoodId
```

Bulk lookups exist for performance:

```
getFoodIdsByRecipeIds(...)
getBatchFoodIds(...)
```

This prevents N+1 database queries.

---

# 7) Snapshot Nutrition

All macro math is derived from:

```
FoodNutritionSnapshot
```

Key properties:

```
nutrientsPerGram
nutrientsPerMilliliter

gramsPerServingUnit
mlPerServingUnit
```

Canonical macro keys:

```
CALORIES_KCAL
PROTEIN_G
CARBS_G
FAT_G
```

---

# 8) Scaling Logic

Each template item contributes macros based on its amount.

### Case 1 — grams

```
macro = nutrientsPerGram * grams
```

### Case 2 — servings

Convert servings → base unit:

Preferred order:

```
servings * gramsPerServingUnit
servings * mlPerServingUnit
```

Then apply nutrient map.

### Case 3 — missing bridges

If no bridge exists:

```
item contributes 0 macros
```

This is intentional best-effort behavior.

---

# 9) Macro Totals Output

Template totals are:

```
MacroTotals
    caloriesKcal
    proteinG
    carbsG
    fatG
```

Picker displays:

```
403 kcal • P 23 • C 53 • F 11
```

---

# 10) Performance Design

Key principle:

**Never compute macros with repeated database queries.**

Correct approach:

```
load template items in bulk
resolve recipe/batch mappings in bulk
load snapshots in bulk
compute totals in memory
```

This keeps the picker fast even with many templates.

---

# 11) Correctness Rules

### Canonical calorie key

Must always use:

```
CALORIES_KCAL
```

Never:

```
CALORIES
```

---

### Best-effort design

Templates should **never crash** due to missing nutrition.

Missing snapshot → contributes 0.

---

### Domain-only computation

UI must not:

```
convert servings
load snapshots
compute macros
```

All macro logic must remain in domain/use cases.

---

# 12) Template Naming Rule

Picker displays **template names**.

Never display:

```
meal slot
```

Templates may represent any meal type.

Examples:

```
"High Protein Breakfast"
"Cutting Lunch"
"Pre-workout"
```

---

# 13) Future Improvements

### A) Show food count

Example:

```
High Protein Breakfast
403 kcal • P 23 • C 53 • F 11
2 foods
```

---

### B) Macro goal comparison

Display relative to user goals.

Example:

```
403 kcal (20% of daily)
```

---

### C) Template categories

Allow grouping:

```
Breakfast templates
Lunch templates
Bulk meals
Cutting meals
```

---

### D) Template preview

Expandable card showing foods inside template.

---

### E) Template macro caching

Optionally store computed totals in:

```
meal_template_macros
```

Only update when template changes.

This would make picker rendering instant.

---

# 14) Troubleshooting

If picker shows `0 kcal`:

Check:

1. Snapshot exists for referenced food
2. Macro key is `CALORIES_KCAL`
3. grams or servings is present
4. serving bridge exists if servings used
5. snapshot maps contain macro keys

---

# End

Template Picker follows the same principles as the planner:

- nutrition derived from snapshots
- amounts stored separately
- macro math centralized in domain
- UI remains simple and reactive
