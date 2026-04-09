# Implementation Plan (Phased)

## Phase 1 – Domain Modeling
- Define input models:
  - RecipeDemandEntry
  - RecipeDefinition
- Define output models:
  - RecipeTotalRequirement
  - RecipeOccurrenceRequirement
  - IngredientRequirement

## Phase 2 – Core Computation Use Case
- Implement:
  - Horizon aggregation
  - Yield normalization
  - Batch computation
  - Ingredient scaling

## Phase 3 – Duplicate Detection
- Build cross-recipe ingredient index
- Flag duplicates without merging

## Phase 4 – Internal Ingredient Merge
- Merge same-ingredient rows within same recipe

## Phase 5 – Output Structuring
- Produce:
  - Totalled (per recipe)
  - Not totalled (per occurrence)

## Phase 6 – UI Mapping (Separate Layer)
- Map domain → UI models
- Add layered icon flags
- Add formatting

## Phase 7 – Regression Test Coverage
- Lock invariants before UI integration

## Phase 8 – Integration
- Plug into existing shopping pipeline
- Keep fallback for legacy logic if needed
