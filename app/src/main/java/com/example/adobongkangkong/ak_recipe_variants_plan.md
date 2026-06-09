# AdobongKangkong Recipe Variants Plan

## Decision

For now, pause active work on **cooked/prepared batches** and lean toward a **recipe variant** model for one-off recipe changes.

The existing cooked batch code should stay in place for now. We are not deleting the feature, removing methods, or forcing a large cleanup yet. The immediate goal is to stop expanding cooked batches from Quick Add and avoid turning cooked batches into a confusing catch-all concept.

## Current direction

Use **Recipe Variant** as the main concept for one-off changes to a base recipe.

A recipe variant represents:

```text
I made this recipe differently this time, so the nutrition may be different.
```

Examples:

```text
Base recipe: Chicken adobo

Today’s variant:
- removed low-sodium soy sauce
+ added regular soy sauce
+ added potato
- adjusted oil from 2 tbsp to 1 tbsp
```

A cooked/prepared batch represents something slightly different:

```text
I made this recipe and the actual cooked yield or servings were different.
```

Because these are related but not identical, we should avoid overloading cooked batches too early.

## Rationale

### 1. Variants match the real problem better

The user’s main day-to-day issue is substitution and one-off changes:

```text
The stored recipe calls for low-fat ingredient, but only regular is available.
The recipe calls for one ingredient, but the user substitutes another.
The user adds or removes an ingredient for this one meal.
```

That is better modeled as a **variant** than as a cooked batch.

### 2. Cooked batches imply yield or inventory

“Cooked batch” sounds like:

```text
I cooked 1,500 g.
I logged 300 g.
The app should know 1,200 g remains.
```

That can accidentally imply inventory tracking, depletion, leftovers, negative balances, and precision requirements.

AK does not need strict inventory tracking for this MVP.

### 3. Yield differences and ingredient differences are separate concerns

A batch can answer:

```text
How much did this recipe yield after cooking?
```

A variant can answer:

```text
What changed in the ingredients this time?
```

Those can eventually work together, but they should not be forced into one concept too soon.

### 4. Historical logs must stay truthful

AK already follows the principle that logs should use immutable snapshots.

If a user logs a variant today, then edits the base recipe next week, today’s logged nutrition must not silently change.

So ingredient deltas alone are not enough. A variant should eventually save a frozen nutrient snapshot.

## Agreed modeling idea

A recipe variant should be calculated as:

```text
final ingredients =
(original recipe ingredients - removed original lines)
+ adjusted original lines
+ added new lines
```

Implementation detail:

```text
final = originalByLineId
final.removeAll(removed)
final.putAll(adjustedByOriginalLineId)
final.putAll(addedByDeltaId)
```

Important rule:

```text
Do not key by foodId.
```

A recipe can validly use the same food more than once:

```text
1 tbsp oil for sauté
1 tsp oil for finishing
```

So original recipe ingredients should be keyed by **recipe ingredient line ID**, not food ID.

## Initial change types

For the MVP, use three change types:

```kotlin
enum class RecipeVariantIngredientChangeType {
    ADD,
    REMOVE,
    ADJUST
}
```

Meaning:

```text
ADD    = add a new ingredient line for this variant only
REMOVE = remove an original recipe ingredient line
ADJUST = keep the original line, but change grams or servings
```

Substitutions can be represented as:

```text
REMOVE old ingredient
ADD replacement ingredient
```

A separate `REPLACE` type can be added later as a UI convenience, but it is not required for the first implementation.

## Conflict rule

A removed original ingredient should not also be adjusted.

Clean rule:

```text
A removed original ingredient cannot also be adjusted.
```

If a conflict somehow happens, either block it in the UI or make remove win.

## Snapshot rule

Ingredient deltas explain the variant.

The frozen nutrient snapshot preserves historical truth.

Recommended principle:

```text
Deltas are for explanation and editing.
The final nutrient snapshot is for logging and history.
```

At variant creation/log time:

```text
1. Load base recipe ingredients.
2. Apply REMOVE changes.
3. Apply ADJUST changes.
4. Apply ADD changes.
5. Compute final nutrition.
6. Save final nutrient snapshot.
7. Log from the snapshot.
```

## MVP goals

### Goal 1: Stop expanding cooked batches in Quick Add

Quick Add should not create new cooked batches for now.

Keep existing code in place, but remove or hide the user-facing path that opens batch creation from Quick Add.

This avoids pushing users toward a concept we may rename or replace.

### Goal 2: Keep existing cooked batch code

Do not rip out cooked batch methods, dialogs, entities, or repository functions yet.

Reason:

```text
The code may still be useful later for actual yield tracking.
Removing it now creates churn with little benefit.
```

### Goal 3: Introduce recipe variants as the clearer concept

Create a future path where the user can say:

```text
I am logging this recipe, but with changes.
```

The app can then show:

```text
Base recipe
Changes from recipe
Final computed nutrition
```

### Goal 4: Preserve AK snapshot behavior

Any logged variant must produce a stable nutrition snapshot.

Old logs should remain accurate even if:

```text
the base recipe changes
the food data changes
the variant is later edited
```

### Goal 5: Keep the first UI simple

The first UI should avoid heavy terminology.

Possible user-facing labels:

```text
Variant
Recipe variation
Made with changes
Log with changes
```

Possible early flow:

```text
Recipe selected
↓
Log normally
or
Log with changes
↓
Add/remove/adjust ingredients
↓
Review nutrition
↓
Log
```

## Deferred ideas

These are intentionally not required for the MVP:

```text
Strict inventory tracking
Remaining grams/servings
Mark batch depleted
Recipe branch management
Named reusable variants
Replace change type
Compare base recipe vs variant charts
Batch yield + variant combined flow
```

## Relationship to cooked/prepared batches later

Cooked/prepared batches may still become useful later for:

```text
Actual cooked yield
Servings made
Meal prep notes
Actual weight after cooking
Optional remaining amount
```

A future combined model could be:

```text
Base recipe
+ variant ingredient changes
+ actual cooked yield
+ frozen nutrient snapshot
```

But for now, the cleaner path is:

```text
Pause batch expansion.
Keep code.
Build toward recipe variants.
Use snapshots for logs.
```

## Working summary

AK should treat **recipe variants** as the primary solution for one-off ingredient changes.

Cooked batches should remain parked as existing code and possibly return later as an actual-yield or meal-prep feature.

The MVP should focus on:

```text
Base recipe
Ingredient deltas
Frozen nutrition snapshot
Simple logging flow
No inventory burden
```
