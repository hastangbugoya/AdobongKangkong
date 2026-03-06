# AdobongKangkong --- Meal Template System Roadmap

**Project:** AdobongKangkong\
**Architecture:** Kotlin · Jetpack Compose · Room · Hilt · Clean
Architecture\
**Subsystem:** Planner / Meal Templates\
**Document Type:** Implementation Roadmap\
**Status:** Approved planning document (no code changes yet)

------------------------------------------------------------------------

# Overview

This document defines the roadmap for implementing the **Meal Template
System** in the AdobongKangkong Android application.

Meal templates allow users to create reusable meal blueprints that can
be applied to planner days. Templates are **not tied to specific dates**
and should remain independent of planned meal occurrences.

The template system must integrate with the existing planner
architecture while remaining flexible enough to support:

-   recurring meal series
-   planner horizon generation
-   template-based meal insertion
-   macro-aware meal building

The system will be implemented in incremental phases to minimize
architectural risk.

------------------------------------------------------------------------

# Core Concept

A **Meal Template** represents a reusable meal blueprint consisting of:

-   Template header (name, optional slot)
-   Template items (foods and quantities)
-   Optional metadata (future extensibility)

Templates are **source objects**, used to create PlannedMeal instances.

They must remain independent from planner occurrences.

------------------------------------------------------------------------

# Design Principles

## Separation of concerns

Templates must remain separate from planned meals.

Planned meals represent: - dated occurrences - planner horizon data -
recurring series members

Templates represent: - reusable meal blueprints - no date association -
reusable across planner days

## Reuse where appropriate

UI components and item-row editing patterns may be reused from the meal
editor where possible, but **ViewModels and domain logic should remain
template-specific**.

## Forward compatibility

The template system must not block future features such as:

-   applying templates to recurring series
-   macro‑guided template construction
-   template categories or tags
-   template duplication and sharing

------------------------------------------------------------------------

# Phase 0 --- Existing System Audit

Before implementing new UI, audit the current codebase to determine what
template infrastructure already exists.

Likely existing components:

-   MealTemplateEntity
-   MealTemplateItemEntity
-   MealTemplateRepository
-   MealTemplateItemRepository
-   SavePlannedMealAsTemplateUseCase
-   Template picker logic

Deliverable:

A short internal inventory describing:

-   existing entities
-   repository APIs
-   use cases
-   missing functionality

This prevents duplicating logic already present in the codebase.

------------------------------------------------------------------------

# Phase 1 --- Domain Contract Stabilization

Lock down the template data model before building UI.

## Template header fields

Recommended minimal fields:

-   id
-   name
-   defaultMealSlot (nullable)
-   createdTimestamp (optional)
-   updatedTimestamp (optional)

## Template item fields

Template items should closely mirror planned meal items:

-   templateId
-   foodId
-   quantity representation
-   unit representation
-   sortOrder

## Key design decisions

-   slot should be optional
-   template items should be structurally similar to planned items
-   templates must not include recurrence data

Deliverable:

Documented invariants for:

-   template headers
-   template items
-   template save behavior

------------------------------------------------------------------------

# Phase 2 --- Save Planned Meal As Template

This is the fastest user-visible feature.

## Flow

1.  User opens a planned meal
2.  User taps **Save as Template**
3.  Application copies header and items into template tables
4.  Template editor opens automatically

## Behavior

The template should be editable immediately after creation.

Default name generation may use:

-   planned meal name
-   slot name fallback
-   generic fallback if necessary

Users should be able to rename immediately.

Deliverable:

Working flow:

Planned Meal → Template → Template Editor

------------------------------------------------------------------------

# Phase 3 --- Template Editor

The Template Editor allows creation and editing of templates.

## Header section

Fields:

-   template name
-   default meal slot

Possible future additions:

-   template notes
-   tags

## Items section

Capabilities:

-   add food
-   edit quantity/unit
-   reorder items
-   remove items

## Editor actions

-   Save template
-   Cancel/back
-   Delete template
-   (Future) duplicate template

## Save behavior

Template save should:

-   replace item set atomically
-   preserve sort order
-   occur in a single database transaction

Deliverable:

Fully functional template editor.

------------------------------------------------------------------------

# Phase 4 --- Template Library Screen

Users need a place to manage templates.

## Templates Screen

Features:

-   searchable template list
-   optional slot grouping
-   template rows showing:
    -   name
    -   slot
    -   item count

Row tap → open Template Editor

Row menu actions:

-   rename
-   delete
-   duplicate (future)

Floating Action Button:

Create new template.

Deliverable:

A dedicated Template Library screen.

------------------------------------------------------------------------

# Phase 5 --- Create Template From Scratch

Users must be able to build templates independently of planned meals.

## Flow

Templates Screen → FAB → Template Editor

User defines:

-   template name
-   optional slot
-   template items

Save creates:

-   template header
-   template items

Deliverable:

Standalone template creation.

------------------------------------------------------------------------

# Phase 6 --- Apply Template To Planner

Templates must be usable inside the planner.

## Planner Flow

Planner Day Screen → Add Meal → Choose Template

Application performs:

Template → PlannedMeal conversion

Steps:

1.  create PlannedMeal
2.  copy TemplateItems → PlannedItems
3.  assign selected slot/date

Slot should be overridable when applying.

Deliverable:

Templates usable for planner meal insertion.

------------------------------------------------------------------------

# Phase 7 --- Macro‑Guided Template Building

Templates can optionally help users build meals that align with macro
goals.

This feature should integrate with the user's **macro goals system**.

## Concept

When building a template, the editor may display macro targets such as:

-   minimum protein
-   maximum fat
-   carbohydrate range
-   calorie targets

Example guidance:

Protein: ≥ 35g\
Fat: ≤ 20g\
Calories: 450--600

The template editor can display real‑time macro totals and show whether
the template meets the user's goals.

## Benefits

-   helps users design nutritionally balanced meals
-   speeds up meal planning
-   improves adherence to fitness goals

## Implementation considerations

The template editor can:

-   compute macro totals from template items
-   compare totals against user macro targets
-   display indicators such as:

✔ within goal\
⚠ outside goal

This feature should remain advisory and **must not block template
saving**.

Deliverable:

Macro guidance system integrated into the template editor.

------------------------------------------------------------------------

# Phase 8 --- Future Enhancements

These features are not required for the first release but should remain
possible within the architecture.

Possible additions:

-   template duplication
-   template favorites
-   recent templates
-   macro preview in template list
-   template categories or tags
-   apply template to multiple days
-   convert template into recurring series seed
-   template sharing/export

------------------------------------------------------------------------

# Recommended Implementation Order

1.  Audit existing template infrastructure
2.  Implement save planned meal as template
3.  Build template editor
4.  Implement template library screen
5.  Implement create template from scratch
6.  Implement apply template to planner
7.  Implement macro‑guided template building
8.  Add polish features

This order delivers working functionality quickly while minimizing
architectural risk.

------------------------------------------------------------------------

# Final Notes

The meal template system is a major usability improvement for the
planner.

It allows users to:

-   reuse common meals
-   speed up planner entry
-   design meals around nutrition goals
-   gradually build a personal meal library

Careful separation between **templates** and **planned meals** ensures
the system remains stable as recurrence features expand in the future.

------------------------------------------------------------------------

**End of Document**
