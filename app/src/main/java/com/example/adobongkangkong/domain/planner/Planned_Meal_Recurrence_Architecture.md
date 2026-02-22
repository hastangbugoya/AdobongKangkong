# Planned Meal / Item Recurrence Refactor

**Architecture Design Document**\
Generated: 2026-02-22 21:40 UTC

------------------------------------------------------------------------

## 🎯 Objective

Redesign the Planned Meal / Planned Item system to support flexible,
high-performance recurring logic while preserving:

-   PlannerDay performance
-   Shopping list aggregation performance
-   Food totals computation
-   Log conversion flow
-   Clean Architecture boundaries

⚠️ This document covers architecture and planning only. No SQL or code
included.

------------------------------------------------------------------------

# 1️⃣ Selected Architecture Strategy

## Option 1: Rule-Based Recurrence + Cached Materialized Occurrences

### Core Concept

We separate recurrence into:

1.  **Series (Rule Layer)** → Defines recurrence logic
2.  **Occurrences (Materialized Layer)** → Fast, queryable instances
    used by UI
3.  **Exceptions (Override Layer)** → Handles skips and future editing
    cases

PlannerDay and Shopping List operate **only on occurrence rows**.

Recurrence rules are expanded only within a bounded horizon window.

------------------------------------------------------------------------

# 2️⃣ High-Level Model Overview

## A. PlannedSeries (Rule Table)

Represents the recurrence definition.

Fields (conceptual): - id - effectiveStartDate - effectiveEndDate
(nullable) - endConditionType (untilDate \| repeatCount \| indefinite) -
endConditionValue - createdAt - updatedAt

------------------------------------------------------------------------

## B. PlannedSeriesSlotRule (Weekday → Slot Mapping)

Represents non-uniform recurrence mapping.

Fields (conceptual): - seriesId - weekday (Mon--Sun) - slot (Breakfast /
Lunch / Dinner / Snack / CUSTOM)

Example mapping: - Monday → Lunch - Tuesday → Dinner - Friday →
Breakfast

------------------------------------------------------------------------

## C. Occurrence Layer (Materialized Cache)

These rows power all UI queries.

### PlannedMealOccurrence

-   id
-   seriesId (nullable for one-offs)
-   date
-   slot
-   status (active / cancelled / overridden)

### PlannedItemOccurrence

-   mealOccurrenceId
-   foodId / recipeId
-   quantity
-   basis

PlannerDay reads only these tables.

------------------------------------------------------------------------

## D. PlannedSeriesException (Phase 2+)

Handles: - Skipped dates - Slot overrides - Future override scenarios

Fields (conceptual): - seriesId - date - exceptionType (skip \|
overrideSlot \| overrideItems) - payload

------------------------------------------------------------------------

# 3️⃣ Recurrence Expansion Strategy

## Bounded Horizon Model

We generate occurrences only within a defined window:

Recommended horizon: **180 days (6 months)**

When: - User opens PlannerDay - User opens Shopping for next N days

We ensure occurrences exist up to: today + max(N, horizon)

No runtime rule evaluation during UI reads.

------------------------------------------------------------------------

# 4️⃣ Editing Semantics

## Edit This Occurrence Only

-   Mark occurrence overridden or cancelled
-   Create exception row if needed
-   Do NOT modify series

## Edit Entire Series

-   End current series at pivotDate - 1
-   Create new series version starting pivotDate
-   Regenerate future occurrences

## Edit Future Occurrences

-   Split series at pivot date
-   Old series ends
-   New series begins

No separate history table required --- versioning is achieved via series
rows.

------------------------------------------------------------------------

# 5️⃣ Phase Rollout Plan

## Phase 0 --- Foundations

-   Introduce series concept
-   Add seriesId to occurrence rows
-   No UI change

## Phase 1 --- Recurring Creation

-   Create recurrence builder UI
-   Generate bounded occurrences
-   PlannerDay uses occurrence layer only

## Phase 2 --- Exceptions + Horizon Maintenance

-   Add exception handling
-   Add background ensure-horizon mechanism

## Phase 3 --- Advanced Editing

-   Edit single occurrence
-   Edit future occurrences
-   Series splitting logic

------------------------------------------------------------------------

# 6️⃣ Performance Guardrails

-   PlannerDay queries must be date-based direct lookups
-   Shopping list must operate on occurrence rows only
-   No rule evaluation during navigation
-   Horizon expansion must be bounded
-   Avoid recomposition storms in ViewModel

------------------------------------------------------------------------

# 7️⃣ Why This Architecture

  Criterion        Result
  ---------------- -------------------------
  Simplicity       Moderate
  Performance      High
  Flexibility      High
  Future Editing   Fully supported
  Migration Risk   Moderate but controlled

This model preserves current architecture while enabling long-term
recurrence capabilities.

------------------------------------------------------------------------

# 8️⃣ Key Architectural Principles

-   Series defines intent
-   Occurrence defines reality
-   Exceptions define deviation
-   UI reads only occurrences
-   Rules never evaluated during PlannerDay rendering

------------------------------------------------------------------------

**End of Document**
