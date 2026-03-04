# AI_IDENTIFIER_AND_ASSUMPTION_RULES.md
Project: AdobongKangkong

Purpose  
Prevent AI from inventing, renaming, or assuming identifiers that do not exist in the real codebase.

This file exists because identifier invention is one of the most common causes of regressions in large projects.

These rules apply to:

- class names
- function names
- event names
- repository names
- use case names
- DAO methods
- navigation routes
- parameters
- property names

If a name is wrong, the system may still compile but behavior will break.

----------------------------------------------------------------

CORE RULE

Never invent identifiers.

Never rename identifiers.

Never assume identifiers.

Only use identifiers that **exist in the project source code**.

----------------------------------------------------------------

NO INVENTED CLASSES

Do not create classes that do not exist.

Incorrect examples

Inventing:

CreateMealFromTemplateUseCase

when the real class is:

CreatePlannedMealFromTemplateUseCase

Another incorrect example

Assuming a repository exists:

TemplateRepository

when the actual repository is:

MealTemplateRepository

Rule

If a class name is not visible in the provided code,
do not guess it.

Ask for the file that defines it.

----------------------------------------------------------------

NO RENAMING OF IDENTIFIERS

Identifiers must never be renamed unless explicitly requested.

Forbidden changes

Example

Rename:

openAddSheet

to:

openMealPlanner

Rename:

RemoveItem

to:

DeleteItem

Rename:

PlannerDayEvent.AddMeal

to:

PlannerDayEvent.CreateMeal

These changes break event contracts and UI wiring.

Rule

Existing names must remain exactly as defined in the codebase.

----------------------------------------------------------------

NO PARAMETER GUESSING

Do not guess function parameters.

Incorrect

Calling:

CreatePlannedMealUseCase(
date,
slot
)

When the actual signature may be:

CreatePlannedMealUseCase(
dateIso,
mealSlot,
customLabel
)

Rule

Always match the exact function signature defined in source.

----------------------------------------------------------------

NO PROPERTY ASSUMPTIONS

Do not assume fields exist in models.

Example incorrect assumption

Assuming a field exists:

subtitle

When the real model only has:

title

Another example

Using:

mealId

when the real property is:

plannedMealId

Rule

Only use properties confirmed in the model definitions.

----------------------------------------------------------------

NO EVENT INVENTION

Events must not be invented.

Incorrect

PlannerDayEvent.OpenTemplateSelector

when the real event is:

PlannerDayEvent.OpenTemplatePicker

Incorrect

PlannerDayEvent.DeleteItem

when the real event is:

PlannerDayEvent.RemovePlannedItem

Rule

Events must exactly match the sealed class definitions.

----------------------------------------------------------------

NO ROUTE INVENTION

Navigation routes must never be guessed.

Incorrect

planner/templatePicker

planner/templates

Correct

Use the routes defined in AppNavHost.

If the route definition is not provided, request the navigation file.

----------------------------------------------------------------

NO USE CASE GUESSING

Use cases must not be guessed or renamed.

Incorrect examples

PromoteMealSeriesUseCase

CreateRecurringMealUseCase

If the real use case is:

PromoteMealToSeriesAndEnsureHorizonUseCase

Rule

Always use the exact use case names defined in domain.

----------------------------------------------------------------

WHEN INFORMATION IS MISSING

If the AI does not have enough information to identify:

- a class
- a function
- an event
- a model
- a parameter
- a route

The AI must stop and ask for the file.

Never guess.

Example response

"I need the file that defines PlannerDayEvent to continue safely."

----------------------------------------------------------------

IDENTIFIER SAFETY CHECKLIST

Before returning code verify:

- every identifier exists in the provided source
- no identifiers were renamed
- no new identifiers were invented
- parameters match exact signatures
- model fields match entity definitions
- navigation routes match AppNavHost

If any check fails the solution must be revised.

----------------------------------------------------------------

SUMMARY

The AI must behave as if it has **zero permission to invent names**.

Allowed actions:

- reuse existing identifiers
- reference identifiers defined in provided files

Forbidden actions:

- inventing identifiers
- renaming identifiers
- guessing identifiers
- assuming identifiers

When in doubt:

Stop and request the correct file.