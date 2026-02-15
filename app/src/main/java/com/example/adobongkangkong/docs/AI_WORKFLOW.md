# AI_WORKFLOW.md

### Stable Workflow Protocol for AdobongKangkong

------------------------------------------------------------------------

## 🎯 Purpose

This document defines how ChatGPT is used as a structured engineering
assistant for the AdobongKangkong project.

Goals:

-   Prevent architectural drift
-   Avoid accidental refactors
-   Protect database integrity
-   Minimize context-loss regressions
-   Enforce minimal-patch discipline

ChatGPT must be treated as stateless between threads.

------------------------------------------------------------------------

# 🧱 Core Principles

## 1. One Thread = One Milestone

Each conversation focuses on ONE clearly defined goal.

Examples:

-   ✅ Add usdaModifiedDateIso to FoodBarcodeEntity with migration
-   ✅ Fix planner bottomsheet not opening
-   ❌ Fix planner + sorting + USDA + banner in one thread

------------------------------------------------------------------------

## 2. Required Thread Header

Every new thread must begin with:

PROJECT: AdobongKangkong\
GOAL: `<single clear task>`{=html}

CURRENT DB VERSION:\
PATCH MODE:\
A) Minimal patch only (default)\
B) Clean refactor allowed\
C) Redesign allowed

FILES PROVIDED:\
- `<file list>`{=html}

If PATCH MODE is not specified → Default = A.

------------------------------------------------------------------------

# 🏗 Architecture Guardrails

-   Domain models ≠ Entity models\
-   No silent constructor parameter removal\
-   No field renaming unless explicitly approved\
-   No architecture layering changes unless redesign mode

------------------------------------------------------------------------

# 🗄 Database Protocol

When adding a field, explicitly specify:

-   Domain only
-   Entity only
-   Both
-   Migration required? (Yes/No)
-   DB version bump allowed? (Yes/No)

Never bump DB version without approval.

Never assume destructive migration.

------------------------------------------------------------------------

# 🧮 Nutrition Modeling Rules

Before changing math:

1.  Lock canonical basis (per100g / per100ml / serving)
2.  Define USDA_SERVING behavior (if applicable)
3.  Define import scaling vs runtime scaling
4.  Confirm migration impact

No math refactor without model lock-in.

------------------------------------------------------------------------

# 📦 File Handling Rules

ChatGPT may:

-   Modify only files explicitly provided
-   Return full file only if requested

ChatGPT may NOT:

-   Recreate entire files without request
-   Remove parameters silently
-   Invent new composables unless redesign mode

------------------------------------------------------------------------

# 🧩 Large Feature Phasing

For Planner, USDA modeling, macro targets, etc:

Phase 1 -- Requirements (no code)\
Phase 2 -- Data model\
Phase 3 -- Migration impact\
Phase 4 -- UseCases + Repo\
Phase 5 -- UI wiring\
Phase 6 -- Testing

Do not skip phases.

------------------------------------------------------------------------

# 🛑 Reset Protocol

If instability occurs:

User says: RESET.

Then provides:

-   Current DB version
-   Current entity definitions
-   Current domain definitions
-   Exact error

No assumptions carried forward.

------------------------------------------------------------------------

# 🔐 Protected Zones

Changes require explicit approval:

-   FoodEntity constructor
-   FoodBarcodeEntity constructor
-   PlannedMeal schema
-   Nutrition math basis
-   Migration versioning
-   NavRoutes parameters

------------------------------------------------------------------------

# 📌 Operational Reminder

ChatGPT does not have:

-   Persistent file access
-   Direct repo access
-   Guaranteed long context retention

Always paste relevant files and restate invariants.

------------------------------------------------------------------------

End of document.
