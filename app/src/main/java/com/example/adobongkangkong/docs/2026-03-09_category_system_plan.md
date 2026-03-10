# Food and Recipe Category System Plan
Developer / AI Reference Document

Timestamp: 2026-03-09

Purpose:
This document captures the planned design for a **category system** that can organize both
foods and recipes in AdobongKangkong.

This may be used either:
- as an implementation guide now
- or as a reminder/specification for future implementation

The key design decision is that categories are **not stored directly on FoodEntity or RecipeEntity**.
Instead, foods and recipes may each belong to **multiple categories**.

---

# Core Goal

Allow users to organize foods and recipes according to the groupings that matter to them.

Examples of user-defined groupings:

- Meal Prep
- High Protein
- Budget
- Quick Breakfast
- Pantry
- Freezer
- Kid Friendly
- Blood Pressure Focus
- Travel Food
- Favorite Staples

These are organizational groupings, not core nutrition data.

---

# Key Design Decisions

## 1. Categories are separate from foods and recipes
Do **not** add a category field directly to:

- `FoodEntity`
- `RecipeEntity`

Reason:
A food or recipe may belong to **multiple categories**, so a single field is the wrong model.

---

## 2. Use a shared category system
Use one shared category table for both foods and recipes.

Recommended entities:

- `CategoryEntity`
- `FoodCategoryCrossRefEntity`
- `RecipeCategoryCrossRefEntity`

This allows the same category to be used for both foods and recipes.

Example:

- `Meal Prep` can apply to both foods and recipes
- `High Protein` can apply to both foods and recipes

This is more flexible than separate food-category and recipe-category systems.

---

## 3. Categories should be user-defined
The system should support **user-created categories** first.

The category system exists so the user can define what matters to them.

Examples:

- Cheap Staples
- Gym Foods
- Easy Dinner
- Blood Pressure
- Family Favorites

The system should not assume the app knows the correct organizational buckets in advance.

---

# Recommended Data Model

## Category entity
Recommended fields:

- `id`
- `name`
- `sortOrder`
- `createdAtEpochMillis`
- `isSystem` (optional now, useful later)

Possible future fields:

- `color`
- `icon`
- `archived`
- `notes`

---

## Food category cross-reference
Recommended fields:

- `foodId`
- `categoryId`

This should use a composite primary key.

---

## Recipe category cross-reference
Recommended fields:

- `recipeId`
- `categoryId`

This should also use a composite primary key.

---

# Why this model is correct

This model supports:

- one food → many categories
- one recipe → many categories
- one category → many foods
- one category → many recipes

It also keeps category concerns separate from:

- nutrient storage
- recipe composition
- barcode mapping
- planner logic

This separation is important for long-term maintainability.

---

# Shared vs Separate Category Systems

## Recommended
Use a **shared category table**.

Reason:
Many user groupings naturally apply to both foods and recipes.

Examples:

- Meal Prep
- High Protein
- Budget
- Quick Breakfast

A shared category system is simpler and more natural for the user.

## Not recommended initially
Separate:

- Food categories
- Recipe categories

This would duplicate concepts and create unnecessary complexity.

---

# User-Defined Category Behavior

The system should support:

- create category
- rename category
- delete category
- assign category to food
- unassign category from food
- assign category to recipe
- unassign category from recipe

Deleting a category should:

- remove the category
- remove cross-reference rows
- **not** delete foods or recipes

This is a critical trust rule.

---

# Category Validation Rules

Recommended validation:

- name must not be blank
- trim whitespace
- prevent duplicates if possible
- ideally treat names case-insensitively for uniqueness

Examples of duplicates to avoid:

- `Meal Prep`
- `meal prep`
- ` meal prep `

---

# Suggested Initial Scope

## Phase 1 — Storage + assignment
Implement:

- category entity
- food category cross-ref
- recipe category cross-ref
- create/edit/delete category
- assign/unassign categories in food and recipe editors

This is the most important first step.

## Phase 2 — Browsing and filtering
Implement:

- filter foods by category
- filter recipes by category
- browse items by category

## Phase 3 — Quality-of-life improvements
Possible additions:

- reorder categories
- archive categories
- color or icon support
- system categories
- category chips in lists

---

# UI Guidance

Categories should likely be shown as chips or tags.

Examples:

Food editor:
- categories section
- add/remove category chips
- create new category inline or via dialog

Recipe editor:
- same category assignment pattern

Later, list screens may show:

- category filter chips
- grouped results
- category badges

This UI should remain separate from nutrient and meal-slot concepts.

---

# Architectural Boundaries

Categories are for **organization**, not for:

- nutrient classification
- USDA mapping
- meal scheduling
- recurrence rules
- health status modeling

Do not overload categories to solve unrelated domain problems.

---

# Future-Proofing Notes

The model should support future additions such as:

- system-defined categories
- user-defined categories
- synced categories if cloud sync is added later
- category colors/icons
- category sort ordering

Adding these later should not require redesigning the basic cross-reference structure.

---

# Why categories should not be embedded in FoodEntity

Avoid models like:

- `category: String`
- `categoriesCsv: String`
- `categoryId: Long?`

on `FoodEntity` or `RecipeEntity`

Reasons:

- wrong for many-to-many
- awkward to query
- hard to rename safely
- poor normalization
- hard to reuse across foods and recipes

---

# Recommendation Summary

Recommended implementation model:

- `CategoryEntity`
- `FoodCategoryCrossRefEntity`
- `RecipeCategoryCrossRefEntity`

Key behavior:

- user-defined categories
- foods and recipes can each belong to multiple categories
- categories remain separate from core nutrition entities
- shared category system across foods and recipes

This is the correct scalable design for category-based organization.

---

# Suggested Repo Placement

Recommended location:

```text
docs/dev/2026-03-09_category_system_plan.md
```

---

End of document.
