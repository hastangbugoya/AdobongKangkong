
# AI_WORKFLOW_RULES.md
Project: AdobongKangkong

Purpose:
Define strict rules for AI-assisted modifications to prevent regressions,
architectural violations, or unintended behavior changes.

These rules apply to all AI-generated code, refactors, and fixes in this repository.

----------------------------------------------------------------

CORE REFACTOR RULE

Refactoring must NEVER change application behavior.

Allowed refactor changes:
- formatting
- comments or KDoc
- code organization
- dead code removal
- variable renaming (only if explicitly requested)

NOT allowed during refactors:
- changing UI flows
- changing navigation behavior
- altering ViewModel event handling
- introducing new UX
- introducing new screens
- altering domain logic

If behavior changes, it is NOT a refactor.

----------------------------------------------------------------

COMPILE FIX RULE

When fixing compile errors:

Only modify the lines necessary to restore compilation.

Do NOT:
- rewrite entire files
- rename events
- rename functions
- change use case signatures
- change navigation logic
- change UI state structures

Preferred process:

1 Identify the failing line
2 Apply minimal patch
3 Leave surrounding logic untouched

----------------------------------------------------------------

NO FUNCTIONAL REGRESSION RULE

Existing working functionality must never be removed or altered unintentionally.

Examples of forbidden regressions:
- removing navigation to PlannedMealEditor
- changing how +Add behaves
- disabling template creation
- altering planner recurrence behavior
- modifying logging semantics

If intent is unclear:
ASK BEFORE MODIFYING.

----------------------------------------------------------------

FILE MODIFICATION RULE

Full file replacements are NOT default.

Prefer targeted patches.

Example correct approach:

Replace:

    openAddSheet(slot)

With:

    openMealPlanner(slot)

Incorrect approach:

Rewrite entire PlannerDayViewModel.kt

Full replacements allowed only when:
- explicitly requested
- file structure is broken
- patching is impossible

----------------------------------------------------------------

CLEAN ARCHITECTURE RULE

Layer order:

UI
↓
ViewModel
↓
UseCase (Domain)
↓
Repository
↓
DAO / Database

Rules:

UI must not call repositories directly.
ViewModels must not access DAOs.
UseCases contain business logic.
Repositories coordinate persistence.

Do not bypass layers.

----------------------------------------------------------------

MINIMAL CHANGE PRINCIPLE

Every change must satisfy:

Smallest change that fixes the problem without altering behavior.

----------------------------------------------------------------

AI SAFETY CHECKLIST

Before returning code verify:

- navigation behavior unchanged
- UI behavior unchanged
- no removed functionality
- no renamed events
- architecture layers preserved
- minimal modifications applied

If any fails → revise the solution.
