# Meal Prep Planner UI Plan (Google Calendar–Inspired)
Date: 2026-02-07  
Project: AdobongKangkong  
Milestone: Planner MVP UI planning (no UI code yet)

## TL;DR

- Planner stores **concrete meal instances** (date + slot), not recurring rules.
- Repeating meals works by **copying instances** (Calendar-style “Repeat…”).
- Templates are **blueprints only**; applying one materializes independent meals.
- Meals auto-create on first item add; users never manage containers directly.
- Planning ≠ logging; actual intake is logged separately and may diverge.
- Meal slots are semantic (Breakfast/Lunch/etc.), not time-based.
- Design intentionally favors clarity over automation.

## Goals
- Familiar feel for Android users (borrow Google Calendar patterns).
- Planning is **concrete instances** (like single calendar events).
- Repetition is a **shortcut** (like “Repeat…”), not a hidden recurrence system.
- Planner items always belong to a meal container, but UI should **not feel** like extra steps.
- Logging remains separate: planned vs actual comparison is optional and best-effort.

## Core UX Principles (Calendar Mental Model)
1. **Start with a single instance**
    - User plans one meal on one date/slot.
2. **“Repeat…” is an action**
    - After an instance exists, user can “Repeat…” it across dates.
3. **Instances are editable**
    - Repeated meals are independent (no hard linkage). Editing one doesn’t force edits to all.
4. **“This meal” vs “All copies”**
    - Since we materialize independent copies, the safest default is “edit this meal”.
    - Later we can add “apply changes to other planned meals” as a batch action (optional, not MVP).

## Entities/Use Cases Already Supporting This
- Planner roots: `PlannedMealEntity`, `PlannedItemEntity` (PlannedDay derived)
- Templates: `MealTemplateEntity`, `MealTemplateItemEntity`
- Template prefs (optional already wired): favorite + bias (NEUTRAL/EAT_MORE/LIMIT)
- Observers: day / range
- Writes: create meal, add item, update qty, remove item
- Template ops:
    - Save planned meal → template
    - Apply template → multiple days/slots

## MVP Screens
### 1) Planned Day Screen (Primary)
**Header**
- Date navigation: back/forward arrows
- Tap date to open date picker (optional for MVP)
- Primary action: “Add” (global)

**Body: sections**
- Breakfast, Lunch, Dinner, Snack
- Custom meals (only if they exist)

Each section shows either:
- Existing planned meals for that slot (can be multiple), OR
- Empty state with quick actions

**Meal section header actions**
- Add item
- Add from template
- More (⋯)

**Meal section more menu**
- Repeat…
- Save as template
- Duplicate to…
- Rename meal (nameOverride) (optional MVP)
- Delete meal

**Item row**
- Title: food name or recipe batch name
- Subtitle: quantity summary (grams/servings)
- Actions: edit quantity, remove item

**Key UX decision**
- User is never forced to “create a meal container” explicitly.
- If a meal for (date, slot) doesn’t exist, we auto-create it on first add.

### 2) Add to Plan Flow (Logging-Like)
**Intent**
- Same flow users already understand from logging:
    - Search → pick → grams/servings → confirm
- Only difference: destination is planner instead of log.

**Step A: Target**
- Date (defaults to the day screen date)
- Slot (defaults to the section user tapped; otherwise last-used or Snack)

**Step B: Pick consumable**
- Tabs: Food | Recipe Batch
- Search + list

**Step C: Quantity**
- Dual input semantics: grams and/or servings (same as logging)
- Confirm: “Add to plan”

**Confirm behavior**
1. Ensure meal exists (date + slot/customLabel)
    - If missing: create `PlannedMealEntity` (append sortOrder)
2. Add planned item (append sortOrder)
3. Return to day screen (or keep sheet open for “Add another”)

### 3) Templates Screen (Library)
Access points:
- From Planned Day screen “Add from template”
- From global “Add” -> “From template”

Template list:
- Search
- Favorites filter
- Sort: favorites first, then name
- Optional: bias chips (Eat more / Limit) to prioritize or de-prioritize suggestions

Template actions:
- Apply…
- Edit template name (later)
- Delete template (later)
- Toggle favorite
- Set bias (Eat more / Limit / Neutral)

## Repeat / Duplicate Flows (Calendar-Inspired)
### A) Repeat… (meal instance)
Triggered from a planned meal menu.

**Repeat UI options**
- Does not repeat (default)
- Daily
- Weekly
- Weekly on: [Mon Tue Wed Thu Fri Sat Sun]
- Custom… (later)

**Date range**
- For next N weeks (simple MVP)
- Or until date (optional)

**Slot**
- Default: same slot as the source meal
- MVP: one slot for all targets
- Later: per-day slot mapping (“Mon/Wed/Fri Dinner; Tue/Thu Lunch”)

**What happens**
- Materialize independent copies of the meal (and items) for each target date/slot.
- No recurrence rule stored.

### B) Duplicate to… (multi-select dates)
A simplified version of Repeat… without recurrence rules.
- Pick dates (multi-select)
- Pick slot (single slot, MVP)
- Confirm → copies created

### C) Apply template → multiple days/slots
Triggered from template “Apply…”

MVP version:
- Select multiple dates
- Choose one slot for all
- Confirm → create meals + items

Enhanced version (meal prep power-user):
- Date list with per-date slot selector (Lunch/Dinner/etc.)
- Allow different slots across selected days (Mon/Wed/Fri Dinner, Tue/Thu Lunch)

## “Save as Template” Flow
Triggered from meal menu.

**Inputs**
- Template name (default to meal nameOverride; fallback slot label)
- Default slot (toggle: use meal slot as template defaultSlot)

**Behavior**
- Create template + template items from planned meal items
- Template independent after creation

## Default Behaviors (to reduce friction)
- Global add defaults:
    - Date: current day screen date
    - Slot: last-used slot, else Snack
- Add from a slot section:
    - Slot preselected, no prompt
- Custom slot usage:
    - Only when user explicitly creates a custom meal or labels it
    - Not required for MVP

## Notes for Future Enhancements (Pinned Ideas)
### Meal macro goals (per-slot targets)
- Do not implement now.
- Keep compatibility:
    - Slot remains first-class on PlannedMeal
    - Allow multiple meals per slot per day
    - Slot-level targets later compare totals of all meals in that slot vs goal

### Planned vs Actual comparison
- Logging remains independent.
- `LogEntry` may have nullable `mealSlot` later for best-effort comparisons.
- Default analytics should work at daily total even when mealSlot is null.

## MVP Acceptance Checklist
- View a day’s planned meals/items
- Add item (food/batch) into a slot with logging-like flow
- Auto-create meals as needed (no manual container step)
- Edit item quantity (grams/servings)
- Remove item
- Save meal as template
- Apply template to multiple dates (same slot MVP)
- Repeat/Duplicate a meal across multiple dates (same slot MVP)

## Implementation Sequence (when we start coding UI)
1. Day screen (observe single day)
2. Add-to-plan flow (food + batch search + qty)
3. Quantity edit + remove
4. Templates list + apply (single slot multi-date)
5. Save as template from meal
6. Repeat/Duplicate meal (reusing apply/copy logic)

---

## Design Guardrails & Non-Goals (Read Before Changing This Feature)

These constraints are **intentional**. Revisit carefully before altering.

### 1. Meals are instances, not rules
- Every planned meal is a **concrete instance** tied to a date + slot.
- Repetition is implemented by **copying instances**, not by storing recurrence rules.
- Editing a meal edits **that meal only** unless an explicit batch action is chosen.

### 2. Templates are blueprints only
- Templates are **never part of the planner timeline**.
- Applying a template **materializes new meals**; there is no live linkage.
- Template changes do **not** retroactively affect planned meals.

### 3. No explicit “create meal” step
- Meal containers are auto-created on first item add.
- Users interact with **slots and items**, not container mechanics.

### 4. Planner ≠ Logger
- Planning does **not** log consumption.
- Logging remains item-level and may diverge from plan.
- Planned vs actual comparison is best-effort and optional.

### 5. Slot is semantic, not time-based
- MealSlot (Breakfast/Lunch/etc.) conveys intent, not clock time.
- No meal time storage by design.
- Multiple meals per slot per day are allowed.

### 6. Ordering is simple and append-only (for now)
- Meals and items append using high sortOrder.
- Relative order is stabilized by (sortOrder, id).
- Reordering is additive and optional.

### 7. Meal macro goals are future-safe, not implemented
- Keep MealSlot first-class.
- Do not assume one meal per slot.
- Slot-level targets will aggregate multiple meals later.

### 8. Versioning discipline
- DB version bumps are **intentional checkpoints**.
- Minor design iterations may be documented in comments.
- Schema JSONs + companion object notes are the source of truth.

---
