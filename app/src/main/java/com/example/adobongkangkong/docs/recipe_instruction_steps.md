# Recipe Instruction Steps (Design Reference)

## Purpose

Introduce structured, ordered instruction steps for recipes with optional step images.

This is a **data-layer-first feature** intended for future:
- recipe editor UX
- home planning / cooking flows
- instruction-driven experiences (step-by-step cooking, checklists, etc.)

---

## Core Design

### Table: `recipe_instruction_steps`

Each row represents a single instruction step.

Fields:

- `id` (PK, auto)
- `stableId` (UUID string, unique)
- `recipeId` (FK → recipes.id, CASCADE delete)
- `position` (Int, ordered index within recipe)
- `text` (String)
- `imagePath` (nullable String, app-owned relative path)

### Key Decisions

- Child table (not blob on recipe)
- Explicit ordering via `position`
- Stable ID for future import/export reconciliation
- File-based images (not Room blobs)
- One image per step (for now)

---

## Ordering Model

- Steps are ordered by `position ASC`
- Unique constraint: `(recipeId, position)`
- Repository is responsible for maintaining valid ordering

---

## Image Storage

filesDir/recipe_instruction_images/{recipeId}/{stepStableId}/step.jpg

- DB stores relative path only
- Images compressed JPEG (~85 quality, max ~1600px)

---

## Storage Helper

RecipeInstructionImageStorage

Responsibilities:
- deterministic file paths
- import/compress image
- delete images

---

## Repository API (Current)

- getInstructionSteps(recipeId)
- insertInstructionStep(recipeId, position, text)
- updateInstructionStepText(stepId, text)
- updateInstructionStepPosition(stepId, position)
- setInstructionStepImage(stepId, imagePath)
- deleteInstructionStep(stepId)
- deleteInstructionStepsForRecipe(recipeId)

---

## Backup / Restore

Includes:
- filesDir/recipe_instruction_images

---

## Migration

v21:
- adds recipe_instruction_steps table
- adds indices

---

## Deferred

- UI / ViewModel
- reorder UX
- multi-image support
- validation layer

---

## Summary

- normalized schema
- file-based images
- future-ready design
