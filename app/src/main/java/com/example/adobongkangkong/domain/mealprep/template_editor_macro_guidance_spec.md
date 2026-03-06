# AdobongKangkong --- Template Editor Macro Guidance Specification

**Subsystem:** Meal Templates\
**Component:** Template Editor\
**Purpose:** Prevent design drift and standardize macro‑goal guidance
behavior during template construction.

------------------------------------------------------------------------

# Overview

The Template Editor will provide **macro‑goal guidance** while users
construct meal templates.

This feature is **advisory only** and must **never block saving** of a
template.

The purpose is to help users build meals that align with their
nutritional goals such as:

-   minimum protein targets
-   maximum fat limits
-   calorie ranges
-   carbohydrate ranges

The system should display **real‑time macro totals** and indicate
whether the template is inside or outside the user's configured targets.

------------------------------------------------------------------------

# Design Philosophy

Macro guidance exists to **assist the user**, not constrain them.

Rules:

1.  The user can always save a template even if it violates macro
    targets.
2.  Macro guidance must be **visually helpful but not intrusive**.
3.  Macro guidance should update **in real time** as foods are added or
    modified.
4.  The system should use **existing nutrition calculation logic** used
    by planner totals.

------------------------------------------------------------------------

# Data Sources

Macro targets come from the **user macro goal configuration**.

Typical fields:

-   daily calorie target
-   daily protein target
-   daily fat target
-   daily carbohydrate target

Optional fields:

-   minimum protein
-   maximum fat
-   calorie range
-   carbohydrate range

If per‑meal goals exist later, the template editor should prefer those.

------------------------------------------------------------------------

# Macro Model

The template editor must compute totals for:

-   Calories
-   Protein
-   Fat
-   Carbohydrates

These values are derived from **template items**.

Each template item already resolves to:

-   Food
-   Quantity
-   Serving unit

The nutrition calculation pipeline should reuse the same logic used in:

-   PlannedMeal totals
-   Log entry totals

No duplicate macro computation logic should be introduced.

------------------------------------------------------------------------

# Macro Aggregation Algorithm

For each template item:

1.  Resolve the food nutrient profile.
2.  Convert the serving amount to grams.
3.  Scale the nutrient profile to the consumed quantity.
4.  Accumulate totals across all template items.

Example pseudocode:

    totalProtein += itemProtein
    totalFat += itemFat
    totalCarbs += itemCarbs
    totalCalories += itemCalories

The totals must recompute whenever:

-   an item is added
-   an item is removed
-   quantity changes
-   unit changes

------------------------------------------------------------------------

# Target Comparison

Each macro total should be compared to the configured targets.

Example rule set:

## Protein

Condition:

    totalProtein >= minProteinTarget

Status:

-   OK if above minimum
-   Warning if below

## Fat

Condition:

    totalFat <= maxFatTarget

Status:

-   OK if below maximum
-   Warning if above

## Calories

Condition:

    minCalories <= totalCalories <= maxCalories

Status:

-   OK if inside range
-   Warning if outside

## Carbohydrates

Optional guidance similar to calories.

------------------------------------------------------------------------

# UI Presentation

Macro guidance should appear **inside the Template Editor header area**.

Suggested layout:

Template Editor

Template Name\
Meal Slot

Macro Summary Card

Protein: 42g ✔\
Fat: 18g ✔\
Carbs: 55g ⚠\
Calories: 520 kcal ✔

------------------------------------------------------------------------

# Visual Indicators

Use clear but minimal visual indicators.

Recommended states:

  State              Meaning                  Indicator
  ------------------ ------------------------ ----------------
  Within goal        Macro target satisfied   Green check
  Slightly outside   Near target              Yellow warning
  Far outside        Significant deviation    Red warning

Indicators must remain subtle and not dominate the UI.

------------------------------------------------------------------------

# Update Behavior

Macro guidance must update whenever:

-   template items change
-   quantities change
-   serving units change

Compose recomposition should trigger recalculation through ViewModel
state updates.

The calculation should **not run on the UI thread if heavy**.

However, macro calculations are expected to be lightweight.

------------------------------------------------------------------------

# Performance Considerations

Macro calculation should remain fast even with many items.

Typical template size:

3‑10 foods

Worst case scenario:

20 items

Optimization guidelines:

-   reuse existing nutrient scaling functions
-   avoid repeated database queries
-   keep calculations in memory

------------------------------------------------------------------------

# Edge Cases

## Empty Template

If no items exist:

Display:

"Add foods to see macro totals."

## Missing Nutrient Data

If a food lacks macro values:

Treat missing values as zero but show a subtle indicator.

## Extreme Quantities

Large quantities should still compute correctly using existing scaling
logic.

------------------------------------------------------------------------

# Future Extensions

This macro guidance system should support future features such as:

-   per‑meal macro goals
-   macro target sliders during template creation
-   macro balancing suggestions
-   recommended food swaps
-   automated template scoring

The current design must not block these additions.

------------------------------------------------------------------------

# Non‑Goals

The macro guidance system should NOT:

-   block template saving
-   automatically modify template items
-   enforce strict macro compliance

It is purely an **informational tool**.

------------------------------------------------------------------------

# Example Scenario

User goal:

Protein ≥ 35g\
Fat ≤ 20g\
Calories 450‑600

Template items:

Chicken breast\
Rice\
Broccoli

Computed totals:

Protein: 41g ✔\
Fat: 12g ✔\
Calories: 510 ✔

The template editor displays the macro card showing that the meal meets
all goals.

------------------------------------------------------------------------

# Final Notes

Macro guidance improves:

-   meal quality
-   user adherence to nutrition goals
-   template usefulness

Because templates represent **reusable meals**, helping users align them
with macro targets significantly increases the long‑term value of the
planner system.

------------------------------------------------------------------------

**End of Specification**
