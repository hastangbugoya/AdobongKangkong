# PLANNER_SYSTEM.md
Project: AdobongKangkong  
Last updated: 2026-03-04

Purpose  
Document the Planner feature end-to-end: user flow, core data types, macro totals pipeline (per meal + per day), and future improvements.

This doc is intended for:
- future-you
- future AI assistants
- preventing regressions and “helpful refactors” that break planner behavior

---

## 1) User Flow Overview

### PlannerDayScreen (Calendar Day View)
The planner shows a single day with multiple meal slots (Breakfast/Lunch/Dinner/Snack/Custom).

User actions:
- Tap **+ Add** on a meal slot
    - Create **new empty meal** → navigates to Planned Meal Editor
    - Choose **from template** → opens Template Picker → creates meal → navigates to Planned Meal Editor
- Tap a meal card to open it in the editor
- From the editor:
    - add foods/recipes
    - edit grams/servings
    - remove items (undo supported if enabled)
    - save as template
    - log meal
    - make recurring (series)

---

## 2) High-Level Architecture

Clean Architecture flow:

UI  
↓  
ViewModel  
↓  
UseCases (Domain)  
↓  
Repositories (Domain interfaces)  
↓  
Data repositories / DAO / Room

Planner-specific rule:
- UI **never** computes nutrition or queries DB
- ViewModel coordinates
- UseCases perform business logic (including macro totals computation)
- Repositories provide data & snapshot resolution

---

## 3) Core Data Types

### Meal slots
`MealSlot` (enum)  
Represents sections of the day: Breakfast/Lunch/Dinner/Snack/Custom.

### PlannedMeal
Represents a single meal instance on a specific day and slot.

Key identity:
- `plannedMealId: Long`
- `dateIso: String` (`yyyy-MM-dd`)
- `mealSlot: MealSlot`

### PlannedItem
A line item inside a planned meal.

Fields (conceptual):
- `plannedItemId`
- `plannedMealId`
- `type: PlannedItemSource`  
  Usually one of:
    - `FOOD`
    - `RECIPE`
    - `RECIPE_BATCH`
- `refId: Long`
    - FOOD: foodId
    - RECIPE: recipeId (or recipe snapshot food id depending on design)
    - RECIPE_BATCH: recipeBatchId
- amount:
    - `grams: Double?`
    - `servings: Double?`

Amount rule:
- Exactly one of `grams` or `servings` is expected in most UI flows
- If both are null → the item contributes 0 to macro totals (best effort)

### FoodNutritionSnapshot
The single source of truth for macro math.

Snapshot provides normalized nutrient maps:
- `nutrientsPerGram: Map<NutrientKey, Double>?`
- `nutrientsPerMilliliter: Map<NutrientKey, Double>?`

And bridges for servings conversion:
- `gramsPerServingUnit: Double?`
- `mlPerServingUnit: Double?`

Macro keys are canonical (examples):
- `CALORIES_KCAL`
- `PROTEIN_G`
- `CARBS_G`
- `FAT_G`

---

## 4) Macro Totals Feature

### What is shown
- **Per-meal totals**: macro totals computed from that meal’s items
- **Day totals**: sum across all meals in the day

Displayed format (current):
`kcal • P • C • F`

### Why this is computed (not stored)
Planner tables store:
- references (food/recipe/batch)
- amounts (grams/servings)

They do NOT store nutrients.
This avoids drift and keeps nutrition updates centralized in snapshots.

---

## 5) Macro Computation Pipeline

### Inputs
- All planned meals for a day
- All planned items in those meals

### Step A: Resolve “snapshot food id”
Each planned item must resolve to a snapshot-backed foodId:
- FOOD → `foodId = refId`
- RECIPE → resolve to recipe’s snapshot foodId (or recipe foodId depending on design)
- RECIPE_BATCH → resolve to batch’s `batchFoodId`

This is why bulk lookup helpers exist:
- recipeId → foodId
- batchId → batchFoodId

### Step B: Load snapshots in bulk
Once all relevant foodIds are known:
- fetch `FoodNutritionSnapshot` in bulk (to avoid N+1 lookups)

### Step C: Scale each planned item
For each planned item:
- If `grams != null`:
    - prefer `nutrientsPerGram * grams`
- Else if `servings != null`:
    - if `gramsPerServingUnit` exists:
        - grams = servings * gramsPerServingUnit
        - use `nutrientsPerGram * grams`
    - else if `mlPerServingUnit` exists:
        - ml = servings * mlPerServingUnit
        - use `nutrientsPerMilliliter * ml`
    - else:
        - contributes 0 (best effort)

### Step D: Aggregate
- Sum item macros → meal total
- Sum meal totals → day total

Output shape:
- `mealTotals: Map<Long, MacroTotals>` (keyed by plannedMealId)
- `dayTotals: MacroTotals`

---

## 6) UI Integration

### Where totals live
`PlannerDayUiState` stores:
- `mealMacroTotals: Map<Long, MacroTotals>`
- `dayMacroTotals: MacroTotals`

### When totals are computed
In `PlannerDayViewModel`:
- observe the planned day data (reactive)
- whenever the day changes:
    - compute totals
    - update ui state

UI then renders:
- day totals at top
- per-meal totals inside meal cards

---

## 7) Correctness Rules / Invariants

### Canonical nutrient keys
Calories must use `CALORIES_KCAL` (not `CALORIES`).
Macro keys must match snapshot keys.

### Best-effort behavior
Missing data should not crash planner:
- missing snapshot → item contributes 0
- missing bridge → item contributes 0
- unknown source type → item contributes 0

### No UI-layer computation
Composables must not:
- query repositories
- compute totals
- convert servings to grams

All of that belongs in domain/use cases.

---

## 8) Performance Considerations

Main risk: N+1 queries.
The macro pipeline must:
- fetch all items for a day in one pass
- bulk-resolve recipe/batch mappings
- bulk-load snapshots

Compute should run in:
- ViewModel scope (not composable)
- ideally on a background dispatcher if the computation grows

---

## 9) Future Improvements (Recommended)

### A) Better UX display
- Show per-meal totals right-aligned next to title (more scannable)
- Show daily totals in a sticky header
- Allow toggling macro display on/off for clutter control

### B) Goal comparisons
Add:
- remaining kcal
- remaining protein/carb/fat
- per-meal “target vs actual” (future per-meal macro goal support)

### C) Cache macro results
If you notice recompute cost:
- cache computed totals keyed by:
    - dateIso
    - a simple hash/version of planned meals/items list
- only recompute when underlying data changes

### D) Persisted template macro snapshots (optional)
For templates you may choose to store totals in `MealTemplateEntity`
for instant picker rendering, but this requires:
- migration
- backfill
- decision about whether templates “freeze” nutrition or stay live

### E) Sugar and fiber support
If desired:
- add sugars key and show `Sugars` in UI
- optionally show fiber
- ensure canonical keys exist in snapshots

### F) Series-aware totals
If recurring meals expand across horizon:
- optionally show “weekly planned totals”
- allow exporting “planned week” summary

---

## 10) Troubleshooting Checklist

If totals show 0:
1. Confirm the ViewModel actually calls compute totals when planned day updates.
2. Confirm the planned items have either grams or servings.
3. Confirm snapshots exist for the resolved foodIds.
4. Confirm macro keys match canonical keys (`CALORIES_KCAL`, etc.).
5. Confirm servings bridges are present when servings is used.

If per-meal totals don’t show:
- check the meal card is passed the map lookup by `plannedMealId`

---

## End
This Planner system is intentionally designed to be:
- snapshot-driven
- bulk-computed
- best-effort
- UI-light

Any future changes should preserve these invariants.