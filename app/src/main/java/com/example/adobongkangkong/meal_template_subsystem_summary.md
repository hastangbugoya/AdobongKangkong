# Meal Template Subsystem — Architectural Summary

_Last reviewed against uploaded `src` snapshot on 2026-03-07._

## Purpose

The Meal Template subsystem is the reusable-meal layer between the planner/editor UI and persisted template storage. It allows the app to:

- save a reusable meal definition as a named template
- store an optional default `MealSlot`
- store an ordered set of template items
- reopen/edit/duplicate/delete templates
- compute macro summaries for browsing/picking
- apply a template onto an existing planned meal

This document is for future AI assistants and developers who need a fast mental model of how the template system fits together.

---

## 1. High-level architecture

At a high level, the subsystem has four layers:

1. **Persistence layer (Room entities + DAOs)**
   - Stores template headers and ordered template items.

2. **Repository layer**
   - Exposes CRUD access to template headers/items.
   - Provides a transactional writer abstraction for full-template saves/deletes.

3. **Domain/use-case layer**
   - Reconstructs templates for editing.
   - Saves complete template drafts transactionally.
   - Duplicates/deletes templates.
   - Computes macro totals for template cards.
   - Builds lightweight preview/details for list/picker cards.
   - Applies template items to an existing planned meal.

4. **UI layer**
   - **Template Editor**: create/edit/save/delete/duplicate templates.
   - **Template List**: browse/manage template library.
   - **Template Picker**: choose a template in planner context.

The important separation is:
- **Editor** owns mutable draft state.
- **List/Picker** are read-oriented consumers of summary/detail use cases.
- **Writer repository** is the safest path for “replace whole template” operations.

---

## 2. Persisted data model

### `MealTemplateEntity`
Room header record for a template.

Core fields:
- `id: Long`
- `name: String`
- `defaultSlot: MealSlot?`

Meaning:
- one row per template header
- `defaultSlot` is advisory metadata for quick planning, not a forced rule

### `MealTemplateItemEntity`
Room child rows for ordered template contents.

Core fields:
- `id: Long`
- `templateId: Long`
- `type: PlannedItemSource`
- `refId: Long`
- `grams: Double?`
- `servings: Double?`
- `sortOrder: Int`

Meaning:
- each template item points to a food / recipe / recipe batch reference
- quantity is represented as `grams` and/or `servings`
- `sortOrder` preserves user-visible item order

### Relationship
- `MealTemplateEntity` 1 → N `MealTemplateItemEntity`
- delete cascade is enforced from template header to template items

### DAOs
`MealTemplateDao`
- insert / update / delete
- `getById(id)`
- `observeAll()` ordered by name

`MealTemplateItemDao`
- insert / update / delete
- `getById(id)`
- `observeItemsForTemplate(templateId)`
- `getItemsForTemplate(templateId)`
- `getItemsForTemplates(templateIds)` for bulk lookups
- `deleteItemsForTemplate(templateId)`

**Future-AI note:** `getItemsForTemplates()` is important because both list/picker-style screens need bulk card data without N+1 fetch patterns.

---

## 3. Repository layer

### `MealTemplateRepository`
Header-level repository abstraction.

Responsibilities:
- observe all template headers
- CRUD a single `MealTemplateEntity`

This repository is intentionally simple and header-oriented.

### `MealTemplateItemRepository`
Child-item repository abstraction.

Responsibilities:
- observe/load items for one template
- bulk-load items for many templates
- CRUD item rows
- delete all items for one template

### `MealTemplateWriterRepository`
Transactional write abstraction for full-template operations.

Responsibilities:
- `save(template: MealTemplate): Long`
- `delete(templateId: Long)`

This exists because a full save is not just a header update; it is a **header + complete ordered item-set replacement** that must stay consistent.

### `MealTemplateWriterRepositoryImpl`
Current implementation uses `NutriDatabase.withTransaction` and performs:

For save:
- trim/validate name
- update existing header or insert new header
- delete all existing template items
- reinsert current ordered item set with normalized sort order

For delete:
- validate template id
- load existing header
- delete header
- child item deletion follows Room cascade

**Design implication:** this repository is the cleanest persistence boundary for whole-template create/update/delete behavior.

---

## 4. Domain models

The editable/template-building flows use domain models instead of operating directly on Room records.

### `MealTemplate`
Primary domain model for full template editing.

Fields:
- `id`
- `name`
- `defaultSlot`
- `items: List<MealTemplateItem>`

Conventions:
- `id == 0L` means “new / not yet persisted”
- item ordering is meaningful and must be preserved

### `MealTemplateItem`
Sealed interface with quantity-bearing subtypes:
- `FoodMealTemplateItem`
- `RecipeMealTemplateItem`
- `RecipeBatchMealTemplateItem`

Each item carries `quantity: PlannedQuantity`.

### `MealTemplateSummary`
Header-only summary model:
- `id`
- `name`
- `defaultSlot`

Useful for light list/summary flows.

---

## 5. Core use cases

### `GetMealTemplateUseCase`
Reconstructs a full editable `MealTemplate` from persisted data.

Flow:
- validate `templateId`
- load template header
- load ordered template items
- map `PlannedItemSource` + stored quantity into domain `MealTemplateItem` variants
- return a complete `MealTemplate`

Use this when an editing flow needs the full domain representation.

### `SaveMealTemplateUseCase`
Validates high-level invariants and delegates to `MealTemplateWriterRepository.save()`.

Key invariant:
- template name must not be blank

### `DuplicateMealTemplateUseCase`
Copies an existing template into a new one.

Behavior:
- loads source template via `GetMealTemplateUseCase`
- creates a new domain template with `id = 0L`
- preserves `defaultSlot` and ordered items
- appends a predictable `(copy)` suffix to the name
- saves via `SaveMealTemplateUseCase`

Important constraint:
- domain data is duplicated, but banner/media files are intentionally **not** copied here

### `DeleteMealTemplateUseCase`
Deletes a template via `MealTemplateWriterRepository.delete()`.

### `ComputeMealTemplateMacroTotalsUseCase`
Computes compact per-template macro totals for card/list browsing.

Inputs:
- template ids

Resolution strategy:
- bulk-load template items
- resolve direct foods, recipe-backed foods, and recipe-batch-backed foods
- fetch food snapshots
- scale nutrients using grams/servings and per-gram / per-mL bases
- return `Map<Long, MacroTotals>`

Why this matters:
- template cards should show macro summaries without teaching Compose to do repository/nutrition work
- list/picker screens depend on this being a bulk, pre-render pipeline

### `BuildMealTemplatePickerDetailsUseCase`
Builds lightweight template-card details for preview text.

Current output model:
- `itemCount`
- `previewLine`

Behavior:
- bulk-load template items
- resolve food display names across FOOD / RECIPE / RECIPE_BATCH references
- preserve stable item order
- skip missing references instead of failing the screen
- compress names into a short preview line like “A • B • C +N more”

**Important architectural note:** despite the name mentioning “Picker,” this use case is effectively the best shared detail-builder for both picker and list card previews.

### `ApplyMealTemplateToPlannedMealUseCase`
Applies a template onto an existing planned meal container.

Behavior:
- validate planned meal exists
- validate template exists
- load ordered template items
- delete current planned items for the meal
- insert new planned items copied from template rows
- preserve item order

Critical non-behavior:
- this does **not** mutate the planned meal header itself (`slot`, `date`, `name`, etc.)

---

## 6. UI architecture

### A. Template Editor
Primary class:
- `MealTemplateEditorViewModel`

Shared editor contract pieces:
- `MealEditorContract`
- `MealEditorUiState`
- `MealEditorMode.TEMPLATE`
- related editor composables such as `MealEditorHeader` / `MealEditorScreen`

Responsibilities of `MealTemplateEditorViewModel`:
- load template into editor state
- edit name
- edit optional default slot
- add/remove/reorder items
- compute advisory live macro summary from draft state
- save changes
- duplicate current template
- delete current template
- emit editor effects such as open duplicated template / notify deletion

Notable implementation detail in this snapshot:
- the editor currently persists by directly using `MealTemplateRepository` + `MealTemplateItemRepository`, not the transactional writer use case stack
- this works, but means editor save/duplicate paths are a separate write path from `SaveMealTemplateUseCase`

**Future-AI note:** if write behavior ever drifts, compare editor save/duplicate logic against `MealTemplateWriterRepositoryImpl` and domain save/duplicate use cases.

### B. Template List
Primary classes:
- `MealTemplateListViewModel`
- `MealTemplateListScreen`
- `MealTemplateListRoute`
- `MealTemplateMacroSummaryFormatter`
- `MealTemplateBannerCardBackground`

Responsibilities:
- browse template library
- search/filter
- sort rows
- show banner, name, item count, default slot, macro summary, preview text
- open editor / create flow

`MealTemplateListViewModel` currently:
- observes all templates
- fetches template items in bulk
- computes item counts
- computes macro totals
- formats macro summary text for rows

### C. Template Picker
Primary classes:
- `MealTemplatePickerViewModel`
- `MealTemplatePickerScreen`
- `MealTemplatePickerRoute`
- `MealTemplateBannerCardBackground`

Responsibilities:
- browse/select templates in planner context
- show planner-context title/date/slot cue
- show banner, macro summary, default slot, preview text
- emit selection back into planner flow

### Shared visual/card concepts
Template List and Template Picker are different flows, but they intentionally share near-identical card content:
- template name
- banner if available
- item count / preview line
- macro summary
- default meal slot

They differ mainly in **screen intent**:
- **List** = library management / open editor
- **Picker** = contextual selection for planner flow

**Maintenance rule:** when changing template-card content, inspect both list and picker.

---

## 7. End-to-end flow map

### Create or edit a template
1. User opens template editor.
2. `MealTemplateEditorViewModel` owns in-memory draft state.
3. Editor updates name, default slot, item order, quantities.
4. Draft macro summary is recomputed live.
5. Save persists header + ordered items.

### Browse templates
1. `MealTemplateListViewModel` observes all template headers.
2. It bulk-loads items and macro totals.
3. It creates render-ready row models.
4. `MealTemplateListScreen` renders flat, lightweight rows/cards.

### Pick a template for planner use
1. `MealTemplatePickerViewModel` loads templates plus card details/macros.
2. `MealTemplatePickerScreen` renders template cards in picker context.
3. User taps a template.
4. Planner flow receives selected template id.
5. Template can then be applied to a planned meal.

### Apply template to a planned meal
1. Caller invokes `ApplyMealTemplateToPlannedMealUseCase(plannedMealId, templateId)`.
2. Existing planned items for the meal are deleted.
3. Template items are copied into planned items.
4. Planned meal header remains unchanged.

---

## 8. Coupling / drift risks

The main drift risk in this subsystem is **parallel card pipelines**.

Even though the template list and picker look functionally similar, they may still have separate:
- viewmodels
- row models
- screen composables
- event contracts

This means a feature such as:
- default slot display
- macro summary formatting
- food preview line
- banner behavior
can accidentally be updated in one screen and not the other.

### Safe mental model
Treat these as:
- **separate screen shells**
- but a **shared card-content contract**

When changing shared card behavior, review together:
- `MealTemplateListViewModel`
- `MealTemplateListScreen`
- `MealTemplatePickerViewModel`
- `MealTemplatePickerScreen`
- `BuildMealTemplatePickerDetailsUseCase`
- `ComputeMealTemplateMacroTotalsUseCase`
- `MealTemplateBannerCardBackground`
- macro summary formatter/helper files

---

## 9. Practical guidance for future AI assistant

When asked to modify the Meal Template subsystem:

### If the request is about editing behavior
Inspect first:
- `MealTemplateEditorViewModel`
- shared `MealEditor*` contract/state/composables
- `MealTemplateEntity`
- `MealTemplateItemEntity`
- write paths used by editor save/duplicate/delete

### If the request is about template cards or browsing
Inspect first:
- `MealTemplateListViewModel`
- `MealTemplateListScreen`
- `MealTemplatePickerViewModel`
- `MealTemplatePickerScreen`
- `BuildMealTemplatePickerDetailsUseCase`
- `ComputeMealTemplateMacroTotalsUseCase`
- `MealTemplateBannerCardBackground`

### If the request is about persistence correctness
Inspect first:
- `MealTemplateWriterRepository`
- `MealTemplateWriterRepositoryImpl`
- `SaveMealTemplateUseCase`
- `GetMealTemplateUseCase`
- `DuplicateMealTemplateUseCase`
- `DeleteMealTemplateUseCase`
- DAOs/entities

### If the request is about applying templates to planner meals
Inspect first:
- `ApplyMealTemplateToPlannedMealUseCase`
- `PlannedMealRepository`
- `PlannedItemRepository`
- `MealTemplateItemRepository`

### Invariants to preserve
- template item ordering is meaningful
- blank template names should not persist
- default slot is optional metadata, not a forced rule
- picker/list card content should remain aligned unless divergence is intentional
- bulk item lookup should be preferred over N+1 repository calls for list/picker views

---

## 10. Short subsystem takeaway

The Meal Template subsystem is a reusable meal-definition system with:
- **header + ordered item persistence** in Room
- **transactional full-template writes** via a writer repository
- **domain reconstruction and copy/delete/apply use cases**
- **editor-owned mutable draft state**
- **list/picker read pipelines** for banner + macro + preview-style cards

If you remember only one thing, remember this:

> The editor is the mutable authoring surface; the list and picker are read-only presentation surfaces built from shared template data and bulk summary/detail use cases.

