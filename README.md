# AdobongKangkong

A modern Android nutrition, meal planning, recipe, and food tracking app built with a strong focus on architecture, nutrition correctness, and long-term extensibility.

AdobongKangkong is designed for real-life food logging, where users do not always eat perfectly standardized foods. It supports foods, recipes, recipe variants, planned meals, reusable meal templates, immutable nutrition snapshots, USDA import, barcode mapping, and deterministic nutrition scaling.

This project is intentionally designed as a technical showcase of real-world mobile engineering practices:

- Clean architecture
- Complex domain modeling
- Deterministic nutrition math
- Local-first persistence
- Offline-friendly data ownership
- Testable business logic
- Practical handling of messy real-world food data

---

## Tech Stack

### Core

- Kotlin
- Jetpack Compose / Material3
- Room / SQLite with migrations
- Hilt dependency injection
- Kotlin Coroutines / Flow
- WorkManager
- kotlinx.serialization

### Data and APIs

- USDA FoodData Central API
- Barcode ingestion and mapping
- Local-first persistence with enrichment flows
- User-edited nutrition and serving metadata

### Testing

- JUnit for domain and pure logic
- Instrumented tests for database, I/O, and Android components

---

## Architecture

AdobongKangkong follows a layered architecture with strict separation between UI, state, domain logic, repositories, and persistence.

```text
UI (Compose)
    ↓
ViewModel
    ↓
Domain / Use Cases
    ↓
Repository Interfaces
    ↓
Data Layer (Room / API / Mappers)
```

Core principles:

- Single source of truth for persisted data
- Explicit nutrition basis rules
- No hidden density guessing
- Immutable logged nutrition snapshots
- Testable domain transformations
- Reversible and deterministic nutrient scaling

---

## Nutrition Correctness Model

Nutrition values are stored and scaled using explicit basis types.

| Basis Type | Meaning |
|---|---|
| `PER_100G` | Canonical mass-based nutrition |
| `PER_100ML` | Canonical volume-based nutrition |
| `USDA_REPORTED_SERVING` | Raw imported serving-based nutrition |

The app avoids silent conversions. Mass and volume conversions require explicit bridge data:

- `gramsPerServingUnit`
- `mlPerServingUnit`

If a bridge is missing, the app does not guess density. It surfaces warnings or limits the available conversion paths.

---

## Food Model

Foods and recipes share a unified food-facing model.

```kotlin
Food(
    servingSize,
    servingUnit,
    gramsPerServingUnit?,
    mlPerServingUnit?,
    nutrients
)
```

A food can represent:

- A normal food item
- A packaged item
- A USDA-imported item
- A user-created item
- A recipe-backed food

This keeps the logging pipeline consistent while still allowing recipes to expand into ingredients when needed.

---

## Recipe System

Recipes are represented as foods with ingredient expansion behind them.

Supported behavior:

- Per-serving nutrition
- Ingredient expansion
- Recipe editing
- Recipe-backed food logging
- Historical log preservation through snapshots

### Recipe Variants

Recipe variants support real-life one-off recipe changes without mutating the base recipe.

Examples:

- Less oil
- More protein
- No pork
- Different rice amount
- Ingredient substitution

Planned meals can select a recipe variant. The selected variant is preserved on the planned meal item and used for planner display and nutrition preview.

Important distinction:

- Base recipe = reusable canonical recipe
- Recipe variant = planned or logged adjustment
- Logged nutrition = frozen snapshot so history does not change when recipes are edited later

---

## Planner

The planner supports date-specific planned meals and recurring meal generation.

Planner capabilities include:

- Daily planned meals
- Meal slots such as breakfast, lunch, dinner, snack, and custom
- Planned item quantities by servings, grams, or milliliters
- Recipe and food items
- Recipe variant selection for planned meals
- Recurrence-aware planned series
- Override handling for generated occurrences
- Planner nutrition cautions for nutrients such as sodium and sugar

Planned meals are specific enough to support recipe variants because the user is planning an actual meal for a specific day.

---

## Meal Templates

Meal templates are reusable meal patterns.

Examples:

- Pork tenderloin meal
- Standard breakfast
- High-protein lunch
- Post-workout meal

Templates are intentionally simpler than planned meals. They can contain foods and recipe-backed items, but recipe variants are not saved in templates yet.

Current rule:

```text
Meal templates keep recipes as the base recipe.
Choose a recipe variant later when editing the planned meal created from the template.
```

This keeps templates reusable. A variant such as “less oil” or “extra rice” may be appropriate on one future day but not every time the template is used.

---

## Shopping Engine

The planner and recipe systems support shopping-style aggregation.

The shopping engine can:

- Expand recipes into ingredients
- Aggregate food amounts across planned meals
- Keep grams, milliliters, and servings separate
- Avoid unsafe conversions when bridge data is missing
- Preserve deterministic behavior with no density guessing

---

## USDA Import

AdobongKangkong integrates with USDA FoodData Central.

Supported flows include:

- Food search
- Barcode-based lookup
- Importing branded and generic foods
- Mapping USDA nutrients into local canonical nutrition
- Preserving raw serving basis when needed
- Warning when imported data is incomplete

Some USDA Foundation Foods and search results may contain partial nutrition data. The app is designed to warn the user without blocking useful imports.

---

## Barcode System

The barcode system uses a dedicated `FoodBarcodeEntity`.

It supports:

- Multiple barcodes per food
- Package-specific overrides
- USDA mapping
- User overrides
- Collision-safe remapping
- Adopt and merge flows

---

## Merge System

The app includes a non-trivial food deduplication system.

Merge behavior includes:

- Soft-delete overrides
- Barcode reassignment
- Canonical nutrient preservation
- Filling missing values where safe
- Merge lineage tracking

Example lineage fields:

```kotlin
mergedIntoFoodId
mergeChildCount
```

---

## CSV Import System

The CSV importer is designed for repeatable, idempotent imports.

Features:

- Stable hash-based IDs
- Automatic nutrient detection
- Basis normalization
- Duplicate handling
- Warning system instead of hard failure

Design rule:

```text
1 nutrient → 1 basis only
```

The app avoids storing the same nutrient in multiple competing bases.

---

## UI / UX Engineering

The app uses Jetpack Compose with state hoisting and unidirectional data flow.

Notable UI systems:

- Dynamic food editor
- Basis-aware nutrition editing
- Quick Add with synchronized grams, mL, and servings
- Planner day screen
- Shared meal editor UI for planned meals and meal templates
- Recipe variant selector for planned meal recipe rows
- Meal template info dialogs for base-recipe-only behavior
- Nutrition caution cards
- Alphabet index scrolling

The meal editor uses a shared UI contract so different ViewModels can reuse the same screen while preserving feature-specific behavior.

---

## Logging and Snapshots

Logged foods and recipes use immutable nutrition snapshots.

This means:

- Editing a food later does not rewrite old logs
- Editing a recipe later does not rewrite old logs
- Recipe variants can produce the correct nutrition for that logged event
- Historical data remains stable and auditable

This is one of the app’s core correctness rules.

---

## Current Feature Status

Working or active areas:

- Food database
- Food editor
- USDA import
- Barcode mapping
- Quick Add
- Recipe builder
- Recipe variants
- Planned meals
- Recurring planned meals
- Meal templates
- Planner nutrition cautions
- Shopping aggregation
- Merge system
- Backup / restore related infrastructure

Paused / shelved areas:

- Cooked or prepared batch workflow

The cooked batch concept may return later, but recipe variants currently cover the more common one-off adjustment use case.

---

## Engineering Decisions

### Intentionally avoided

- Density guessing
- Silent g ↔ mL conversion
- Hidden normalization
- Multi-basis nutrient storage
- Rewriting historical log nutrition

### Enforced

- Explicit user-provided bridges
- Deterministic nutrition math
- Reversible transformations
- Snapshot-based logging
- Testable domain logic
- Clear distinction between planned meals, templates, recipes, and variants

---

## Why This Project Exists

AdobongKangkong is not just a calorie tracker.

It is a systems-heavy Android app designed to demonstrate:

- Handling messy real-world food data
- Designing for correctness over convenience
- Building scalable domain models
- Supporting realistic food substitutions and recipe changes
- Maintaining historical nutrition integrity
- Applying production-grade Android architecture

---

## Future Work

Potential future directions:

- More advanced nutrient analytics
- Smarter user-controlled bridge estimation
- Cloud sync layer
- Kotlin Multiplatform exploration
- Performance tuning for larger datasets
- More complete recipe variant workflows
- Possible revival of cooked/prepared batch support

---

## Author

Built by an Android developer focused on correctness-first systems, clean architecture, and long-term maintainability.
