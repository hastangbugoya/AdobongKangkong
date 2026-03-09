# Planned Meal Recurrence Flexibility Plan
Developer / AI Reference Document

Timestamp: 2026-03-09

Purpose:
This document formalizes a plan for making recurring planned meals more flexible, with a focus on allowing the **same recurring meal concept** to appear in **different meal slots on different days of the week**.

This is a planning/specification document. It does **not** imply that all behavior is already implemented.

---

# Problem Statement

The current recurring meal flow is too rigid if a user wants the same planned meal pattern to occur:

- on multiple days
- in different meal slots
- with recurrence details visible and editable from the recurring UI

Real-world examples:

- Monday → Lunch
- Tuesday → Dinner
- Thursday → Lunch
- Saturday → Snack

This should be representable as **one recurring meal series** rather than forcing the user to create multiple unrelated recurring meals.

---

# High-Level Goal

Support a recurrence model where a single planned meal series can define:

1. **Frequency**
   - Daily
   - Weekly
   - Monthly later

2. **Details**
   - Which days are active
   - Which slot applies on each active day
   - Start date
   - End condition

3. **Future extensibility**
   - Monthly recurrence later
   - Exceptions / detached occurrences later
   - "Apply to this / all / future" later

---

# Product Direction

When the user taps **Make recurring**, the UI should evolve toward:

## Section 1 — Frequency
- Daily
- Weekly
- Monthly (future)

## Section 2 — Details
For weekly recurrence, the user should be able to define rules like:

- Mon → Lunch
- Tue → Dinner
- Wed → Lunch
- Fri → Breakfast

This must be visible and editable, not hidden in implicit assumptions.

---

# Recommended Semantic Model

A recurring planned meal should be treated as:

- **one series**
- containing **one or more slot rules**

## Series-level data
A series should own data such as:

- effective start date
- effective end date
- end condition
- recurrence type / frequency
- source meal linkage / seed meal linkage
- optional naming info

## Rule-level data
Each rule should define:

- recurrence selector
- slot
- optional custom label

For weekly recurrence, a rule is naturally:

- weekday
- slot
- custom label (if applicable)

This is the right model for supporting different slots on different weekdays.

---

# Recommended V1 Scope

## V1 recurring flexibility
Implement:

- Daily
- Weekly
- Weekly rule details with **per-day slot assignment**

### Daily meaning
Daily should probably behave as a **convenience preset**, not a separate long-term engine.

Recommended interpretation:

- Daily = all days active by default
- user may still override details if the UI later allows it

This avoids boxing the system into a special-case "daily but single slot only" model.

### Weekly meaning
Weekly is the core flexible rule system.

Users select weekdays, and each selected weekday can have its own slot.

Example:

- Mon → Lunch
- Tue → Dinner
- Thu → Lunch

---

# Future Monthly Support

Monthly should be deferred for now.

Recommended future V1 monthly scope:

- recurrence type = MONTHLY
- rule value = dayOfMonth

Examples:

- every 1st
- every 15th
- every 28th

Defer more advanced monthly patterns such as:

- first Monday
- second Tuesday
- last Friday

until the weekly rule system is stable.

---

# UI Plan

## Make Recurring entry point
When user taps **Make recurring** from a planned meal:

1. Open recurring configuration UI
2. Let user choose frequency
3. Show recurrence details
4. Save recurring configuration
5. Generate series + future occurrences

## Weekly details UI
Recommended layout:

- Frequency: Weekly
- Start date
- End condition

Then a weekday detail section such as:

- Mon [slot dropdown]
- Tue [slot dropdown]
- Wed [slot dropdown]
- Thu [slot dropdown]
- Fri [slot dropdown]
- Sat [slot dropdown]
- Sun [slot dropdown]

Days can be toggled on/off, and enabled days must allow slot selection.

This mirrors the domain model clearly.

---

# Data Model Assessment (Current DB)

## Short answer
**Yes, the current database is already partially aligned with this direction for weekly recurrence.**
It does **not** fully support the proposed Daily/Weekly/Monthly frequency model yet, but it already has the most important foundation for **different slots on different weekdays**.

## What the current DB already supports
The existing recurrence/storage model already includes a slot-rule table concept.

From the current schema/design, the recurring planner system includes entities like:

- `PlannedSeriesEntity`
- `PlannedSeriesSlotRuleEntity`

The important part is that rule rows already store a **weekday** and a **slot**.

That means the database already supports the concept of:

- Monday → Lunch
- Tuesday → Dinner
- Friday → Breakfast

for the same series.

### Practical implication
The current DB should already be able to represent **weekly recurring meals with different slots on different weekdays**.

That is a major advantage and means this feature is more of a **UI / flow / semantics** improvement than a full schema redesign.

---

# What the current DB does NOT fully support yet

## 1. Explicit recurrence type / frequency
The current recurrence model appears weekly-rule driven.

It does not yet clearly model:

- DAILY
- WEEKLY
- MONTHLY

as an explicit frequency enum/type in the stored series/rule structure.

## 2. Monthly rule fields
The current rule shape is weekday-based, not monthly-based.

So it does not yet natively represent:

- dayOfMonth
- ordinal weekday of month

## 3. Rich recurring UI semantics
Even if the DB can already store weekday-slot rules, the current UI may not expose that flexibility clearly.

---

# Engineering Assessment

## What should be possible now without major DB changes
You should be able to support:

- one series
- multiple weekly slot rules
- different slots for different weekdays

without major schema redesign, because the current rule-table design already points in that direction.

## What likely still needs work
To make this user-facing and understandable, you will still need:

- recurring UI redesign
- event/state updates
- validation rules
- save/update flow changes
- rule editing semantics

So the DB foundation is there, but the full product experience is not yet formalized.

---

# Recommended Implementation Strategy

## Phase 1 — Expose existing weekly flexibility
Goal:
Allow the same recurring meal to have different slots on different weekdays.

Tasks:
- update recurring UI
- surface weekday → slot mapping clearly
- reuse existing weekly slot-rule storage
- keep monthly out of scope

## Phase 2 — Formalize frequency model
Goal:
Introduce explicit recurrence frequency semantics.

Possible additions:
- DAILY
- WEEKLY
- MONTHLY

This may require extending series metadata and rule interpretation logic.

## Phase 3 — Monthly recurrence
Goal:
Support simple monthly day-of-month rules.

Only do this after weekly flexibility is stable.

---

# Validation Rules

Recommended rules for weekly recurring meals:

1. At least one day must be selected
2. Each selected day must have a valid slot
3. Start date is required
4. End condition must be valid
5. Series creation should fail gracefully with user-readable errors

---

# Invariants to Preserve

- A recurring series must remain understandable from stored rules
- UI must reflect the real rule model
- Different weekdays may have different slots
- The same recurring concept should not require multiple separate series unless intentionally created that way
- Monthly support must remain additive later

---

# Risks / Pitfalls

Avoid these mistakes:

- hardcoding recurrence around one slot only
- treating Daily as a completely separate incompatible model
- hiding weekday-slot rules behind vague UI
- introducing monthly fields prematurely into weekly-only flows
- creating separate series automatically when one series with multiple rules would suffice

---

# Recommendation Summary

## Recommended near-term direction
Implement a recurring meal flow where:

- the user taps **Make recurring**
- chooses **Daily** or **Weekly**
- sees recurrence **details**
- weekly details allow **different slots on different weekdays**

## Current DB support
**The current DB already appears capable of storing weekly weekday-slot rules for one series.**
So the foundation for:
- same series
- different weekdays
- different slots

is already there.

What is still needed is mostly:
- better recurring configuration UI
- clearer semantics
- save/edit behavior

---

End of document.
