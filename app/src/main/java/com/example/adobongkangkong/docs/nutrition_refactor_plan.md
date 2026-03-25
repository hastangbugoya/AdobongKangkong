# 🧩 AdobongKangkong --- Nutrition Editing Refactor Plan

## 🎯 Goal

Decouple **serving definition** from **nutrient editing** to: -
eliminate reactive UI bugs - improve user control and predictability -
centralize nutrition computation logic

------------------------------------------------------------------------

## 🧠 Core Concept

Two independent phases:

### 1. Serving Definition Phase

User edits: - serving amount - serving unit - grams per serving unit
(optional) - mL per serving unit (optional)

Action: ➡️ **Recompute Displayed Nutrients**

------------------------------------------------------------------------

### 2. Nutrient Editing Phase

User edits: - nutrient values (manual input)

Action: ➡️ **Apply Nutrient Edits**

------------------------------------------------------------------------

## 🔁 Data Flow Rules

### Recompute (Canonical → UI)

-   Input: canonical nutrients (PER_100G / PER_100ML)
-   Output: UI nutrient fields

### Apply Nutrients (UI → Canonical)

-   Input: UI nutrient fields
-   Output: canonical storage

🚫 These must NEVER be combined.

------------------------------------------------------------------------

## 🏗️ Implementation Phases

------------------------------------------------------------------------

### ✅ Phase 1 --- Serving Resolution Layer

Create: `ServingResolution`

Responsibilities: - resolve grams per serving - resolve mL per serving -
determine compatibility with basis - NO UI logic

Inputs: - servingSize - servingUnit - gramsPerServingUnit -
mlPerServingUnit

Outputs: - gramsPerServing: Double? - mlPerServing: Double? -
supportsPer100G: Boolean - supportsPer100ML: Boolean

------------------------------------------------------------------------

### ✅ Phase 2 --- Recompute Use Case

Create: `RecomputeNutrientsFromCanonicalUseCase`

Input: - canonical nutrients - ServingResolution

Output: - UI nutrient values

------------------------------------------------------------------------

### ✅ Phase 3 --- Apply Nutrients Use Case

Create: `ApplyEditedNutrientsUseCase`

Input: - edited UI nutrient values - ServingResolution

Output: - canonical nutrients (PER_100G or PER_100ML)

------------------------------------------------------------------------

### ✅ Phase 4 --- FoodEditorViewModel Changes

Add state:

``` kotlin
val isServingDirty: Boolean
val isNutrientsDirty: Boolean
```

Add events:

``` kotlin
onServingChanged(...)
onRecomputeClicked()
onNutrientChanged(...)
onApplyNutrientsClicked()
```

------------------------------------------------------------------------

### ✅ Phase 5 --- UI Changes

#### Serving Section

-   inputs (size, unit, bridges)
-   button: **Recompute Displayed Nutrients**

Show warning: - "Nutrient values are out of date"

------------------------------------------------------------------------

#### Nutrient Section

-   editable nutrient fields
-   button: **Apply Nutrient Edits**

Show warning: - "Nutrient edits not applied"

------------------------------------------------------------------------

### ⚠️ Phase 6 --- Conflict Handling

If: - nutrients dirty - user taps recompute

➡️ Show dialog: "Discard manual edits and recompute?"

------------------------------------------------------------------------

### ✅ Phase 7 --- Save Behavior

Save should: - persist serving fields - persist canonical nutrients

Block or warn if: - serving not recomputed - nutrients not applied

------------------------------------------------------------------------

## 🚫 Anti-Patterns to Avoid

-   ❌ Live recalculation on every keystroke
-   ❌ Mixing UI display math with storage math
-   ❌ Multiple scattered conversion logic
-   ❌ Implicit conversions without user awareness

------------------------------------------------------------------------

## 🧠 Future Extensions

-   Bridge confidence system integration
-   External app snapshot compatibility
-   Meal planner / recipe alignment
-   Nutrition audit/debug mode

------------------------------------------------------------------------

## 🏁 Summary

This refactor introduces: - deterministic computation pipeline -
explicit user actions - separation of concerns

Result: ✔ predictable UI\
✔ fewer bugs\
✔ scalable architecture
